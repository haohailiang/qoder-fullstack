"""保险 PDF 知识库接口：上传、列表、重建、删除。"""

from __future__ import annotations

import logging
import uuid
from pathlib import Path

from fastapi import (
    APIRouter,
    BackgroundTasks,
    Depends,
    File,
    Form,
    HTTPException,
    Query,
    UploadFile,
    status,
)
from sqlalchemy import select
from sqlalchemy.orm import Session
from starlette.concurrency import run_in_threadpool

from ..config import get_settings
from ..db import get_db
from ..deps import CurrentUser, admin_only, require_user
from ..models import InsuranceDocument
from ..rag.ingest import run_ingest
from ..rag.vector_store import get_vector_store

log = logging.getLogger(__name__)

router = APIRouter(prefix="/documents", tags=["insurance-documents"])

PDF_MAGIC = b"%PDF-"


def _get_document(db: Session, doc_id: str) -> InsuranceDocument:
    doc = db.get(InsuranceDocument, doc_id)
    if doc is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="文档不存在")
    return doc


@router.post("/upload", summary="上传保险 PDF 并触发解析入库（仅 ADMIN）")
async def upload_document(
    background_tasks: BackgroundTasks,
    product_id: int = Form(..., alias="productId"),
    file: UploadFile = File(...),
    user: CurrentUser = Depends(admin_only),
    db: Session = Depends(get_db),
) -> dict:
    settings = get_settings()
    filename = Path(file.filename or "").name
    if not filename.lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="仅支持 PDF 文件")

    doc_id = uuid.uuid4().hex
    target_dir = settings.uploads_dir / str(product_id)
    target_dir.mkdir(parents=True, exist_ok=True)
    target_path = target_dir / f"{doc_id}.pdf"

    max_bytes = settings.max_pdf_mb * 1024 * 1024
    total = 0
    first_chunk = True
    try:
        with target_path.open("wb") as fh:
            while chunk := await file.read(64 * 1024):
                if first_chunk:
                    if not chunk.startswith(PDF_MAGIC):
                        raise HTTPException(status_code=400, detail="文件内容不是合法 PDF")
                    first_chunk = False
                total += len(chunk)
                if total > max_bytes:
                    raise HTTPException(
                        status_code=413, detail=f"PDF 大小超过 {settings.max_pdf_mb}MB 限制"
                    )
                fh.write(chunk)
    except HTTPException:
        target_path.unlink(missing_ok=True)
        raise
    except OSError as exc:
        target_path.unlink(missing_ok=True)
        raise HTTPException(status_code=500, detail=f"保存 PDF 失败: {exc}") from exc

    if total == 0:
        target_path.unlink(missing_ok=True)
        raise HTTPException(status_code=400, detail="上传文件为空")

    document = InsuranceDocument(
        id=doc_id,
        product_id=product_id,
        filename=filename,
        file_path=str(target_path),
        file_size=total,
        status="processing",
        chunk_count=0,
        created_by=user.username,
    )
    db.add(document)
    db.commit()
    db.refresh(document)

    background_tasks.add_task(run_ingest, doc_id)
    log.info("收到保险 PDF: product=%s, doc=%s, file=%s", product_id, doc_id, filename)
    return document.to_dict()


@router.get("", summary="查询某保险商品的知识库文档")
def list_documents(
    product_id: int = Query(alias="productId", description="保险商品 ID"),
    user: CurrentUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[dict]:
    rows = db.scalars(
        select(InsuranceDocument)
        .where(InsuranceDocument.product_id == product_id)
        .order_by(InsuranceDocument.created_at.desc())
    ).all()
    return [row.to_dict() for row in rows]


@router.post("/{doc_id}/reindex", summary="重新解析并重建向量（仅 ADMIN）")
def reindex_document(
    doc_id: str,
    background_tasks: BackgroundTasks,
    user: CurrentUser = Depends(admin_only),
    db: Session = Depends(get_db),
) -> dict:
    document = _get_document(db, doc_id)
    if not Path(document.file_path).exists():
        raise HTTPException(status_code=409, detail="原始 PDF 已丢失，请重新上传")

    document.status = "processing"
    document.error_message = None
    db.commit()
    db.refresh(document)

    background_tasks.add_task(run_ingest, doc_id)
    return document.to_dict()


@router.delete("/{doc_id}", summary="删除文档及其向量（仅 ADMIN）")
async def delete_document(
    doc_id: str,
    user: CurrentUser = Depends(admin_only),
    db: Session = Depends(get_db),
) -> dict:
    document = _get_document(db, doc_id)
    try:
        await run_in_threadpool(get_vector_store().delete_by_doc, doc_id)
    except Exception as exc:  # Milvus 不可用时仍允许清理元数据
        log.warning("删除 Milvus 向量失败，继续删除元数据: doc=%s, error=%s", doc_id, exc)

    file_path = Path(document.file_path)
    db.delete(document)
    db.commit()
    file_path.unlink(missing_ok=True)
    return {"id": doc_id, "deleted": True}