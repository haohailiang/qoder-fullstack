# ai-service · 保险咨询机器人（RAG Agent）

Python + FastAPI 微服务（默认端口 `8084`），为「保险商品」提供 **PDF 知识库构建 + 严格限定范围的流式问答 + 会话/检查点回溯**。

- 技术栈：LangChain + LangGraph（`InMemorySaver` 检查点）+ 智谱 GLM（OpenAI 兼容）+ MinerU PDF 解析 + 阿里云百炼 Embedding + Milvus 向量库
- 浏览器只访问网关 `/api/ai/**`，由网关校验 JWT 并注入 `X-User-Id / X-User-Name / X-User-Role`（可附 HMAC 签名），本服务不解析 JWT
- 回答范围严格限定在**该商品已上传的保险 PDF**：检索无命中或相似度低于阈值时直接返回固定拒答话术，**不调用大模型**

## 一、目录结构

```
ai-service/
  requirements.txt          # 运行时依赖
  run.ps1                   # 本地启动（uvicorn app.main:app --port 8084）
  app/
    config.py               # pydantic-settings，读取仓库根 .env
    main.py                 # FastAPI 入口：建表 + 初始化 Milvus 集合 + 路由
    deps.py                 # 身份依赖（X-User-*，可选 HMAC 验签）+ 管理员校验
    db.py / models.py       # SQLAlchemy：ai_insurance_document / ai_chat_session / ai_chat_message
    api/
      chat.py               # POST /chat（SSE 流式）
      sessions.py           # 会话 CRUD + 检查点列表/回溯
      documents.py          # 上传/列表/重建/删除（写操作仅 ADMIN）
    rag/
      mineru.py             # MinerU v4 客户端 + pypdf 降级
      ingest.py             # 解析 → 切分 → Embedding → 写 Milvus（后台任务）
      embeddings.py         # 百炼 text-embedding-v4（dim=1024）
      vector_store.py       # Milvus 集合 ensure/upsert/search/delete
      graph.py              # LangGraph：retrieve → generate | refuse
      prompts.py            # 严格系统提示词 + 拒答话术
  scripts/make_sample_pdfs.py  # 生成验收用示例条款 PDF（fpdf2，一次性）
  samples/                  # 已生成的示例保险条款 PDF
  tests/                    # pytest
```

## 二、环境变量（写入仓库根目录 `.env`，已被 .gitignore 忽略）

仓库只保留 `.env.example`（仅变量名与说明），**不要把真实密钥提交到 Git**。

| 变量 | 说明 |
| --- | --- |
| `ZHIPU_API_KEY` / `ZHIPU_BASE_URL` / `ZHIPU_MODEL` | 智谱 GLM，默认 `glm-5.3`，base_url `https://open.bigmodel.cn/api/paas/v4` |
| `MINERU_API_TOKEN` / `MINERU_BASE_URL` / `MINERU_MODEL_VERSION` | MinerU 精准解析；`MINERU_FALLBACK_PYPDF=true` 时失败自动降级 pypdf |
| `DASHSCOPE_API_KEY` / `DASHSCOPE_BASE_URL` / `EMBEDDING_MODEL` / `EMBEDDING_DIM` | 百炼 Embedding，默认 `text-embedding-v4`、1024 维 |
| `MILVUS_URI` / `MILVUS_TOKEN` / `MILVUS_COLLECTION` | Milvus 地址与集合名（默认 `insurance_chunks`） |
| `AI_MYSQL_URL` | 业务库（默认 `qoder_fullstack`），测试可指向 sqlite |
| `CHUNK_SIZE` / `CHUNK_OVERLAP` / `RAG_TOP_K` / `RAG_MIN_SCORE` / `REFUSAL_MESSAGE` | 切分与检索门控参数 |
| `LANGSMITH_TRACING` / `LANGCHAIN_ENDPOINT` / `LANGCHAIN_API_KEY` / `LANGCHAIN_PROJECT` | 填了 API Key 且 `LANGSMITH_TRACING=true` 才开启 tracing |
| `GATEWAY_JWT_SECRET` / `VERIFY_GATEWAY_SIGNATURE` | 开启后校验网关 HMAC 签名（`userId\|username\|role\|timestamp`） |

配置加载顺序：进程环境变量 > `ai-service/.env` > 仓库根 `.env`。

## 三、Milvus 部署（VM 192.168.178.100）

1. 在仓库根执行 `scripts/deploy-milvus.ps1`（需要 `$env:VM_PASS` 与仓库内 `vm-ssh.js`）：
   - 上传 `docker-compose.milvus.yml` 到 VM，执行 `docker compose -f ... up -d`，随后探测 `19530`
   - 无 SSH 口令时脚本会打印手工步骤：把 compose 上传到 VM 后执行
     `docker compose -f docker-compose.milvus.yml up -d`
2. 校验端口：`Test-NetConnection 192.168.178.100 -Port 19530`
3. 本地 `.env` 设置 `MILVUS_URI=http://192.168.178.100:19530`

`docker-compose.milvus.yml` 含 etcd + minio + `milvusdb/milvus:v2.4.15`，暴露 `19530`（gRPC）与 `9091`（健康检查）。

## 四、启动

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8084
# 或
.\run.ps1
```

启动时会：

1. 注入 LangSmith 环境变量（有 Key 才开启）
2. `Base.metadata.create_all` 建 `ai_*` 表（等价 SQL 见 `init-sql/03-insurance-agent.sql`）
3. 连接 Milvus 并 `ensure_collection`（失败仅告警，服务仍可启动，便于先联调前端）

网关侧需 `backend/ecommerce-gateway` 增加 `/api/ai/**` → `http://localhost:8084` 路由（已配置），改完重启网关。

**Python 版本**：推荐 3.11/3.12。若 3.13 下 `pymilvus`/`langgraph` 安装或运行异常，用 3.11 建 venv。

## 五、接口一览（均经网关 `/api/ai` 前缀）

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/chat` | 登录 | SSE 流式问答，body `{sessionId, productId, message}` |
| POST | `/sessions` | 登录 | 新建会话（`productId`） |
| GET | `/sessions?productId=` | 登录 | 当前用户在该商品下的会话 |
| GET | `/sessions/{id}/messages` | 属主 | 历史消息 |
| DELETE | `/sessions/{id}` | 属主 | 删除会话与消息 |
| GET | `/sessions/{id}/checkpoints` | 属主 | 检查点列表（回溯入口） |
| POST | `/sessions/{id}/restore` | 属主 | 回溯到指定 `checkpointId` |
| POST | `/documents/upload` | ADMIN | multipart：`productId` + `file`（PDF ≤20MB） |
| GET | `/documents?productId=` | 登录 | 文档列表与解析状态 |
| POST | `/documents/{id}/reindex` | ADMIN | 重新解析入库 |
| DELETE | `/documents/{id}` | ADMIN | 删除文档、向量与文件 |

SSE 事件：`meta`（sessionId）→ `token`（文本增量）→ `done`（`refused` + `sources`）/ `error`。

## 六、RAG 与拒答策略

1. **入库**：MinerU v4（`/file-urls/batch` → 预签名 PUT → 轮询 `/extract-results/batch` → 下载 `full_zip_url` 取 `full.md`）→ `clean_markdown` → `RecursiveCharacterTextSplitter(chunk_size=800, chunk_overlap=120, separators 含中文标点)` → 百炼 Embedding → Milvus `insurance_chunks`（COSINE + AUTOINDEX，按 `product_id` 过滤检索）
2. **门控**：`retrieve` 无命中或最高相似度 `< RAG_MIN_SCORE(0.35)` → 直接 `refuse` 节点输出固定话术，**不调用 LLM**（可复现、不编造）
3. **生成**：`generate` 用 `glm-5.3` 流式回答，系统提示词约束「只用文档片段、标注来源、片段不足必须原样输出拒答话术、禁止使用自身知识/推测」
4. **多轮与回溯**：`InMemorySaver` 检查点，`thread_id = u{userId}:p{productId}:s{sessionId}`
   - ⚠️ `InMemorySaver` 保存在进程内存，**服务重启后检查点丢失**，此时 `POST /sessions/{id}/restore` 返回 409；已落库的消息仍可在「对话」页查看
   - 之后每轮对话若使用过回溯，会在落库时清空 `last_checkpoint_id`，避免下次继续从旧状态分叉

## 七、测试

```powershell
cd ai-service
.\.venv\Scripts\python.exe -m pip install pytest pytest-asyncio
.\.venv\Scripts\python.exe -m pytest tests -q
```

覆盖内容：切分参数与中文分隔符、检索空/低分走拒答且不调用 LLM、上传非 PDF/超限/非管理员被拒、会话跨用户 404、SSE 帧格式与落库、检查点回溯 409/成功。

前端对应测试：

```powershell
cd frontend/product-app
npx vitest run
```

## 八、联调验收清单

1. 启动 Milvus（VM）、`ai-service`（8084）、网关（8080）与前端；确认 `GET http://localhost:8084/health` 返回 `{"status":"ok"}`
2. 管理员登录 → 商品列表筛选「保险」→ 进入保险商品详情页 → 点击「保险咨询机器人」
3. 抽屉「知识库」页签上传 `samples/安心百万医疗险条款.pdf` → 状态由「解析中」变为「已就绪」，片段数 > 0
4. 就 PDF 内容提问（如「等待期多久？」）→ 回答与条款一致、逐字流式、可展开「参考片段」看到来源文件与相似度
5. 问「今天天气如何？」→ 固定拒答话术「抱歉，我只能回答与该保险文档相关的问题。」
6. 普通用户 A/B 各自会话互不可见；「回溯」页签可恢复到历史检查点
7. `git status` 无 `.env`，`git check-ignore .env` 有输出（确认密钥未入库）

## 九、常见问题

| 现象 | 处理 |
| --- | --- |
| 上传后状态 `failed` | 展开失败行查看 `error_message`；常见为 MinerU Token 额度/网络、PDF 为扫描件且未 OCR |
| 检索一直拒答 | 确认文档为「已就绪」、`MILVUS_URI` 可达、`RAG_MIN_SCORE` 是否过高（可降到 0.25） |
| `glm-5.3` 报模型未开通 | 改 `.env` 的 `ZHIPU_MODEL`（如 `glm-5.3-flash`）后重启 |
| 回溯返回 409 | `InMemorySaver` 重启即失效，属预期行为 |
| 网关 413/内存不足 | 确认网关 `spring.codec.max-in-memory-size: 16MB` 且已重启 |
| 经网关访问 `/api/ai/**` 返回 500（响应体是 Spring 的 `timestamp/path/status`） | 8084 进程未监听：网关对下游连接拒绝默认返回 500 而非 503；先用 `GET http://localhost:8084/health` 自查再查网关 |
| 列表接口 422 | 查询参数约定为 camelCase（`productId`），后端已用 `Query(alias="productId")` 兼容，若仍 422 检查是否用了其他写法 |