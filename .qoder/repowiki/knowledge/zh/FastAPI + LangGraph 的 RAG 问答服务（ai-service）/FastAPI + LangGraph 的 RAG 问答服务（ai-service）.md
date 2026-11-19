---
kind: external_dependency
name: FastAPI + LangGraph 的 RAG 问答服务（ai-service）
slug: ai-service
category: external_dependency
category_hints:
    - framework_behavior
scope:
    - '**'
---

### ai-service（insurance-agent）
- 角色：保险咨询机器人后端，FastAPI + LangGraph 承载「PDF 入库 → 向量检索 → GLM 流式回答」，并利用 LangGraph 检查点提供会话回溯（time travel）。
- 端口与路由：监听 `8084`；网关路由为固定 `uri: http://localhost:8084` + `Path=/api/ai/**` + `StripPrefix=2`，因此 Python 侧路径是 `/chat`、`/sessions/**`、`/documents/**`、`/health`（不带 `/ai` 前缀），且不注册 Nacos。
- 鉴权：`app/deps.py` 只信任网关注入的 `X-User-Id` / `X-User-Name` / `X-User-Role`；可选开启 `VERIFY_GATEWAY_SIGNATURE` 复算 `X-Gateway-Signature`（HMAC-SHA256，载荷 `userId|username|role|timestamp`，密钥 `GATEWAY_JWT_SECRET`，时钟偏差 `GATEWAY_MAX_SKEW_SECONDS=300`）；管理员操作走 `admin_only` 依赖。
- 数据：与电商同库但只操作 `ai_*` 表（`ai_insurance_document`、`ai_chat_session`、`ai_chat_message`），由 SQLAlchemy `create_all` 幂等建表；测试用 `AI_MYSQL_URL=sqlite:///...` 切换，因此模型主键用 `BigInteger().with_variant(Integer, "sqlite")`。
- RAG 参数：`CHUNK_SIZE=800` / `CHUNK_OVERLAP=120` / `RAG_TOP_K=4` / `RAG_MIN_SCORE=0.35`；召回不足时返回 `REFUSAL_MESSAGE`（默认「抱歉，我只能回答与该保险文档相关的问题。」）而不是让模型自由发挥。
- 关键差异：错误体是 FastAPI 的 `{"detail": ...}`，与 Java 侧 `{code,message,data}` 不同；问答走 SSE 流式返回；查询参数支持 camelCase（`productId`）以对齐前端约定。
- 配置来源：仓库根 `.env` → `ai-service/.env`（后者优先），进程环境变量最高；缺 `ZHIPU_API_KEY` 启动即报错，`DASHSCOPE_API_KEY` 用于 Embedding，`MINERU_API_TOKEN` 可选（失败回退 pypdf）。