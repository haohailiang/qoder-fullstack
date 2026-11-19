"""流式问答接口（SSE）。

前端通过 fetch + ReadableStream 逐段渲染，事件类型：
- meta   ：会话信息
- token  ：文本增量
- sources：本轮引用的文档片段（随 done 之前发送）
- done   ：本轮结束
- error  ：生成失败
"""

from __future__ import annotations

import json
import logging
from datetime import datetime
from typing import Any, AsyncIterator

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import StreamingResponse
from langchain_core.messages import HumanMessage
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy.orm import Session

from ..config import get_settings
from ..db import SessionLocal, get_db
from ..deps import CurrentUser, require_user
from ..models import ChatMessage, ChatSession
from ..rag.graph import get_agent
from .sessions import get_owned_session

log = logging.getLogger(__name__)

router = APIRouter(tags=["insurance-chat"])

SSE_HEADERS = {
    "Cache-Control": "no-cache",
    "Connection": "keep-alive",
    # 防止 Nginx / 网关缓冲导致「不流式」
    "X-Accel-Buffering": "no",
}


class ChatRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    session_id: str = Field(alias="sessionId")
    product_id: int = Field(alias="productId")
    message: str = Field(min_length=1, max_length=2000)


def sse_event(event: str, payload: dict[str, Any]) -> str:
    return f"event: {event}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"


def message_text(message: Any) -> str:
    content = getattr(message, "content", "")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if isinstance(item, dict):
                parts.append(str(item.get("text", "")))
            else:
                parts.append(str(item))
        return "".join(parts)
    return str(content) if content else ""


def _persist_turn(
    session_id: str,
    question: str,
    answer: str,
    sources: list[dict[str, Any]],
    refused: bool,
) -> None:
    with SessionLocal() as db:
        session = db.get(ChatSession, session_id)
        if session is None:
            return
        db.add(ChatMessage(session_id=session_id, role="user", content=question))
        db.add(
            ChatMessage(
                session_id=session_id,
                role="assistant",
                content=answer,
                sources=sources or [],
            )
        )
        if session.title in ("", "新会话"):
            session.title = question[:50]
        # 本轮已消费掉待恢复的检查点，避免下一轮继续从旧状态分叉
        session.last_checkpoint_id = None
        session.updated_at = datetime.now()
        db.commit()
    log.info("会话落库完成: session=%s, refused=%s, chars=%s", session_id, refused, len(answer))


def _persist_question(session_id: str, question: str) -> None:
    with SessionLocal() as db:
        if db.get(ChatSession, session_id) is None:
            return
        db.add(ChatMessage(session_id=session_id, role="user", content=question))
        db.commit()


@router.post("/chat", summary="保险咨询机器人流式问答")
async def chat(
    payload: ChatRequest,
    user: CurrentUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> StreamingResponse:
    session = get_owned_session(db, payload.session_id, user.user_id)
    if session.product_id != payload.product_id:
        raise HTTPException(status_code=400, detail="会话与商品不匹配")
    pending_checkpoint = session.last_checkpoint_id

    settings = get_settings()
    agent = get_agent()
    config = agent.thread_config(
        user.user_id, payload.product_id, payload.session_id, pending_checkpoint
    )
    question = payload.message.strip()
    if not question:
        raise HTTPException(status_code=400, detail="提问内容不能为空")

    async def event_stream() -> AsyncIterator[str]:
        yield sse_event("meta", {"sessionId": payload.session_id})
        answer_parts: list[str] = []
        sources: list[dict[str, Any]] = []
        refused = False

        try:
            inputs = {
                "messages": [HumanMessage(content=question)],
                "question": question,
                "product_id": payload.product_id,
            }
            async for mode, chunk in agent.graph.astream(
                inputs, config=config, stream_mode=["messages", "updates"]
            ):
                if mode == "messages":
                    message, metadata = chunk
                    if (metadata or {}).get("langgraph_node") != "generate":
                        continue
                    text = message_text(message)
                    if text:
                        answer_parts.append(text)
                        yield sse_event("token", {"text": text})
                elif mode == "updates":
                    for node, update in (chunk or {}).items():
                        if not isinstance(update, dict):
                            continue
                        if update.get("sources"):
                            sources = update["sources"]
                        if update.get("refused"):
                            refused = True
                        # 拒答分支不调用大模型，整句话在此一次性下发
                        if node == "refuse":
                            for message in update.get("messages") or []:
                                text = message_text(message)
                                if text:
                                    answer_parts.append(text)
                                    yield sse_event("token", {"text": text})

            answer = "".join(answer_parts).strip()
            if not answer:
                answer = settings.refusal_message
                refused = True
                yield sse_event("token", {"text": answer})

            _persist_turn(payload.session_id, question, answer, sources, refused)
            yield sse_event(
                "done",
                {
                    "sessionId": payload.session_id,
                    "refused": refused,
                    "sources": sources,
                },
            )
        except Exception as exc:  # pragma: no cover - 依赖外部服务
            log.exception("流式问答失败: session=%s", payload.session_id)
            _persist_question(payload.session_id, question)
            yield sse_event("error", {"message": f"生成回答失败: {exc}"})

    return StreamingResponse(event_stream(), media_type="text/event-stream", headers=SSE_HEADERS)