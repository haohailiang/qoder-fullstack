# demo-fullstack

前后端分离的全栈示例项目，包含：

- **backend/**：Spring Boot 3.x + Java 17 + Maven + MyBatis + MySQL，提供用户资源 REST API、图片验证码、云片网短信发送服务
- **frontend/**：Vue 3 + TypeScript + Vite + Element Plus，提供登录 / 注册（含图片验证码）与用户管理（含手机号、发送短信验证码）页面

## 目录结构

```
demo-fullstack/
├── backend/                              # Spring Boot 后端
│   ├── pom.xml                           # Maven 配置（Spring Boot 3.3.x，Java 17，MyBatis，MySQL 驱动）
│   ├── src/main/
│   │   ├── java/com/example/demo/
│   │   │   ├── DemoApplication.java      # 启动入口
│   │   │   ├── common/                   # 统一响应 Result / 业务异常 / 全局异常处理
│   │   │   ├── config/                   # CORS / Spring Security / RestTemplate / 演示数据初始化配置
│   │   │   │   └── properties/            # 图片验证码 / 云片网短信配置项
│   │   │   ├── controller/               # 控制层（认证 / 验证码 / 用户 / 短信 / 健康检查）
│   │   │   ├── dto/                      # 请求 / 响应 DTO
│   │   │   ├── entity/                   # 实体类（含审计字段）
│   │   │   ├── repository/               # 仓储层（基于 MyBatis Mapper 访问 MySQL，统一填充审计字段）
│   │   │   │   └── mapper/               # MyBatis Mapper 接口（SQL 见 resources/mapper/*.xml）
│   │   │   ├── security/                 # JWT 工具与认证过滤器
│   │   │   └── service/                  # 服务层（认证 / 用户 / 图片验证码 / 短信）
│   │   └── resources/
│   │       ├── application.yml           # 端口 8081，上下文路径 /api，MySQL / MyBatis / 验证码 / 短信配置
│   │       ├── db/schema.sql             # MySQL 建表 SQL（users 表）
│   │       └── mapper/UserMapper.xml     # 用户表 SQL（全部写在 XML，不使用注解写 SQL）
│   └── src/test/                         # 异常处理 / 验证码 / 短信服务测试用例
│       └── java/com/example/demo/
│           ├── common/
│           │   └── GlobalExceptionHandlerTest.java   # 全局异常处理器单元测试
│           ├── controller/
│           │   ├── AuthControllerExceptionTest.java  # 注册/登录接口异常集成测试
│           │   └── SecurityExceptionTest.java        # 认证/JWT/授权安全链路测试
│           └── service/
│               ├── CaptchaServiceTest.java           # 图片验证码服务单元测试
│               └── SmsServiceTest.java               # 短信服务重试/频控单元测试
└── frontend/                             # Vue 3 前端
    ├── package.json
    ├── vite.config.ts                    # Vite 配置（含 /api 代理到后端）
    ├── tsconfig.json / tsconfig.node.json
    ├── index.html
    └── src/
        ├── main.ts                       # 入口（注册 Element Plus、路由）
        ├── App.vue                       # 根组件（布局）
        ├── api/                          # axios 封装 + 认证 / 用户 / 短信接口
        ├── router/                       # vue-router 路由
        ├── utils/                        # 令牌与登录用户状态管理
        └── views/
            ├── Login.vue                 # 登录页（含图片验证码）
            ├── Register.vue              # 注册页（含手机号、图片验证码）
            └── UserManage.vue            # 用户管理页面（增删改查 + 发送短信验证码）
```

## 环境要求

- JDK 17+
- Maven 3.6+
- Node.js 18+（推荐 20 LTS）
- MySQL 8.x（本项目用户数据持久化依赖 MySQL，注册/登录、用户管理、集成测试均需要真实数据库，见下方「数据库初始化」）

## 数据库初始化

后端启动或运行集成测试前，需要先创建数据库和表：

```sql
CREATE DATABASE IF NOT EXISTS demo_fullstack DEFAULT CHARSET utf8mb4;
USE demo_fullstack;
```

然后执行建表脚本 [`backend/src/main/resources/db/schema.sql`](backend/src/main/resources/db/schema.sql)（`users` 表包含 `id / username / password / name / email / phone / age` 业务字段，以及 `created_by / created_date / last_modified_by / last_modified_date` 四个审计字段，均为下划线命名，通过 `mapper/UserMapper.xml` 的 `resultMap` 映射为 Java 实体中的驼峰字段）。

建库建表完成后，还需要把 `application.yml` 中的 `spring.datasource.password` 从占位符 `your_mysql_password_here` 改成本机 MySQL 的真实密码（该文件仅供本地开发使用，不要把真实密码提交到仓库）。

应用首次启动时，`DemoDataInitializer`（`config/DemoDataInitializer.java`）会检查 `users` 表中是否存在预置演示账号，若不存在则自动插入两条演示数据（`zhangsan` / `lisi`，初始密码均为 `123456`，默认手机号为 `13800000001` / `13800000002`），无需手动执行 `INSERT` 语句；逻辑幂等，重复启动不会产生脏数据。审计字段在数据初始化、注册（无登录上下文）等场景下会自动记录为 `system`，登录后的用户新增 / 更新操作会记录当前登录用户名。

## 后端启动

```bash
cd backend
mvn spring-boot:run
```

- 服务地址：http://localhost:8081/api
- 用户接口：http://localhost:8081/api/users

> 端口说明：默认端口为 8081（本机 8080 被 Windows 系统进程占用）。如需更换端口，请同时修改 `backend/src/main/resources/application.yml` 的 `server.port` 与 `frontend/vite.config.ts` 的代理 `target`。

## 前端启动

```bash
cd frontend
npm install
npm run dev
```

- 访问地址：http://localhost:5173
- 开发代理：Vite 将 `/api` 前缀请求转发到 `http://localhost:8081`（后端 context-path 即为 `/api`，代理不做路径重写）

## API 一览

| 方法   | 路径                    | 说明                                       |
| ------ | ----------------------- | ------------------------------------------ |
| GET    | /api/auth/captcha       | 获取图片验证码（匿名访问）                 |
| POST   | /api/auth/register      | 注册（需携带验证码，需填写手机号）         |
| POST   | /api/auth/login         | 登录（需携带验证码，返回 JWT）             |
| GET    | /api/auth/me            | 获取当前登录用户                           |
| POST   | /api/auth/logout        | 退出登录                                   |
| GET    | /api/users              | 查询用户列表                               |
| GET    | /api/users/{id}         | 查询单个用户                               |
| POST   | /api/users              | 新增用户                                   |
| PUT    | /api/users/{id}         | 更新用户                                   |
| DELETE | /api/users/{id}         | 删除用户                                   |
| POST   | /api/sms/send-code      | 按用户 id 发送短信验证码（需登录访问）     |
| GET    | /api/health             | 健康检查                                   |

请求 / 响应体示例（新增用户）：

```json
{
  "name": "王五",
  "email": "wangwu@example.com",
  "phone": "13800000003",
  "age": 28
}
```

注册 / 登录请求体需额外携带图片验证码相关字段：

```json
{
  "username": "wangwu",
  "password": "123456",
  "confirmPassword": "123456",
  "name": "王五",
  "email": "wangwu@example.com",
  "phone": "13800000003",
  "age": 28,
  "captchaKey": "<GET /api/auth/captcha 返回的 captchaKey>",
  "captchaValue": "<用户输入的图片验证码字符>"
}
```

## 统一响应结构

所有接口统一返回如下结构，`code` 为 200 表示成功：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

## 图片验证码

- 前端登录 / 注册页面通过 `GET /api/auth/captcha` 获取验证码图片（Base64 PNG）与一次性 `captchaKey`，点击图片可刷新。
- 提交登录 / 注册时须携带 `captchaKey` 与用户输入的 `captchaValue`；后端在执行业务逻辑（查重、密码校验）之前先调用 `CaptchaService.validate`，校验失败（不存在 / 已过期 / 已被使用 / 不匹配）统一返回「验证码错误或已过期，请重新获取」，前端会自动刷新验证码图片并清空输入框，允许重新提交。
- 验证码内容用纯 JDK AWT 生成并保存在进程内存（不落库），有效期与一次性使用规则见 `application.yml` 的 `captcha.*` 配置（`expire-seconds`、`width`、`height`、`code-length`、`line-count`）；`captcha.enabled=false` 可临时关闭校验，便于本地调试。

## 短信发送与云片网封装

- 前端不直接对接云片网 API；`apiKey`、`accountSid` 等敏感配置只在后端 `SmsService` 内部读取，不会出现在任何接口响应 / 异常信息中。
- 前端用户管理页每行提供「发送验证码」按钮，点击调用 `POST /api/sms/send-code`（请求体为 `{ userId }`），后端根据 `userId` 查询用户手机号后调用 `SmsService` 发送验证码。
- 后端对同一手机号做发送频率控制（`sms.rate-limit-seconds` 默认 60 秒内不允许重复发送，超出会返回 `code=429`）；前端按钮发送成功后进入倒计时禁用状态，倒计时结束前不可重复点击。
- 调用云片网接口失败时（网络异常 / 超时 / 5xx 可恢复错误）会按 `sms.max-retry` 重试，重试间隔按 `sms.retry-backoff`（`linear` 线性 / `exponential` 指数）退避；4xx 客户端错误（如 `apiKey` 错误）不重试直接失败，统一返回 `code=502`「短信发送失败，请稍后重试」，不泄露敏感配置细节。
- `sms.enabled=false`（默认值）时不调用真实云片网接口，仅打印日志，用于本地演示 / 自动化测试；配置真实密钥后改为 `true` 才会实际发送短信。

云片网配置项（`application.yml` 的 `sms.*`）需在 [云片网控制台](https://www.yunpian.com/) 申请后填入本地 `application.yml`，仓库中保留的是占位符（`your_yunpian_account_sid` / `your_yunpian_api_key`），**不要提交真实密钥**：

```yaml
sms:
  enabled: false                     # 本地演示默认关闭，配置真实密钥后改为 true
  api-url: https://sms.yunpian.com/v2/sms/tpl_send.json
  account-sid: your_yunpian_account_sid   # 占位符，请替换为云片网控制台的真实 Account SID
  api-key: your_yunpian_api_key           # 占位符，请替换为云片网控制台的真实 API Key，不要提交到仓库
  template-id: "0"                        # 占位符，请替换为云片网控制台的真实模板 ID
  timeout-ms: 5000
  max-retry: 3
  retry-interval-ms: 1000
  retry-backoff: linear
  rate-limit-seconds: 60
```

## 异常处理与测试

后端通过 `common/GlobalExceptionHandler`（`@RestControllerAdvice`）将各类异常统一包装为前述 `Result` 结构返回。测试用例位于 `backend/src/test/`，基于 JUnit 5 + Spring Boot Test + MockMvc + Mockito（由已有的 `spring-boot-starter-test` 提供，无需额外依赖），共 39 个用例，覆盖以下场景：

| 异常 / 场景类型 | 触发场景 | 处理器 | `Result.code` | HTTP 状态 |
| --------------- | -------- | ------ | ------------- | --------- |
| 业务异常 `BusinessException` | 认证标识已存在（409）、确认密码不一致（400）、用户不存在（404）、短信发送频控（429）、短信发送失败（502）等 | `GlobalExceptionHandler` | 400 / 404 / 409 / 429 / 502 | 200 |
| 参数校验异常 `MethodArgumentNotValidException` | `@Valid` 触发的字段校验失败（username 为空、密码长度不合法、邮箱 / 手机号格式错误、年龄 < 0、姓名 / 确认密码缺失等） | `GlobalExceptionHandler` | 400 | 200 |
| 认证异常 | 登录凭证错误（认证标识不存在 / 密码不正确） | `GlobalExceptionHandler`（`BusinessException` 分支） | 401 | 200 |
| 认证异常 | 已抛出到控制器层的 `AuthenticationException` | `GlobalExceptionHandler` | 401 | 200 |
| 授权异常 `AccessDeniedException` | 已登录但权限不足 | `GlobalExceptionHandler` | 403 | 200 |
| JWT 异常 | Token 过期 `ExpiredJwtException`、签名 / 格式非法 `JwtException` | `GlobalExceptionHandler` | 401 | 200 |
| 未认证 / 无效 Token（过滤器层） | 无 Token、Token 无效、Token 过期访问受保护接口 | `SecurityConfig` 入口点 / `JwtAuthenticationFilter` | 401 | 401 |
| 图片验证码 | 正确 / 一次性使用 / 过期 / 不匹配 / 关闭开关（`CaptchaServiceTest`，不依赖数据库） | 单元测试直接调用 `CaptchaService.validate` | 400 | 不涉及 HTTP |
| 短信发送 | 频控 429、网络异常 / 5xx 触发重试、4xx 不重试（`SmsServiceTest`，Mock `RestTemplate`，不发起真实网络请求） | 单元测试直接调用 `SmsService.send` | 429 / 502 | 不涉及 HTTP |
| 兜底异常 `Exception` | 不可预期的运行时异常 | `GlobalExceptionHandler` | 500 | 200 |

> 说明：`GlobalExceptionHandler` 未标注 `@ResponseStatus`，因此**经由它处理的异常 HTTP 状态恒为 200**，真正的错误码体现在响应体的 `code` 字段；而「未认证 / 无效 Token」的 401 由 Spring Security 入口点与 JWT 过滤器**直接写出真实 HTTP 401**，二者路径不同，测试断言也据此区分。

测试类职责：

- `GlobalExceptionHandlerTest`：单元测试，直接构造各类异常调用处理器方法，稳定覆盖全部 handler 分支（含 403 授权、429 / 502 短信相关分支、JWT 过期 / 无效、500 兜底）。
- `AuthControllerExceptionTest`：MockMvc 集成测试，走真实 `/auth/register`、`/auth/login` 接口，验证业务异常、参数校验（含手机号、验证码字段）与登录凭证错误；通过 `@MockBean CaptchaService` 关闭真实验证码校验逻辑，使断言聚焦于注册 / 登录自身的参数与业务异常。
- `SecurityExceptionTest`：MockMvc 集成测试，走安全过滤链，验证无 Token / 无效 Token / 过期 Token 的 401，以及有效 Token 查询不存在用户的 404。
- `CaptchaServiceTest`：纯单元测试（不依赖 SpringBootTest / 数据库），验证图片验证码的生成、正确校验、一次性使用（成功 / 失败均消费同一个 key）、过期失效、开关关闭等规则。
- `SmsServiceTest`：纯单元测试（Mock `RestTemplate`，不发起真实网络请求），验证发送开关关闭时不触达真实接口、同一手机号频控、网络异常 / 5xx 触发重试且次数受 `maxRetry` 限制、4xx 不重试直接失败。

> **运行集成测试前请先完成上方「数据库初始化」小节**（`AuthControllerExceptionTest` / `SecurityExceptionTest` 直连本地 MySQL，依赖真实数据库连接与 `zhangsan` 预置账号；`DemoDataInitializer` 会在测试上下文启动时自动补齐预置数据）。`CaptchaServiceTest` / `SmsServiceTest` / `GlobalExceptionHandlerTest` 不依赖数据库，可独立运行。

### 本地运行测试

```bash
cd backend
# 运行全部测试
mvn test
# 仅运行指定测试类
mvn test -Dtest=GlobalExceptionHandlerTest
mvn test -Dtest=CaptchaServiceTest,SmsServiceTest
mvn test -Dtest=AuthControllerExceptionTest,SecurityExceptionTest
```

## CORS 说明

后端已通过 `CorsConfig` 配置全局 CORS（允许任意来源、任意方法）。开发环境下前端经 Vite 代理访问，不会触发跨域；该配置用于前端直连后端（如生产环境不同域名部署）的场景。

## 说明

- 后端用户数据持久化到 MySQL，数据库访问统一使用 MyBatis，SQL 全部写在 `resources/mapper/UserMapper.xml`（不使用注解方式写 SQL）。
- `users` 表包含 `created_by / created_date / last_modified_by / last_modified_date` 审计字段，实体对应 `createdBy / createdDate / lastModifiedBy / lastModifiedDate`；新增和更新用户（含注册等认证相关数据写入）时由 `UserRepository.save()` 统一自动填充，无需业务代码手动设置。
- 图片验证码内容与短信发送频控记录均为进程内内存实现（重启后失效 / 重置），未引入 Redis；如需多实例共享需后续替换存储方案。
- 前端 Element Plus 采用完整引入方式（简单可靠）；生产环境可改用 `unplugin-auto-import` + `unplugin-vue-components` 实现按需引入。
