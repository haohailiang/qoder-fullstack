"""LangGraph 保险问答图。

流程：START → retrieve（Milvus 按商品检索）→ 条件边
  - 命中且最高相似度达标 → generate（GLM-5.3 严格基于片段流式回答）
  - 未命中 / 相似度过低 → refuse（固定拒答话术，完全不调用大模型）

检查点：InMemorySaver，thread_id = u{userId}:p{productId}:s{sessionId}，
既支撑多轮上下文，也支撑 get_state_history / checkpoint_id 的「时空旅行」。
"""

from __future__ import annotations

import asyncio
import logging
from functools import lru_cache
from typing import Annotated, Any, TypedDict

from langchain_core.messages import AIMessage, AnyMessage, HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages

try:  # langgraph >= 0.2.50 提供 InMemorySaver
    from langgraph.checkpoint.memory import InMemorySaver
except ImportError:  # pragma: no cover - 旧版本兼容
    from langgraph.checkpoint.memory import MemorySaver as InMemorySaver

from ..config import Settings, get_settings
from .embeddings import get_embeddings
from .prompts import build_system_prompt
from .vector_store import InsuranceVectorStore, get_vector_store

log = logging.getLogger(__name__)


class AgentState(TypedDict, total=False):
    messages: Annotated[list[AnyMessage], add_messages]
    question: str
    product_id: int
    # 检索片段（纯 dict，保证检查点可序列化）
    context: list[dict[str, Any]]
    sources: list[dict[str, Any]]
    best_score: float
    refused: bool


def build_llm(settings: Settings | None = None) -> ChatOpenAI:
    settings = settings or get_settings()
    settings.require_llm_config()
    return ChatOpenAI(
        model=settings.zhipu_model,
        api_key=settings.zhipu_api_key,
        base_url=settings.zhipu_base_url,
        temperature=settings.zhipu_temperature,
        max_tokens=settings.zhipu_max_tokens,
        streaming=True,
        timeout=120,
        max_retries=1,
    )


def messages_to_dicts(messages: list[AnyMessage]) -> list[dict[str, Any]]:
    """把 LangChain 消息转成前端可直接渲染的结构。"""
    result: list[dict[str, Any]] = []
    for message in messages:
        role = getattr(message, "type", "")
        if role not in ("human", "ai"):
            continue
        content = message.content if isinstance(message.content, str) else str(message.content)
        result.append(
            {"role": "user" if role == "human" else "assistant", "content": content, "sources": []}
        )
    return result


class InsuranceAgent:
    """保险咨询问答 Agent（可注入 embeddings / vector_store / llm 以便测试）。"""

    def __init__(
        self,
        settings: Settings | None = None,
        embeddings: Any | None = None,
        vector_store: InsuranceVectorStore | Any | None = None,
        llm: Any | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self._embeddings = embeddings
        self._vector_store = vector_store
        self._llm = llm
        self.checkpointer = InMemorySaver()
        self.graph = self._build_graph()

    # ---------- 惰性依赖 ----------
    @property
    def embeddings(self) -> Any:
        if self._embeddings is None:
            self._embeddings = get_embeddings()
        return self._embeddings

    @property
    def vector_store(self) -> Any:
        if self._vector_store is None:
            self._vector_store = get_vector_store()
        return self._vector_store

    @property
    def llm(self) -> Any:
        if self._llm is None:
            self._llm = build_llm(self.settings)
        return self._llm

    # ---------- 节点 ----------
    async def _retrieve(self, state: AgentState) -> dict[str, Any]:
        question = (state.get("question") or "").strip()
        product_id = int(state.get("product_id") or 0)
        if not question:
            return {"context": [], "sources": [], "best_score": 0.0, "refused": False}

        try:
            vector = await asyncio.to_thread(self.embeddings.embed_query, question)
            hits = await asyncio.to_thread(
                self.vector_store.search, product_id, vector, self.settings.rag_top_k
            )
        except Exception as exc:
            log.error("检索失败，按拒答处理: product=%s, error=%s", product_id, exc)
            return {"context": [], "sources": [], "best_score": 0.0, "refused": False}

        hits = [hit for hit in hits if (hit.text or "").strip()]
        best_score = max((float(hit.score) for hit in hits), default=0.0)
        return {
            "context": [dict(hit.__dict__) for hit in hits],
            "sources": [hit.to_source() for hit in hits],
            "best_score": best_score,
            "refused": False,
        }

    def _route_after_retrieve(self, state: AgentState) -> str:
        has_context = bool(state.get("context"))
        score = float(state.get("best_score") or 0.0)
        if has_context and score >= self.settings.rag_min_score:
            return "generate"
        log.info("检索未命中或相似度不足，走拒答分支: score=%.3f", score)
        return "refuse"

    async def _refuse(self, state: AgentState) -> dict[str, Any]:
        return {
            "messages": [AIMessage(content=self.settings.refusal_message)],
            "sources": [],
            "context": [],
            "refused": True,
        }

    async def _generate(self, state: AgentState) -> dict[str, Any]:
        hits = state.get("context") or []
        history = state.get("messages") or []
        system = SystemMessage(
            content=build_system_prompt(hits, self.settings.refusal_message)
        )
        reply = await self.llm.ainvoke([system, *history])
        text = reply.content if isinstance(reply.content, str) else str(reply.content)
        refused = text.strip() == self.settings.refusal_message.strip()
        return {
            "messages": [reply],
            "sources": [] if refused else (state.get("sources") or []),
            "refused": refused,
        }

    # ---------- 图 ----------
    def _build_graph(self) -> Any:
        builder = StateGraph(AgentState)
        builder.add_node("retrieve", self._retrieve)
        builder.add_node("generate", self._generate)
        builder.add_node("refuse", self._refuse)

        builder.add_edge(START, "retrieve")
        builder.add_conditional_edges(
            "retrieve",
            self._route_after_retrieve,
            {"generate": "generate", "refuse": "refuse"},
        )
        builder.add_edge("generate", END)
        builder.add_edge("refuse", END)
        return builder.compile(checkpointer=self.checkpointer)

    # ---------- 会话 / 时空旅行 ----------
    def thread_config(
        self,
        user_id: int,
        product_id: int,
        session_id: str,
        checkpoint_id: str | None = None,
    ) -> dict[str, Any]:
        configurable: dict[str, Any] = {
            "thread_id": f"u{user_id}:p{product_id}:s{session_id}"
        }
        if checkpoint_id:
            configurable["checkpoint_id"] = checkpoint_id
        return {"configurable": configurable}

    async def list_checkpoints(
        self, config: dict[str, Any], limit: int = 50
    ) -> list[dict[str, Any]]:
        snapshots: list[dict[str, Any]] = []
        async for snapshot in self.graph.aget_state_history(config, limit=limit):
            values = getattr(snapshot, "values", None) or {}
            messages = values.get("messages") or []
            preview = ""
            if messages:
                last = messages[-1]
                preview = (last.content if isinstance(last.content, str) else str(last.content))[:60]
            snapshots.append(
                {
                    "checkpointId": snapshot.config["configurable"]["checkpoint_id"],
                    "step": (snapshot.metadata or {}).get("step"),
                    "createdAt": str(getattr(snapshot, "created_at", "") or ""),
                    "messageCount": len(messages),
                    "preview": preview,
                }
            )
        return snapshots

    async def read_checkpoint(
        self, config: dict[str, Any], checkpoint_id: str
    ) -> list[dict[str, Any]] | None:
        checkpoint_config = {
            "configurable": {**config["configurable"], "checkpoint_id": checkpoint_id}
        }
        snapshot = await self.graph.aget_state(checkpoint_config)
        values = getattr(snapshot, "values", None) or {}
        messages = values.get("messages") or []
        if not messages:
            return None
        return messages_to_dicts(messages)


@lru_cache(maxsize=1)
def get_agent() -> InsuranceAgent:
    return InsuranceAgent()