---
kind: error_handling
name: 后端统一异常处理体系（BusinessException + GlobalExceptionHandler + Result）
category: error_handling
scope:
    - '**'
source_files:
    - backend/ecommerce-common/src/main/java/com/ecommerce/common/BusinessException.java
    - backend/ecommerce-common/src/main/java/com/ecommerce/common/GlobalExceptionHandler.java
    - backend/ecommerce-common/src/main/java/com/ecommerce/common/Result.java
    - backend/ecommerce-gateway/src/main/java/com/ecommerce/gateway/filter/AuthGlobalFilter.java
    - backend/ecommerce-order/src/main/java/com/ecommerce/order/OrderApplication.java
    - backend/ecommerce-product/src/main/java/com/ecommerce/product/ProductApplication.java
    - backend/ecommerce-user/src/main/java/com/ecommerce/user/UserApplication.java
    - backend/ecommerce-common/src/main/java/com/ecommerce/common/config/SecurityConfig.java
    - backend/ecommerce-user/src/test/java/com/ecommerce/user/common/GlobalExceptionHandlerTest.java
---

## 1. 采用的方案

后端基于 Spring Boot 的 `@RestControllerAdvice` + 自定义业务异常类，实现统一的异常捕获与统一响应体。前端侧没有独立的错误框架，仅在 API 请求层通过 HTTP 状态码与 `Result.code` 判断成功/失败。

## 2. 关键文件与包

- `backend/ecommerce-common/src/main/java/com/ecommerce/common/BusinessException.java` — 业务异常类型
- `backend/ecommerce-common/src/main/java/com/ecommerce/common/GlobalExceptionHandler.java` — 全局异常处理器
- `backend/ecommerce-common/src/main/java/com/ecommerce/common/Result.java` — 统一响应结构
- `backend/ecommerce-gateway/src/main/java/com/ecommerce/gateway/filter/AuthGlobalFilter.java` — 网关层的鉴权错误处理
- 各微服务 `*Application.java`：通过 `@Import(GlobalExceptionHandler.class)` 引入全局处理器
- 各 Controller / Service 中大量 `throw new BusinessException(code, message)` 调用点

## 3. 架构与约定

### 3.1 统一响应体 Result<T>

所有接口返回 `Result<T>`，包含三个字段：`code`（Integer）、`message`（String）、`data`（T）。成功路径使用 `Result.success(data)`，失败路径使用 `Result.error(code, message)`。HTTP 状态码由 Spring MVC 默认行为决定（正常返回 200），业务错误码集中在 `code` 字段。

### 3.2 业务异常 BusinessException

继承 `RuntimeException`，构造时携带业务错误码（如 400、401、403、404、409、429、500、502 等）和消息。默认单参构造器固定 code=400。业务层在参数校验失败、资源不存在、权限不足、库存不足等场景直接抛出该异常，不再在 Controller 中 try/catch。

### 3.3 全局异常处理器 GlobalExceptionHandler

以 `@RestControllerAdvice` 注册，集中处理以下异常类型：

- `BusinessException`：log.warn + 透传 code/message
- `MethodArgumentNotValidException`：取第一个字段校验错误信息，返回 400
- `AuthenticationException`：认证失败，返回 401 "用户名或密码错误"
- `AccessDeniedException`：授权拒绝，返回 403 "未授权访问：权限不足"
- `ExpiredJwtException`：JWT 过期，返回 401 "Token 已过期，请重新登录"
- `JwtException`：JWT 无效，返回 401 "Token 无效"
- `Exception`（兜底）：log.error 打印堆栈，返回 500 "服务器内部错误: ..."

### 3.4 跨服务复用方式

每个微服务的启动类通过 `@Import(GlobalExceptionHandler.class)` 显式引入全局处理器，而非依赖组件扫描自动发现。涉及的文件：

- `ecommerce-order/OrderApplication.java`
- `ecommerce-product/ProductApplication.java`
- `ecommerce-user/UserApplication.java`

### 3.5 网关层独立处理

`AuthGlobalFilter` 在网关层拦截请求，对 Token 缺失、不完整、过期、无效等情况直接返回 `Result.error(401, ...)`，不走下游业务异常。这是网关与业务服务之间的边界：网关负责协议/鉴权错误，业务服务负责领域错误。

### 3.6 Security 配置中的错误响应

`ecommerce-common/config/SecurityConfig.java` 在 `authorizeRequests` 中为所有业务服务统一配置了 `accessDeniedHandler` 与 `authenticationEntryPoint`，分别返回 `Result.error(403, "access denied")` 与 `Result.error(401, "unauthorized")`，绕过 Spring Security 默认的 HTML 错误页。

## 4. 观察到的约定与约束

- 业务异常必须带业务码：所有 `BusinessException` 调用均传入具体数字码（400/401/403/404/409/429/500/502），不依赖默认 400。
- Controller 层不抛异常也不 try/catch：Controller 仅调用 Service 并返回 `Result.success(...)`，异常由 `GlobalExceptionHandler` 统一收敛。
- Service 层是异常源头：参数校验、资源查找、权限判断、外部调用失败等均在 Service 层抛出 `BusinessException`。
- 日志级别区分：业务异常用 log.warn，系统异常用 log.error 并打印完整堆栈；鉴权相关异常也走 warn 级别。
- Gateway 与业务服务解耦：网关只处理 Token 相关的 401 错误，业务错误由下游服务自行处理。
- Spring Security 异常被覆盖：通过 `authenticationEntryPoint` 与 `accessDeniedHandler` 将 Security 异常转换为 `Result` JSON，避免返回 HTML 错误页面。
- 测试覆盖：`ecommerce-user` 模块包含 `GlobalExceptionHandlerTest`、`AuthControllerExceptionTest`、`SendSmsCodeControllerTest`、`SmsRecordsControllerTest` 等用例，验证异常路径能正确返回 `Result`。

## 5. 前端侧

前端 `base-app/src/api/request.ts` 封装了 axios 请求，但仓库中未发现统一的错误拦截器代码片段；从控制器返回的 `Result.code` 可推断前端按 `code !== 200` 作为失败分支处理。子应用（`product-app`、`order-app`）未观察到独立的错误处理框架。