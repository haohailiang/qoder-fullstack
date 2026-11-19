"""会话与检查点（时空旅行）接口：全部按 X-User-Id 隔离。"""

from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..db import get_db
from ..deps import CurrentUser, require_user
from ..models import ChatMessage, ChatSession
from ..rag.graph import get_agent

router = APIRouter(prefix="/sessions", tags=["insurance-sessions"])


class CreateSessionRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    product_id: int = Field(alias="productId")
    title: str | None = None


class RestoreRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    checkpoint_id: str = Field(alias="checkpointId")


def get_owned_session(db: Session, session_id: str, user_id: int) -> ChatSession:
    session = db.get(ChatSession, session_id)
    if session is None or session.user_id != user_id:
        # 不区分「不存在」与「不属于当前用户」，避免探测他人会话
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="会话不存在")
    return session


@router.post("", summary="新建会话")
def create_session(
    payload: CreateSessionRequest,
    user: CurrentUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> dict:
    session = ChatSession(
        id=uuid.uuid4().hex,
        user_id=user.user_id,
        product_id=payload.product_id,
        title=(payload.title or "新会话")[:100],
    )
    db.add(session)
    db.commit()
    db.refresh(session)
    return session.to_dict()


@router.get("", summary="查询当前用户在某保险商品下的会话列表")
def list_sessions(
    product_id: int = Query(alias="productId", description="保险商品 ID"),
    user: CurrentUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[dict]:
    rows = db.scalars(
        select(ChatSession)
        .where(ChatSession.user_id == user.user_id, ChatSession.product_id == product_id)
        .order_by(ChatSession.updated_at.desc())
    ).all()
    return [row.to_dict() for row in rows]


@router.get("/{session_id}/messages", summary="查看会话历史消息")
def list_messages(
    session_id: str,
    user: CurrentUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[dict]:
    get_owned_session(db, session_id, user.user_id)
    rows = db.scalars(
        select(ChatMessage)
        .where(ChatMessage.session_id == session_id)
        .order_by(ChatMessage.id.asc())
    ).all()
    return [row.to_dict() for row in rows]


@router.delete("/{session_id}", summary="删除会话及其消息")
def delete_session(
    session_id: str,
    user: CurrentUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> dict:
    session = get_owned_session(db, session_id, user.user_id)
    db.query(ChatMessage).filter(ChatMessage.session_id == session_id).delete()
    db.delete(session)
    db.commit()
    return {"id": session_id, "deleted": True}


@router.get("/{session_id}/checkpoints", summary="列出该会话的检查点（可回溯的状态）")
async def list_checkpoints(
    session_id: str,
    user: CurrentUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[dict]:
    session = get_owned_session(db, session_id, user.user_id)
    agent = get_agent()
    config = agent.thread_config(user.user_id, session.product_id, session.id)
    try:
        return await agent.list_checkpoints(config)
    except Exception as exc:  # pragma: no cover - 依赖运行时
        raise HTTPException(status_code=503, detail=f"读取检查点失败: {exc}") from exc


@router.post("/{session_id}/restore", summary="恢复到指定历史状态（时空旅行）")
async def restore_checkpoint(
    session_id: str,
    payload: RestoreRequest,
    user: CurrentUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> dict:
    session = get_owned_session(db, session_id, user.user_id)
    agent = get_agent()
    config = agent.thread_config(user.user_id, session.product_id, session.id)

    try:
        restored = await agent.read_checkpoint(config, payload.checkpoint_id)
    except Exception as exc:  # pragma: no cover - 依赖运行时
        raise HTTPException(status_code=503, detail=f"恢复检查点失败: {exc}") from exc

    if restored is None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="检查点不存在（InMemorySaver 在服务重启后会丢失，请继续新对话）",
        )

    # 用快照重建该会话的持久化消息，保证历史列表与回溯后的上下文一致
    db.query(ChatMessage).filter(ChatMessage.session_id == session_id).delete()
    for item in restored:
        db.add(
            ChatMessage(
                session_id=session_id,
                role=item["role"],
                content=item["content"],
                sources=[],
            )
        )
    session.last_checkpoint_id = payload.checkpoint_id
    db.commit()

    return {"sessionId": session_id, "checkpointId": payload.checkpoint_id, "messages": restored}