"""FastAPI 入口：经网关 /api/ai/** 暴露（网关 StripPrefix=2，故路由不带 /ai 前缀）。"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from starlette.concurrency import run_in_threadpool

from .api import chat, documents, sessions
from .config import get_settings
from .db import init_db
from .rag.vector_store import get_vector_store

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
log = logging.getLogger("insurance-agent")

settings = get_settings()


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings.apply_langsmith_env()
    init_db()
    try:
        await run_in_threadpool(get_vector_store().ensure_collection)
    except Exception as exc:
        # Milvus 不可用不阻塞启动：上传/问答会返回明确错误，便于先联调前端
        log.warning("Milvus 初始化失败，向量检索暂不可用: %s", exc)
    log.info(
        "ai-service 启动完成: model=%s, collection=%s, milvus=%s, langsmith=%s",
        settings.zhipu_model,
        settings.milvus_collection,
        settings.milvus_uri,
        settings.tracing_enabled,
    )
    yield


app = FastAPI(
    title="保险咨询机器人",
    version="0.1.0",
    description="保险 PDF 知识库 + LangGraph/GLM-5.3 RAG 流式问答服务",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:7100",
        "http://localhost:7200",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(chat.router)
app.include_router(sessions.router)
app.include_router(documents.router)


@app.get("/health", summary="健康检查")
def health() -> dict:
    return {
        "status": "ok",
        "service": settings.app_name,
        "model": settings.zhipu_model,
        "milvus": settings.milvus_uri,
        "collection": settings.milvus_collection,
    }