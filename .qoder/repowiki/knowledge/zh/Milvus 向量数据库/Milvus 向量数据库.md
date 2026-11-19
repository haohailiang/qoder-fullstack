---
kind: external_dependency
name: Milvus 向量数据库
slug: milvus
category: external_dependency
category_hints:
    - vendor_identity
    - client_constraint
scope:
    - '**'
---

### Milvus
- 角色：保险咨询 RAG 的向量检索后端，存放保险条款 PDF 切分后的片段向量；由 `ai-service` 通过 pymilvus 读写，Java 侧不直接访问。
- 部署形态：单机版 `docker-compose.milvus.yml`（`milvusdb/milvus:v2.4.15` + `bitnamilegacy/etcd:3.5.5` + `bitnamilegacy/minio`），只向局域网暴露 gRPC `19530` 与指标 `9091`；数据目录 `/opt/milvus/{etcd,minio,data}`，默认部署在 VM `192.168.178.100`（`scripts/deploy-milvus.ps1`）。
- 客户端约束：`MILVUS_URI` 默认 `http://192.168.178.100:19530`，`MILVUS_TOKEN` 为空表示不鉴权；Collection 名 `insurance_chunks`，向量维度必须与 Embedding 模型一致（`text-embedding-v4` → 1024）。
- 可用性：ai-service 启动时 `ensure_collection` 失败只记录 warning、不阻塞进程，便于先联调前端；但此时上传文档与问答会明确报错。
- 运维脚本：`scripts/inspect-milvus.py`、`scripts/smoke-milvus.py`、`scripts/milvus-console.py` 用于连通性、Collection schema 与数据量核查。