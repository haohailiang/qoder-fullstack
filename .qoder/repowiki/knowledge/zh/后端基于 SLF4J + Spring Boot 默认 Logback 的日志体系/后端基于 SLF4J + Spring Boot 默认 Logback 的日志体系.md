---
kind: logging_system
name: 后端基于 SLF4J + Spring Boot 默认 Logback 的日志体系
category: logging_system
scope:
    - '**'
source_files:
    - backend/pom.xml
    - backend/ecommerce-common/src/main/java/com/ecommerce/common/GlobalExceptionHandler.java
    - backend/ecommerce-order/src/main/resources/application.yml
    - backend/ecommerce-product/src/main/resources/application.yml
    - backend/ecommerce-gateway/src/main/java/com/ecommerce/gateway/filter/AuthGlobalFilter.java
    - backend/ecommerce-order/src/main/java/com/ecommerce/order/consumer/SeckillOrderConsumer.java
    - backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java
    - backend/ecommerce-product/src/main/java/com/ecommerce/product/service/impl/ProductServiceImpl.java
    - backend/ecommerce-product/src/main/java/com/ecommerce/product/controller/SeckillController.java
---

## 1. 使用的框架与依赖

后端采用 **SLF4J API**（`org.slf4j.Logger` / `LoggerFactory`）作为统一日志门面，由 Spring Boot 3.3.5 默认的 **Logback** 实现输出。父 POM (`backend/pom.xml`) 继承 `spring-boot-starter-parent`，未显式引入 logback 依赖——通过 Spring Boot Starter 自动装配。

前端各子应用（base-app、product-app、order-app）仅使用原生 `console.log` 进行调试输出，无统一的前端日志库或结构化日志方案。

## 2. 关键文件

- `backend/ecommerce-common/src/main/java/com/ecommerce/common/GlobalExceptionHandler.java`：全局异常处理，集中记录 warn/error 级别日志。
- `backend/ecommerce-order/src/main/resources/application.yml`：订单服务日志级别配置。
- `backend/ecommerce-product/src/main/resources/application.yml`：商品服务日志级别配置。
- 各模块 Controller/Service/Filter/Consumer 类中均按相同模式声明 `private static final Logger log = LoggerFactory.getLogger(...)`。

## 3. 架构与约定

### 3.1 日志门面与实例声明
所有 Java 类遵循同一写法：
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// ...
private static final Logger log = LoggerFactory.getLogger(当前类.class);
```
该模式在 GlobalExceptionHandler、AuthGlobalFilter、SeckillOrderConsumer、TrustedAuthenticationFilter（order/product）、OrderServiceImpl、ProductServiceImpl、SeckillController 等文件中一致出现。

### 3.2 日志级别策略
- 业务异常、认证失败、授权拒绝、Token 过期/无效等可预期错误 → `log.warn("...", e.getMessage())`。
- 兜底异常 → `log.error("系统异常", e)`，附带完整堆栈。
- 业务代码中的常规信息日志以 DEBUG 级别为主，各服务在 `application.yml` 中通过 `logging.level.com.ecommerce.<service>: DEBUG` 控制。

### 3.3 日志级别配置位置
每个微服务的 `src/main/resources/application.yml` 末尾追加独立的 `logging:` 块，例如：
- `ecommerce-order`: `logging.level.com.ecommerce.order: DEBUG`
- `ecommerce-product`: `logging.level.com.ecommerce.product: DEBUG`
未看到统一的根级别或自定义 pattern/layout 配置，全部依赖 Spring Boot 默认 Logback 配置。

### 3.4 结构化字段
未发现 MDC、JSON 格式、traceId/correlationId 等结构化日志字段；日志为纯文本，消息体多为中文描述 + 异常 message。

### 3.5 前端日志
前端三个子应用仅在 qiankun 生命周期钩子和 bootstrap/mount 处使用裸 `console.log('[qiankun] ...' | '[order-app] ...')`，无统一 logger 封装，也未接入任何远程日志收集。

## 4. 约定与约束

- **约定（观察到的实践）**：所有需要日志的类通过 `LoggerFactory.getLogger(类.class)` 获取静态 Logger 实例，而非构造时注入。
- **约定（观察到的实践）**：可预期的业务/安全异常用 `warn` 级别记录，不可预期的兜底异常用 `error` 级别并传入异常对象以打印堆栈。
- **约定（观察到的实践）**：日志级别通过各服务 `application.yml` 的 `logging.level.<包名>` 单独设置，未定义共享的 logging 配置文件。
- **约束（Spring Boot 默认行为）**：由于未在任意 `application.yml` 中覆盖 `logging.pattern`，输出格式由 Spring Boot 内置 Logback 配置决定（包含时间戳、线程、级别、logger 名称和消息）。
- **约束（依赖管理）**：父 POM 未显式声明 slf4j/logback 版本，版本由 `spring-boot-starter-parent` 的 BOM 统一管理。

## 5. 缺失项

仓库中没有以下能力：
- 无自定义 Logback XML 配置（如 `logback-spring.xml`）。
- 无 MDC/TraceId 链路追踪字段。
- 无 JSON 结构化日志输出。
- 无前端统一日志 SDK。
- 无日志分级（info/warn/error）的业务规范文档。