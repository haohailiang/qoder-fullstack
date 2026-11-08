---
kind: external_dependency
name: springdoc-openapi（Swagger UI）
slug: springdoc-openapi
category: external_dependency
category_hints:
    - sdk_real_api
scope:
    - '**'
---

### springdoc-openapi v2.6.0
- 角色：为所有后端微服务生成 OpenAPI 文档并提供 Swagger UI，网关聚合所有下游服务文档。
- 集成方式：网关使用 `springdoc-openapi-starter-webflux-ui`，业务服务使用 `springdoc-openapi-starter-webmvc-ui`；Controller 通过 `@Tag` / `@Operation` / `@Parameter` 注解标注接口。
- 稳定用法：网关 `application.yml` 中通过 `springdoc.swagger-ui.urls` 数组聚合下游 `/v3/api-docs`；Swagger 路径已加入鉴权白名单，无需 Token 即可访问。