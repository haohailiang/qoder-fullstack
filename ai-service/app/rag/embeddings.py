"""阿里云百炼 Embedding（OpenAI 兼容协议）。

text-embedding-v4：单次批量上限 10 条、最长 8192 token，默认输出 1024 维。
"""

from __future__ import annotations

from functools import lru_cache

from langchain_openai import OpenAIEmbeddings

from ..config import get_settings


@lru_cache(maxsize=1)
def get_embeddings() -> OpenAIEmbeddings:
    settings = get_settings()
    if not settings.dashscope_api_key:
        raise RuntimeError("缺少 DASHSCOPE_API_KEY，无法调用阿里云百炼 Embedding")

    return OpenAIEmbeddings(
        model=settings.embedding_model,
        api_key=settings.dashscope_api_key,
        base_url=settings.dashscope_base_url,
        dimensions=settings.embedding_dim,
        # 百炼兼容接口只接受原始文本，不能用 OpenAI 的 tiktoken 预切分
        check_embedding_ctx_length=False,
        chunk_size=10,
    )