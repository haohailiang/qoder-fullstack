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