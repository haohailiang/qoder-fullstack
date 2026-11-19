"""流式问答接口测试：SSE 帧格式、拒答分支、落库与用户隔离。"""

from __future__ import annotations

import json

from langchain_core.messages import AIMessage, AIMessageChunk

from app.api import chat as chat_api
from app.config import get_settings
from app.models import ChatMessage, ChatSession

USER_HEADERS = {"X-User-Id": "2", "X-User-Name": "alice", "X-User-Role": "USER"}
OTHER_HEADERS = {"X-User-Id": "3", "X-User-Name": "bob", "X-User-Role": "USER"}

SOURCE = {
    "documentId": "doc-1",
    "filename": "安心百万医疗险条款.pdf",
    "chunkIndex": 0,
    "score": 0.87,
    "snippet": "本保险的等待期为合同生效后三十日。",
}


class FakeGraph:
    def __init__(self, steps):
        self.steps = steps

    async def astream(self, inputs, config, stream_mode):
        for step in self.steps:
            yield step


class FakeAgent:
    def __init__(self, steps):
        self.graph = FakeGraph(steps)

    def thread_config(self, user_id, product_id, session_id, checkpoint_id=None):
        return {"configurable": {"thread_id": f"u{user_id}:p{product_id}:s{session_id}"}}


def _create_session(client, headers=USER_HEADERS, product_id=9) -> str:
    response = client.post("/sessions", json={"productId": product_id}, headers=headers)
    assert response.status_code == 200
    return response.json()["id"]


def _ask(client, session_id, message="等待期多久？", headers=USER_HEADERS, product_id=9):
    return client.post(
        "/chat",
        json={"sessionId": session_id, "productId": product_id, "message": message},
        headers=headers,
    )


def _events(body: str) -> list[tuple[str, dict]]:
    events: list[tuple[str, dict]] = []
    for block in body.split("\n\n"):
        if not block.strip():
            continue
        event = "message"
        data = ""
        for line in block.split("\n"):
            if line.startswith("event:"):
                event = line[len("event:"):].strip()
            elif line.startswith("data:"):
                data += line[len("data:"):].strip()
        events.append((event, json.loads(data) if data else {}))
    return events


def test_chat_streams_tokens_and_persists_turn(client, monkeypatch, db):
    session_id = _create_session(client)
    steps = [
        ("messages", (AIMessageChunk(content="等待期为"), {"langgraph_node": "generate"})),
        ("messages", (AIMessageChunk(content="三十日。"), {"langgraph_node": "generate"})),
        ("updates", {"generate": {"sources": [SOURCE]}}),
    ]
    monkeypatch.setattr(chat_api, "get_agent", lambda: FakeAgent(steps))

    response = _ask(client, session_id)

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")

    events = _events(response.text)
    names = [name for name, _ in events]
    assert names[0] == "meta"
    assert names[-1] == "done"
    tokens = [payload["text"] for name, payload in events if name == "token"]
    assert "".join(tokens) == "等待期为三十日。"

    done = events[-1][1]
    assert done["refused"] is False
    assert done["sources"][0]["filename"] == SOURCE["filename"]

    db.expire_all()
    rows = (
        db.query(ChatMessage)
        .filter_by(session_id=session_id)
        .order_by(ChatMessage.id.asc())
        .all()
    )
    assert [row.role for row in rows] == ["user", "assistant"]
    assert rows[1].content == "等待期为三十日。"
    assert rows[1].sources[0]["documentId"] == "doc-1"
    # 首轮提问会更新会话标题
    assert db.get(ChatSession, session_id).title == "等待期多久？"


def test_chat_refusal_branch_emits_fixed_message(client, monkeypatch):
    settings = get_settings()
    session_id = _create_session(client)
    steps = [
        ("updates", {"refuse": {"messages": [AIMessage(content=settings.refusal_message)], "refused": True}}),
    ]
    monkeypatch.setattr(chat_api, "get_agent", lambda: FakeAgent(steps))

    response = _ask(client, session_id, message="今天天气如何？")

    events = _events(response.text)
    tokens = [payload["text"] for name, payload in events if name == "token"]
    assert tokens == [settings.refusal_message]
    done = events[-1]
    assert done[0] == "done"
    assert done[1]["refused"] is True
    assert done[1]["sources"] == []


def test_chat_rejects_other_user_session(client, monkeypatch):
    session_id = _create_session(client)
    monkeypatch.setattr(chat_api, "get_agent", lambda: FakeAgent([]))

    response = _ask(client, session_id, headers=OTHER_HEADERS)

    assert response.status_code == 404


def test_chat_rejects_product_mismatch(client, monkeypatch, db):
    session_id = _create_session(client, product_id=9)
    monkeypatch.setattr(chat_api, "get_agent", lambda: FakeAgent([]))

    response = _ask(client, session_id, product_id=10)

    assert response.status_code == 400
    assert response.json()["detail"] == "会话与商品不匹配"


def test_chat_error_event_persists_question(client, monkeypatch, db):
    class BrokenAgent(FakeAgent):
        def __init__(self):
            self.graph = None

        def thread_config(self, *args, **kwargs):
            return {"configurable": {"thread_id": "broken"}}

    class BrokenGraph:
        async def astream(self, inputs, config, stream_mode):
            raise RuntimeError("模型服务不可用")
            yield  # pragma: no cover

    agent = BrokenAgent()
    agent.graph = BrokenGraph()
    session_id = _create_session(client)
    monkeypatch.setattr(chat_api, "get_agent", lambda: agent)

    response = _ask(client, session_id)

    events = _events(response.text)
    assert events[0][0] == "meta"
    assert events[-1][0] == "error"
    assert "模型服务不可用" in events[-1][1]["message"]

    db.expire_all()
    rows = db.query(ChatMessage).filter_by(session_id=session_id).all()
    assert [(row.role, row.content) for row in rows] == [("user", "等待期多久？")]