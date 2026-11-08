---
kind: external_dependency
name: Redis 缓存与会话存储
slug: redis
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

### Redis
- 角色：多用途中间件——登录会话 Token、图片验证码、短信验证码、秒杀库存预扣、防重复购买标识等热点数据。
- 镜像版本：`redis:7-alpine`，数据目录挂载至 `/data`。
- 客户端约束：Docker 编排中通过 `SPRING_DATA_REDIS_HOST=redis` 注入；进程内内存实现（重启失效），如需多实例共享需替换为 Redis 方案。