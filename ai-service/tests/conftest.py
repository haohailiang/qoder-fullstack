"""ai-service 测试夹具。

关键点：在导入 app.* 之前把数据库/向量库等外部依赖切到本地临时资源，
避免测试连真实 MySQL / Milvus。
"""

from __future__ import annotations

import os
import sys
import tempfile
from pathlib import Path

SERVICE_ROOT = Path(__file__).resolve().parents[1]
if str(SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVICE_ROOT))

TMP_ROOT = Path(tempfile.mkdtemp(prefix="ai-service-tests-"))

# 必须在 import app.* 之前设置：db.py 在模块导入时就会创建 engine
os.environ["AI_MYSQL_URL"] = f"sqlite:///{(TMP_ROOT / 'test.db').as_posix()}"
os.environ["DATA_DIR"] = str(TMP_ROOT / "data")
os.environ["VERIFY_GATEWAY_SIGNATURE"] = "false"
os.environ["ZHIPU_API_KEY"] = "test-zhipu-key"
os.environ["MINERU_API_TOKEN"] = ""
os.environ["RAG_MIN_SCORE"] = "0.35"
os.environ["RAG_TOP_K"] = "4"
os.environ["REFUSAL_MESSAGE"] = "抱歉，我只能回答与该保险文档相关的问题。"

import pytest  # noqa: E402
from fastapi import FastAPI  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

ADMIN_HEADERS = {"X-User-Id": "1", "X-User-Name": "admin", "X-User-Role": "ADMIN"}
USER_HEADERS = {"X-User-Id": "2", "X-User-Name": "alice", "X-User-Role": "USER"}
OTHER_HEADERS = {"X-User-Id": "3", "X-User-Name": "bob", "X-User-Role": "USER"}


def _built_client() -> TestClient:
    """只挂载业务路由，不触发 main.py 的 lifespan（会连 Milvus）。"""
    from app.api import chat, documents, sessions

    app = FastAPI()
    app.include_router(chat.router)
    app.include_router(sessions.router)
    app.include_router(documents.router)
    return TestClient(app)


@pytest.fixture(scope="session", autouse=True)
def _init_database():
    from app.db import init_db

    init_db()
    yield


@pytest.fixture(autouse=True)
def _clean_tables(_init_database):
    """每个用例前清空业务表，保证用例互不影响。"""
    from app.db import SessionLocal
    from app.models import ChatMessage, ChatSession, InsuranceDocument

    with SessionLocal() as db:
        db.query(ChatMessage).delete()
        db.query(ChatSession).delete()
        db.query(InsuranceDocument).delete()
        db.commit()
    yield


@pytest.fixture()
def client() -> TestClient:
    return _built_client()


@pytest.fixture()
def db():
    from app.db import SessionLocal

    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()