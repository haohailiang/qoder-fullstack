"""PDF 入库流水线：MinerU 解析 → 递归字符切分 → 百炼 Embedding → Milvus。"""

from __future__ import annotations

import asyncio
import logging
from pathlib import Path
from typing import Any

from langchain_text_splitters import RecursiveCharacterTextSplitter

from ..config import Settings, get_settings
from ..db import SessionLocal
from ..models import InsuranceDocument
from .embeddings import get_embeddings
from .mineru import MinerUClient, clean_markdown, parse_with_pypdf
from .vector_store import get_vector_store

log = logging.getLogger(__name__)

# 中文合同文本常见断句点，放在英文分隔符之后、空格之前
CHINESE_SEPARATORS = ["\n\n", "\n", "。", "！", "？", "；", "，", " ", ""]


def build_splitter(settings: Settings | None = None) -> RecursiveCharacterTextSplitter:
    settings = settings or get_settings()
    return RecursiveCharacterTextSplitter(
        chunk_size=settings.chunk_size,
        chunk_overlap=settings.chunk_overlap,
        separators=CHINESE_SEPARATORS,
        keep_separator=True,
        length_function=len,
    )


def split_markdown(markdown: str, settings: Settings | None = None) -> list[str]:
    """递归字符切分，过滤空白块。"""
    if not markdown.strip():
        return []
    chunks = build_splitter(settings).split_text(markdown)
    return [chunk.strip() for chunk in chunks if chunk.strip()]


def _load_document(doc_id: str) -> InsuranceDocument | None:
    with SessionLocal() as db:
        return db.get(InsuranceDocument, doc_id)


def _update_document(doc_id: str, **fields: Any) -> None:
    with SessionLocal() as db:
        doc = db.get(InsuranceDocument, doc_id)
        if doc is None:
            return
        for key, value in fields.items():
            setattr(doc, key, value)
        db.commit()


async def _parse_pdf(path: Path, filename: str, settings: Settings) -> tuple[str, str | None]:
    """优先 MinerU，失败且允许降级时使用 pypdf。"""
    if settings.mineru_api_token:
        try:
            result = await MinerUClient(settings).parse_pdf(path, filename)
            return result.markdown, result.batch_id
        except Exception as exc:
            if not settings.mineru_fallback_pypdf:
                raise
            log.warning("MinerU 解析失败，降级为 pypdf: file=%s, error=%s", filename, exc)

    text = await asyncio.to_thread(parse_with_pypdf, path)
    return text, None


async def run_ingest(doc_id: str) -> None:
    """后台入库任务：不向调用方抛出异常，失败状态写回文档记录。"""
    settings = get_settings()
    doc = _load_document(doc_id)
    if doc is None:
        log.error("入库任务找不到文档记录: %s", doc_id)
        return

    _update_document(doc_id, status="processing", error_message=None)
    try:
        path = Path(doc.file_path)
        if not path.exists():
            raise FileNotFoundError(f"PDF 文件不存在: {path}")

        markdown, batch_id = await _parse_pdf(path, doc.filename, settings)
        cleaned = clean_markdown(markdown)
        chunks = split_markdown(cleaned, settings)
        if not chunks:
            raise ValueError("PDF 未解析出可用文本，请确认文件为可复制文本的保险条款")

        embeddings = get_embeddings()
        vectors = await asyncio.to_thread(embeddings.embed_documents, chunks)
        if len(vectors) != len(chunks):
            raise RuntimeError("Embedding 返回数量与切分块数量不一致")

        records = [
            {
                "chunk_id": f"{doc_id}-{index:04d}",
                "product_id": int(doc.product_id),
                "doc_id": doc_id,
                "chunk_index": index,
                "source": doc.filename[:255],
                "text": chunk[:8000],
                "vector": vector,
            }
            for index, (chunk, vector) in enumerate(zip(chunks, vectors))
        ]

        store = get_vector_store()
        await asyncio.to_thread(store.ensure_collection)
        await asyncio.to_thread(store.replace_document_chunks, doc_id, records)

        _update_document(
            doc_id,
            status="ready",
            chunk_count=len(records),
            mineru_batch_id=batch_id,
        )
        log.info("保险 PDF 入库完成: doc=%s, chunks=%s", doc_id, len(records))
    except Exception as exc:
        log.exception("保险 PDF 入库失败: doc=%s", doc_id)
        _update_document(doc_id, status="failed", error_message=str(exc)[:990])