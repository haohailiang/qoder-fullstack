---
kind: external_dependency
name: Nacos 服务注册与配置中心
slug: nacos
category: external_dependency
category_hints:
    - vendor_identity
    - client_constraint
scope:
    - '**'
---

### Nacos
- 角色：Spring Cloud Alibaba 的服务发现与配置中心，所有后端微服务启动时通过 `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` 连接。
- 镜像版本：`nacos/nacos-server:v2.3.0`，容器名 `ecommerce-nacos`，端口 8848/9848/9849，控制台默认账号 nacos/nacos。
- 客户端约束：Docker 编排中通过环境变量注入 `server-addr`，本地开发需在 `bootstrap.yml` / `application.yml` 指向本机或宿主机地址。