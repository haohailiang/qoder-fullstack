"""会话与检查点接口测试：用户隔离、管理员校验、回溯。"""

from __future__ import annotations

from app.api import sessions as sessions_api
from app.models import ChatMessage, ChatSession

ADMIN_HEADERS = {"X-User-Id": "1", "X-User-Name": "admin", "X-User-Role": "ADMIN"}
USER_HEADERS = {"X-User-Id": "2", "X-User-Name": "alice", "X-User-Role": "USER"}
OTHER_HEADERS = {"X-User-Id": "3", "X-User-Name": "bob", "X-User-Role": "USER"}


class FakeAgent:
    def __init__(self, restored=None, checkpoints=None):
        self.restored = restored
        self.checkpoints = checkpoints or []

    def thread_config(self, user_id, product_id, session_id, checkpoint_id=None):
        return {"configurable": {"thread_id": f"u{user_id}:p{product_id}:s{session_id}"}}

    async def list_checkpoints(self, config, limit: int = 50):
        return self.checkpoints

    async def read_checkpoint(self, config, checkpoint_id):
        return self.restored


def _create_session(client, headers=USER_HEADERS, product_id=9):
    response = client.post(
        "/sessions", json={"productId": product_id}, headers=headers
    )
    assert response.status_code == 200
    return response.json()


def test_create_and_list_sessions_isolated_by_user(client):
    created = _create_session(client)
    assert created["productId"] == 9
    assert created["title"] == "新会话"

    mine = client.get("/sessions", params={"productId": 9}, headers=USER_HEADERS)
    assert mine.status_code == 200
    assert [item["id"] for item in mine.json()] == [created["id"]]

    others = client.get("/sessions", params={"productId": 9}, headers=OTHER_HEADERS)
    assert others.status_code == 200
    assert others.json() == []


def test_other_user_cannot_read_messages(client):
    created = _create_session(client)

    response = client.get(f"/sessions/{created['id']}/messages", headers=OTHER_HEADERS)

    assert response.status_code == 404
    assert response.json()["detail"] == "会话不存在"


def test_other_user_cannot_delete_session(client):
    created = _create_session(client)

    response = client.delete(f"/sessions/{created['id']}", headers=OTHER_HEADERS)

    assert response.status_code == 404


def test_other_user_cannot_read_checkpoints(client):
    created = _create_session(client)

    response = client.get(f"/sessions/{created['id']}/checkpoints", headers=OTHER_HEADERS)

    assert response.status_code == 404


def test_missing_identity_is_unauthorized(client):
    response = client.get("/sessions", params={"productId": 9})

    assert response.status_code == 401


def test_delete_session_removes_messages(client, db):
    created = _create_session(client)
    db.add(ChatMessage(session_id=created["id"], role="user", content="你好"))
    db.commit()

    response = client.delete(f"/sessions/{created['id']}", headers=USER_HEADERS)

    assert response.status_code == 200
    assert db.get(ChatSession, created["id"]) is None
    assert db.query(ChatMessage).filter_by(session_id=created["id"]).count() == 0


def test_list_checkpoints(client, monkeypatch):
    created = _create_session(client)
    monkeypatch.setattr(
        sessions_api,
        "get_agent",
        lambda: FakeAgent(
            checkpoints=[
                {
                    "checkpointId": "cp-1",
                    "step": 1,
                    "createdAt": "2025-01-01T00:00:00",
                    "messageCount": 2,
                    "preview": "等待期为三十日",
                }
            ]
        ),
    )

    response = client.get(f"/sessions/{created['id']}/checkpoints", headers=USER_HEADERS)

    assert response.status_code == 200
    assert response.json()[0]["checkpointId"] == "cp-1"


def test_restore_returns_409_when_checkpoint_lost(client, monkeypatch):
    created = _create_session(client)
    monkeypatch.setattr(sessions_api, "get_agent", lambda: FakeAgent(restored=None))

    response = client.post(
        f"/sessions/{created['id']}/restore",
        json={"checkpointId": "cp-missing"},
        headers=USER_HEADERS,
    )

    assert response.status_code == 409
    assert "InMemorySaver" in response.json()["detail"]


def test_restore_rebuilds_messages_and_marks_checkpoint(client, monkeypatch, db):
    created = _create_session(client)
    db.add(ChatMessage(session_id=created["id"], role="user", content="旧问题"))
    db.commit()

    restored = [
        {"role": "user", "content": "等待期多久？", "sources": []},
        {"role": "assistant", "content": "三十日。", "sources": []},
    ]
    monkeypatch.setattr(sessions_api, "get_agent", lambda: FakeAgent(restored=restored))

    response = client.post(
        f"/sessions/{created['id']}/restore",
        json={"checkpointId": "cp-2"},
        headers=USER_HEADERS,
    )

    assert response.status_code == 200
    assert response.json()["checkpointId"] == "cp-2"
    assert [item["content"] for item in response.json()["messages"]] == ["等待期多久？", "三十日。"]

    db.expire_all()
    rows = (
        db.query(ChatMessage)
        .filter_by(session_id=created["id"])
        .order_by(ChatMessage.id.asc())
        .all()
    )
    assert [row.content for row in rows] == ["等待期多久？", "三十日。"]
    assert db.get(ChatSession, created["id"]).last_checkpoint_id == "cp-2"