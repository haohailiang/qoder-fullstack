---
kind: external_dependency
name: 阿里云 OSS 对象存储（商品图片上传）
slug: aliyun-oss
category: external_dependency
category_hints:
    - vendor_identity
    - client_constraint
scope:
    - '**'
---

### 阿里云 OSS
- 角色：商品图片的托管存储；上传由商品服务中转（后端代理模式），前端只拿对象 Key 与可访问 URL，不做直传。
- 集成点：`backend/ecommerce-product`；`aliyun-sdk-oss` + `OssProperties`（前缀 `oss`），凭据 `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` / `OSS_BUCKET` 齐备才走 OSS，否则由 `FileStorageServiceRouter` 自动回退本地磁盘。
- 默认参数：`endpoint=oss-cn-hangzhou.aliyuncs.com`，`dir=product`（Key 形如 `product/2026/11/18/uuid.png`），`maxSizeMb=5`，允许 `image/jpeg|png|webp|gif`；`publicUrlPrefix` 留空时按 `https://{bucket}.{endpoint}` 拼接，可替换为 CDN 域名。
- 校验与安全：`FileController`（`/files/images`）仅 ADMIN 可调用；服务端除 MIME 外还校验前 12 字节魔数，删除时只允许操作商品图片目录内的对象。Nginx `client_max_body_size 6m` 与后端 5MB 限制需同步。
- 本地兜底：`OSS_LOCAL_DIR=uploads`，对外前缀 `OSS_LOCAL_URL_PREFIX=/api/product/files/local`，该前缀在网关白名单中（图片需免鉴权直接展示）；容器部署时该目录必须挂载卷，否则重启即丢图。
- 常见故障：`OSS 未配置` 业务错误（AK/SK 或 bucket 为空）、413（Nginx 与 Spring multipart 限制不一致）、图片 404（回退目录未持久化或未配置 public-url-prefix）。