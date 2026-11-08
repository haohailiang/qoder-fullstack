---
kind: configuration_system
name: Spring Cloud + Nacos 多环境配置体系（application.yml / bootstrap.yml / Docker 环境变量）
category: configuration_system
scope:
    - '**'
source_files:
    - backend/ecommerce-gateway/src/main/resources/application.yml
    - backend/ecommerce-gateway/src/main/resources/bootstrap.yml
    - backend/ecommerce-user/src/main/resources/application.yml
    - backend/ecommerce-user/src/main/resources/bootstrap.yml
    - backend/ecommerce-user/src/main/java/com/ecommerce/user/config/properties/CaptchaProperties.java
    - backend/ecommerce-user/src/main/java/com/ecommerce/user/config/properties/SmsProperties.java
    - backend/ecommerce-product/src/main/resources/application.yml
    - backend/ecommerce-order/src/main/resources/application.yml
    - docker-compose.yml
    - frontend/base-app/vite.config.ts
    - frontend/product-app/vite.config.ts
---

## 1. 使用的系统与框架

后端采用 Spring Boot + Spring Cloud Alibaba，统一通过 `application.yml` 加载应用级配置，并通过 `bootstrap.yml` 接入 Nacos Config（当前本地开发默认关闭）。服务发现走 Nacos Discovery；分布式配置中心在仓库中保留入口但 `enabled=false`，实际运行时以本地 YAML 为主、Docker 环境变量覆盖为辅。

前端使用 Vite 构建，通过 `vite.config.ts` 中的 `server.proxy` 将 `/api` 请求代理到网关 `http://localhost:8080`，不依赖 `.env` 文件。

## 2. 关键文件与位置

- 后端各服务配置：
  - `backend/ecommerce-gateway/src/main/resources/application.yml` — 网关路由、CORS、JWT secret、Swagger 聚合
  - `backend/ecommerce-user/src/main/resources/application.yml` — MySQL/Redis/JWT/captcha/sms/Swagger
  - `backend/ecommerce-product/src/main/resources/application.yml` — MySQL/Redis/Elasticsearch/RabbitMQ/JWT
  - `backend/ecommerce-order/src/main/resources/application.yml` — MySQL/Redis/RabbitMQ/OpenFeign/JWT
  - 各服务的 `bootstrap.yml` — Nacos Config 入口（`enabled: false`）
- 类型化配置类：
  - `backend/ecommerce-user/src/main/java/com/ecommerce/user/config/properties/CaptchaProperties.java`
  - `backend/ecommerce-user/src/main/java/com/ecommerce/user/config/properties/SmsProperties.java`
- 容器编排与环境变量覆盖：
  - `docker-compose.yml` — 基础设施 + 四个微服务 + 三个前端容器的统一编排，通过 `environment:` 注入 `SPRING_*` 环境变量
  - 各服务独立 `Dockerfile.*`（gateway/order/product/user）
- 前端构建配置：
  - `frontend/base-app/vite.config.ts`、`frontend/product-app/vite.config.ts`、`frontend/order-app/vite.config.ts`

## 3. 架构与约定

### 3.1 配置文件分层

| 层级 | 来源 | 用途 | 优先级 |
|---|---|---|---|
| 默认值 | `application.yml` | 本地开发直连 `192.168.178.100` 的 MySQL/Redis/Nacos/RabbitMQ/ES | 最低 |
| 远程配置 | Nacos Config (`bootstrap.yml`) | 集中式配置，当前 `enabled=false`，仅留入口 | 中间 |
| 运行期覆盖 | Docker Compose `environment` → Spring `SPRING_*` 环境变量 | 容器内替换 host/port/密码等 | 最高 |

Nacos Config 的 `bootstrap.yml` 统一设置 `spring.cloud.nacos.config.enabled=false`，说明项目处于“本地优先”模式；生产或 CI 可改为 `true` 并指向远端 Nacos。

### 3.2 配置项组织约定

- **Spring 内置属性**：数据库、Redis、RabbitMQ、Elasticsearch、OpenFeign、MyBatis、Springdoc 等直接写在 `application.yml` 对应命名空间下。
- **业务自定义属性**：放在根级命名空间，如 `jwt.secret`、`captcha.*`、`sms.*`，并通过 `@ConfigurationProperties(prefix = "...")` + `@Component` 绑定为强类型 POJO（见 `CaptchaProperties`、`SmsProperties`）。
- **敏感信息**：JWT secret、短信 apiKey/accountSid 在 YAML 中以占位符形式存在，注释明确提示“不要提交真实密钥”，生产应通过环境变量或外部 Vault 注入。
- **开关型配置**：`captcha.enabled`、`sms.enabled` 等布尔开关用于本地调试时禁用验证码/短信功能，避免误发短信。

### 3.3 端口与上下文路径约定

每个服务在 `application.yml` 中固定端口和 `servlet.context-path`：
- gateway: `8080`，无前缀
- user: `8081`，`/user`
- product: `8082`，`/product`
- order: `8083`，`/order`

网关通过 `StripPrefix=1` 去掉 `/api` 前缀后转发到子服务，形成 `/api/{service}/**` 的统一入口约定。

### 3.4 前端配置约定

- 所有前端子应用通过 `vite.config.ts` 的 `server.proxy['/api']` 将 API 请求代理到 `http://localhost:8080`（网关），无需在前端代码中硬编码后端地址。
- qiankun 子应用（product-app、order-app）额外启用 `qiankun('xxx', { useDevMode: true })`，并在 dev server 上开启 CORS。

### 3.5 Docker 环境覆盖规则

`docker-compose.yml` 对每个微服务注入一组 `SPRING_*` 环境变量，例如：
- `SPRING_PROFILES_ACTIVE=docker`
- `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATA_REDIS_HOST=redis`
- `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR=nacos:8848`
- `SPRING_ELASTICSEARCH_URIS`、`SPRING_RABBITMQ_HOST`（按服务需要）

这些变量会覆盖 `application.yml` 中的对应值，使同一份 YAML 可在本地直连 IP 与容器网络两种环境下复用。

## 4. 观察到的约束与规则

- **Nacos Config 默认关闭**：所有服务的 `bootstrap.yml` 均设置 `spring.cloud.nacos.config.enabled=false`，本地启动不会尝试连接 Nacos Config，除非显式修改该值。（依据：各服务 `bootstrap.yml`）
- **JWT Secret 跨服务一致**：gateway、user、product、order 的 `application.yml` 中 `jwt.secret` 值完全相同，网关与下游服务共享同一签名密钥。（依据：四份 `application.yml` 对比）
- **敏感字段禁止入库**：`SmsProperties` 与 `application.yml` 中 sms 配置的注释明确要求 apiKey/accountSid 不得提交到仓库，仅保留占位符。（依据：`SmsProperties.java` 与 `application.yml` 注释）
- **验证码/短信默认可关闭**：`captcha.enabled=true`、`sms.enabled=false`，且 `SmsProperties` 默认 `enabled=false`，避免演示环境误发短信。（依据：`application.yml` 与 `SmsProperties.java` 默认值）
- **MyBatis XML 映射位置统一**：所有服务 `mybatis.mapper-locations` 均为 `classpath:mapper/*.xml`，实体包按服务分包。（依据：各服务 `application.yml`）
- **前端 API 统一走网关**：base-app 与 product-app 的 `vite.config.ts` 都将 `/api` 代理到 `http://localhost:8080`，子应用不直接访问微服务端口。（依据：两个 `vite.config.ts`）
- **容器内服务名解析**：`docker-compose.yml` 中各服务的 `depends_on` 与 `environment` 使用 compose 内部 DNS 名（`mysql`、`redis`、`nacos`、`rabbitmq`、`elasticsearch`），而非 `192.168.178.100`，实现容器间通信与本地直连两套环境的解耦。（依据：`docker-compose.yml`）

## 5. 未发现的机制

- 未发现 `.env` 文件或 Spring `@ValueSource` / `PropertySource` 注解用于加载额外配置文件。
- 未发现基于 profile（如 `application-dev.yml`、`application-prod.yml`）的多环境文件，环境切换依赖 `SPRING_PROFILES_ACTIVE` 环境变量配合 `docker-compose.yml` 的环境变量覆盖。
- 未发现统一的 feature flag 系统，开关均以 `*.enabled` 布尔配置项形式分散在各服务 YAML 中。