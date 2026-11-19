"""Milvus 向量库封装：按 product_id / doc_id 归档与检索保险 PDF 片段。

使用 pymilvus 的 MilvusClient（无 ORM 模式），集合结构与检索参数集中在此文件，
便于测试时用同接口的假实现替换。
"""

from __future__ import annotations

import logging
import threading
from dataclasses import dataclass
from functools import lru_cache
from typing import Any

from pymilvus import DataType, MilvusClient

from ..config import Settings, get_settings

log = logging.getLogger(__name__)


class VectorStoreError(RuntimeError):
    """Milvus 不可用或操作失败。"""


@dataclass
class ChunkHit:
    text: str
    source: str
    doc_id: str
    product_id: int
    chunk_index: int
    score: float

    def to_source(self) -> dict[str, Any]:
        """返回给前端的引用片段（不暴露向量）。"""
        return {
            "documentId": self.doc_id,
            "filename": self.source,
            "chunkIndex": self.chunk_index,
            "score": round(self.score, 4),
            "snippet": self.text[:280],
        }


class InsuranceVectorStore:
    def __init__(self, settings: Settings | None = None) -> None:
        self._settings = settings or get_settings()
        self._client: MilvusClient | None = None
        self._lock = threading.Lock()
        self.collection = self._settings.milvus_collection

    # ---------- 连接与建表 ----------
    def client(self) -> MilvusClient:
        with self._lock:
            if self._client is None:
                try:
                    self._client = MilvusClient(
                        uri=self._settings.milvus_uri,
                        token=self._settings.milvus_token or None,
                    )
                except Exception as exc:  # pragma: no cover - 依赖真实 Milvus
                    raise VectorStoreError(f"连接 Milvus 失败: {exc}") from exc
            return self._client

    def ensure_collection(self) -> None:
        try:
            client = self.client()
            if client.has_collection(self.collection):
                client.load_collection(self.collection)
                return

            schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=False)
            schema.add_field(
                field_name="chunk_id", datatype=DataType.VARCHAR, is_primary=True, max_length=96
            )
            schema.add_field(field_name="product_id", datatype=DataType.INT64)
            schema.add_field(field_name="doc_id", datatype=DataType.VARCHAR, max_length=32)
            schema.add_field(field_name="chunk_index", datatype=DataType.INT64)
            schema.add_field(field_name="source", datatype=DataType.VARCHAR, max_length=255)
            schema.add_field(field_name="text", datatype=DataType.VARCHAR, max_length=8192)
            schema.add_field(
                field_name="vector",
                datatype=DataType.FLOAT_VECTOR,
                dim=self._settings.embedding_dim,
            )

            index_params = client.prepare_index_params()
            index_params.add_index(
                field_name="vector", index_type="AUTOINDEX", metric_type="COSINE"
            )
            index_params.add_index(field_name="product_id", index_type="AUTOINDEX")

            client.create_collection(
                collection_name=self.collection, schema=schema, index_params=index_params
            )
            client.load_collection(self.collection)
            log.info("已创建 Milvus 集合 %s (dim=%s)", self.collection, self._settings.embedding_dim)
        except VectorStoreError:
            raise
        except Exception as exc:  # pragma: no cover - 依赖真实 Milvus
            raise VectorStoreError(f"初始化 Milvus 集合失败: {exc}") from exc

    # ---------- 写入 ----------
    def replace_document_chunks(self, doc_id: str, records: list[dict[str, Any]]) -> None:
        """先按 doc_id 清理旧向量，再批量写入（用于重建/覆盖上传）。"""
        self.delete_by_doc(doc_id)
        if not records:
            return
        try:
            self.client().upsert(collection_name=self.collection, data=records)
            self.client().flush(self.collection)
        except Exception as exc:  # pragma: no cover - 依赖真实 Milvus
            raise VectorStoreError(f"写入 Milvus 失败: {exc}") from exc

    def delete_by_doc(self, doc_id: str) -> None:
        try:
            self.client().delete(collection_name=self.collection, filter=f'doc_id == "{doc_id}"')
        except Exception as exc:  # pragma: no cover - 依赖真实 Milvus
            raise VectorStoreError(f"删除 Milvus 向量失败: {exc}") from exc

    # ---------- 检索 ----------
    def search(
        self, product_id: int, query_vector: list[float], top_k: int = 4
    ) -> list[ChunkHit]:
        try:
            results = self.client().search(
                collection_name=self.collection,
                data=[query_vector],
                anns_field="vector",
                filter=f"product_id == {int(product_id)}",
                limit=max(1, top_k),
                output_fields=["text", "source", "doc_id", "product_id", "chunk_index"],
                search_params={"metric_type": "COSINE", "params": {}},
            )
        except Exception as exc:  # pragma: no cover - 依赖真实 Milvus
            raise VectorStoreError(f"Milvus 检索失败: {exc}") from exc

        hits: list[ChunkHit] = []
        for row in results[0] if results else []:
            entity = row.get("entity", {}) if isinstance(row, dict) else {}
            hits.append(
                ChunkHit(
                    text=entity.get("text", ""),
                    source=entity.get("source", ""),
                    doc_id=entity.get("doc_id", ""),
                    product_id=int(entity.get("product_id", product_id)),
                    chunk_index=int(entity.get("chunk_index", 0)),
                    score=float(row.get("distance", 0.0)) if isinstance(row, dict) else 0.0,
                )
            )
        return hits

    def count(self, product_id: int | None = None) -> int:
        expr = f"product_id == {int(product_id)}" if product_id is not None else ""
        try:
            rows = self.client().query(
                collection_name=self.collection, filter=expr, output_fields=["count(*)"]
            )
            return int(rows[0].get("count(*)", 0)) if rows else 0
        except Exception as exc:  # pragma: no cover - 依赖真实 Milvus
            raise VectorStoreError(f"统计 Milvus 向量失败: {exc}") from exc


@lru_cache(maxsize=1)
def get_vector_store() -> InsuranceVectorStore:
    return InsuranceVectorStore()