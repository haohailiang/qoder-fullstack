"""ai-service 表模型：文档元数据、会话、消息。

表名统一 ai_ 前缀，与电商业务表同库但互不影响；启动时 create_all 幂等建表。
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

from sqlalchemy import JSON, BigInteger, DateTime, Index, Integer, String, Text
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

# SQLite（测试）自增主键需为 INTEGER，MySQL 使用 BIGINT
BigIntPk = BigInteger().with_variant(Integer, "sqlite")


class Base(DeclarativeBase):
    pass


class InsuranceDocument(Base):
    """保险 PDF 知识库文档（一份 PDF 对应一条记录，向量按 product_id + doc_id 归档）。"""

    __tablename__ = "ai_insurance_document"

    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    product_id: Mapped[int] = mapped_column(BigInteger, nullable=False, index=True)
    filename: Mapped[str] = mapped_column(String(255), nullable=False)
    file_path: Mapped[str] = mapped_column(String(500), nullable=False)
    file_size: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    # processing / ready / failed
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="processing")
    mineru_batch_id: Mapped[str | None] = mapped_column(String(100), nullable=True)
    chunk_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    error_message: Mapped[str | None] = mapped_column(String(1000), nullable=True)
    created_by: Mapped[str | None] = mapped_column(String(100), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.now, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, default=datetime.now, onupdate=datetime.now, nullable=False
    )

    def to_dict(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "productId": self.product_id,
            "filename": self.filename,
            "fileSize": self.file_size,
            "status": self.status,
            "chunkCount": self.chunk_count,
            "errorMessage": self.error_message,
            "createdBy": self.created_by,
            "createdAt": self.created_at.isoformat(timespec="seconds"),
            "updatedAt": self.updated_at.isoformat(timespec="seconds"),
        }


class ChatSession(Base):
    """会话：按 user_id + product_id 隔离，thread_id 由三者共同确定。"""

    __tablename__ = "ai_chat_session"

    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    product_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    title: Mapped[str] = mapped_column(String(100), nullable=False, default="新会话")
    # 时空旅行：待恢复的检查点；对话继续后清空，避免再次从旧检查点分叉
    last_checkpoint_id: Mapped[str | None] = mapped_column(String(120), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.now, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, default=datetime.now, onupdate=datetime.now, nullable=False
    )

    __table_args__ = (Index("idx_ai_session_owner", "user_id", "product_id"),)

    def to_dict(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "productId": self.product_id,
            "title": self.title,
            "createdAt": self.created_at.isoformat(timespec="seconds"),
            "updatedAt": self.updated_at.isoformat(timespec="seconds"),
        }


class ChatMessage(Base):
    """会话消息：进入数据库保证历史在服务重启后仍可查看。"""

    __tablename__ = "ai_chat_message"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    session_id: Mapped[str] = mapped_column(String(32), nullable=False, index=True)
    # user / assistant
    role: Mapped[str] = mapped_column(String(16), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    sources: Mapped[list[dict[str, Any]] | None] = mapped_column(JSON, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.now, nullable=False)

    def to_dict(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "role": self.role,
            "content": self.content,
            "sources": self.sources or [],
            "createdAt": self.created_at.isoformat(timespec="seconds"),
        }