---
kind: external_dependency
name: RabbitMQ 消息队列
slug: rabbitmq
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

### RabbitMQ
- 角色：秒杀下单异步解耦——公共模块 `ecommerce-common` 的 `SeckillRabbitConfig` 统一定义交换机与队列，商品服务发送 `SeckillMessage`，订单服务 `SeckillOrderConsumer` 消费创建订单。
- 镜像版本：`rabbitmq:3-management-alpine`，管理界面 guest/guest，端口 5672/15672。
- 客户端约束：Docker 编排中通过 `SPRING_RABBITMQ_HOST` 注入 host；本地开发需确保 RabbitMQ 可访问。