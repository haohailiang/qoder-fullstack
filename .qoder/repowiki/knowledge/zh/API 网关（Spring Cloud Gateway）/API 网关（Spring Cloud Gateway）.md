---
kind: external_dependency
name: API 网关（Spring Cloud Gateway）
slug: spring-cloud-gateway
category: external_dependency
category_hints:
    - framework_behavior
scope:
    - '**'
---

### Spring Cloud Gateway
- 角色：统一 API 入口，JWT 全局鉴权、按路径前缀路由到下游微服务，并聚合各服务的 Swagger 文档。
- 集成点：`backend/ecommerce-gateway`；`AuthGlobalFilter` 从 `Authorization: Bearer <token>` 提取 JWT，校验后写入 `X-User-Id` / `X-User-Name` 透传给下游；Swagger 路径 `/api/*/swagger-ui/**` 与 `/api/*/v3/api-docs/**` 加入白名单放行。
- 稳定用法：下游业务服务通过 `TrustedAuthenticationFilter` 信任网关注入的请求头，不再重复校验 JWT。
- 注意：网关基于 WebFlux，接入 springdoc 需使用 `springdoc-openapi-starter-webflux-ui`，与业务服务的 `webmvc-ui` 不同。
- 单设备会话：`AuthGlobalFilter`（order = -100）在 JWT 解析后额外 `GET login:token:{userId}` 与请求 Token 比对，不一致或 Key 不存在时返回 HTTP 401 + 业务码 `40101`；Redis 不可用时降级放行，避免整站不可用。开关为 `gateway.session-check.enabled`。
- 信任头防伪造：转发前先 `stripTrustedHeaders` 洗掉客户端自带的 `X-User-*` 头，再注入 `X-User-Id` / `X-User-Name` / `X-User-Role` 与 `X-Gateway-Timestamp` / `X-Gateway-Signature`（HMAC，载荷 `userId|username|role|timestamp`，密钥复用 `jwt.secret`）；下游是否验签由 `security.trusted-header.verify-signature` 控制，默认关闭，开启后允许时钟偏差 300s。
- 异构服务路由：`id: ai-service` 的 `uri` 写死为 `http://localhost:8084`（非 `lb://`，不参与 Nacos 负载均衡），`Path=/api/ai/**` + `StripPrefix=2`，故 Python 侧路由不带 `/ai` 前缀。
- 白名单：除登录/注册/验证码/Swagger/健康检查外，还需放行支付宝异步通知 `POST /api/order/payments/alipay/notify`（由支付宝服务器直接调用，靠验签保安全）。