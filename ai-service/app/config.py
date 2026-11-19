"""服务配置：全部从环境变量 / 仓库根目录 .env 读取，源码中不保存任何真实密钥。"""

from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

AI_SERVICE_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = AI_SERVICE_ROOT.parent


class Settings(BaseSettings):
    """ai-service 运行时配置。

    加载顺序：仓库根 `.env` → `ai-service/.env`，后者优先；已存在的进程环境变量优先级最高。
    """

    model_config = SettingsConfigDict(
        env_file=(str(REPO_ROOT / ".env"), str(AI_SERVICE_ROOT / ".env")),
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    app_name: str = "insurance-agent"
    app_env: str = "dev"

    # ---------- 智谱 GLM（OpenAI 兼容协议） ----------
    zhipu_api_key: str = ""
    zhipu_base_url: str = "https://open.bigmodel.cn/api/paas/v4"
    zhipu_model: str = "glm-5.3"
    zhipu_temperature: float = 0.2
    zhipu_max_tokens: int = 2048

    # ---------- MinerU PDF 解析 ----------
    mineru_api_token: str = ""
    mineru_base_url: str = "https://mineru.net/api/v4"
    mineru_model_version: str = "pipeline"
    mineru_fallback_pypdf: bool = True
    mineru_timeout_seconds: int = 600
    mineru_poll_interval_seconds: float = 5.0

    # ---------- 阿里云百炼 Embedding ----------
    dashscope_api_key: str = ""
    dashscope_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    embedding_model: str = "text-embedding-v4"
    embedding_dim: int = 1024

    # ---------- Milvus ----------
    milvus_uri: str = "http://192.168.178.100:19530"
    milvus_token: str = ""
    milvus_collection: str = "insurance_chunks"

    # ---------- 业务库（会话历史 / 文档元数据） ----------
    ai_mysql_url: str = (
        "mysql+pymysql://root:root@192.168.178.100:3306/qoder_fullstack?charset=utf8mb4"
    )

    # ---------- RAG 参数 ----------
    chunk_size: int = 800
    chunk_overlap: int = 120
    rag_top_k: int = 4
    rag_min_score: float = 0.35
    refusal_message: str = "抱歉，我只能回答与该保险文档相关的问题。"

    # ---------- 上传与安全 ----------
    data_dir: str = str(AI_SERVICE_ROOT / "data")
    max_pdf_mb: int = 20
    gateway_jwt_secret: str = ""
    verify_gateway_signature: bool = False
    gateway_max_skew_seconds: int = 300

    # ---------- LangSmith ----------
    langsmith_tracing: bool = False
    langchain_endpoint: str = "https://api.smith.langchain.com"
    langchain_api_key: str = ""
    langchain_project: str = "insurance-agent"

    @property
    def uploads_dir(self) -> Path:
        return Path(self.data_dir) / "uploads"

    @property
    def tracing_enabled(self) -> bool:
        return self.langsmith_tracing and bool(self.langchain_api_key)

    def apply_langsmith_env(self) -> None:
        """把 LangSmith 配置注入进程环境变量，供 langchain / langgraph 自动埋点。"""
        os.environ["LANGCHAIN_TRACING_V2"] = "true" if self.tracing_enabled else "false"
        os.environ["LANGSMITH_TRACING"] = "true" if self.tracing_enabled else "false"
        os.environ["LANGCHAIN_ENDPOINT"] = self.langchain_endpoint
        os.environ["LANGSMITH_ENDPOINT"] = self.langchain_endpoint
        os.environ["LANGCHAIN_PROJECT"] = self.langchain_project
        os.environ["LANGSMITH_PROJECT"] = self.langchain_project
        if self.langchain_api_key:
            os.environ["LANGCHAIN_API_KEY"] = self.langchain_api_key
            os.environ["LANGSMITH_API_KEY"] = self.langchain_api_key

    def require_llm_config(self) -> None:
        if not self.zhipu_api_key:
            raise RuntimeError("缺少 ZHIPU_API_KEY，请写入仓库根目录 .env 后再启动 ai-service")


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()