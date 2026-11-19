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
- 单设备语义：`RedisSessionServiceImpl` 用 `SET login:token:{userId} <token> EX <ttl>` 直接覆盖旧值，因此只有最新一次登录的 Token 有效；登录并发锁 `login:lock:{userId}` 为 SETNX + 10s TTL，防止同一账号并发登录互相覆盖。
- 被动下线判定：网关是会话值的唯一读方，`GET login:token:{userId}` 与请求 Token 不一致即返回 401 + 业务码 `40101`；必须区分“Key 不存在”（`SESSION_ABSENT` 哨兵）与“Redis 异常”（`SESSION_UNAVAILABLE` 哨兵，降级放行），否则一次 Redis 抖动会把全站用户踢下线。