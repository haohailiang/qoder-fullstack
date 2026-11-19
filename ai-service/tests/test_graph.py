"""LangGraph 问答图测试：命中生成、低分/空结果拒答且不调用大模型。"""

from __future__ import annotations

import asyncio

from langchain_core.messages import AIMessage

from app.config import get_settings
from app.rag.graph import InsuranceAgent
from app.rag.vector_store import ChunkHit


class FakeEmbeddings:
    def embed_query(self, text: str) -> list[float]:
        return [0.1] * 8

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [[0.1] * 8 for _ in texts]


class FakeVectorStore:
    def __init__(self, hits: list[ChunkHit] | None = None) -> None:
        self.hits = hits or []
        self.calls: list[tuple[int, int]] = []

    def search(self, product_id: int, query_vector: list[float], top_k: int = 4) -> list[ChunkHit]:
        self.calls.append((product_id, top_k))
        return self.hits


class FakeLLM:
    def __init__(self, answer: str) -> None:
        self.answer = answer
        self.calls: list[list] = []

    async def ainvoke(self, messages):
        self.calls.append(list(messages))
        return AIMessage(content=self.answer)


def _hit(score: float) -> ChunkHit:
    return ChunkHit(
        text="本保险的等待期为合同生效后三十日。",
        source="安心百万医疗险条款.pdf",
        doc_id="doc-1",
        product_id=9,
        chunk_index=0,
        score=score,
    )


def _run(agent: InsuranceAgent, question: str = "等待期多久？"):
    config = agent.thread_config(user_id=1, product_id=9, session_id="s1")
    inputs = {"messages": [("user", question)], "question": question, "product_id": 9}
    return asyncio.run(agent.graph.ainvoke(inputs, config=config))


def test_empty_retrieval_refuses_without_calling_llm():
    settings = get_settings()
    llm = FakeLLM("模型不应该被调用")
    agent = InsuranceAgent(
        settings=settings,
        embeddings=FakeEmbeddings(),
        vector_store=FakeVectorStore(hits=[]),
        llm=llm,
    )

    state = _run(agent)

    assert llm.calls == []
    assert state["refused"] is True
    assert state["messages"][-1].content == settings.refusal_message
    assert state["sources"] == []


def test_low_score_refuses_without_calling_llm():
    settings = get_settings()
    llm = FakeLLM("模型不应该被调用")
    agent = InsuranceAgent(
        settings=settings,
        embeddings=FakeEmbeddings(),
        vector_store=FakeVectorStore(hits=[_hit(score=settings.rag_min_score - 0.1)]),
        llm=llm,
    )

    state = _run(agent)

    assert llm.calls == []
    assert state["refused"] is True
    assert state["messages"][-1].content == settings.refusal_message


def test_high_score_generates_answer_with_sources():
    settings = get_settings()
    llm = FakeLLM("等待期为合同生效后三十日（来源：安心百万医疗险条款.pdf）。")
    store = FakeVectorStore(hits=[_hit(score=settings.rag_min_score + 0.4)])
    agent = InsuranceAgent(
        settings=settings,
        embeddings=FakeEmbeddings(),
        vector_store=store,
        llm=llm,
    )

    state = _run(agent)

    assert len(llm.calls) == 1
    assert store.calls and store.calls[0][0] == 9
    assert state["refused"] is False
    assert "三十日" in state["messages"][-1].content
    assert state["sources"][0]["filename"] == "安心百万医疗险条款.pdf"


def test_llm_refusal_message_clears_sources():
    settings = get_settings()
    llm = FakeLLM(settings.refusal_message)
    agent = InsuranceAgent(
        settings=settings,
        embeddings=FakeEmbeddings(),
        vector_store=FakeVectorStore(hits=[_hit(score=0.9)]),
        llm=llm,
    )

    state = _run(agent)

    assert state["refused"] is True
    assert state["sources"] == []
    assert state["messages"][-1].content == settings.refusal_message


def test_retrieval_failure_falls_back_to_refusal():
    class BrokenStore(FakeVectorStore):
        def search(self, *args, **kwargs):
            raise RuntimeError("Milvus 不可用")

    settings = get_settings()
    llm = FakeLLM("模型不应该被调用")
    agent = InsuranceAgent(
        settings=settings,
        embeddings=FakeEmbeddings(),
        vector_store=BrokenStore(),
        llm=llm,
    )

    state = _run(agent)

    assert llm.calls == []
    assert state["refused"] is True
    assert state["messages"][-1].content == settings.refusal_message