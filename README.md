# demo-fullstack

前后端分离的全栈示例项目，包含：

- **backend/**：Spring Boot 3.x + Java 17 + Maven + MyBatis + MySQL，提供用户资源 REST API、图片验证码、云片网短信发送服务
- **frontend/**：Vue 3 + TypeScript + Vite + Element Plus，提供登录 / 注册（图片验证码 + 短信验证码两步验证）与用户管理（增删改查 + 短信发送记录查看）页面

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
│   └── src/test/                         # 异常处理 / 验证码 / 短信服务 / 发送短信接口 / 短信记录测试用例
│       └── java/com/example/demo/
│           ├── common/
│           │   └── GlobalExceptionHandlerTest.java   # 全局异常处理器单元测试
│           ├── controller/
│           │   ├── AuthControllerExceptionTest.java  # 注册/登录接口异常集成测试（含短信验证码两步验证）
│           │   ├── SecurityExceptionTest.java        # 认证/JWT/授权安全链路测试
│           │   ├── SendSmsCodeControllerTest.java    # /auth/send-sms-code 发送短信接口测试
│           │   └── SmsRecordsControllerTest.java     # /sms/records 短信记录查询接口测试
│           └── service/
│               ├── CaptchaServiceTest.java           # 图片验证码服务单元测试
│               └── SmsServiceTest.java               # 短信服务验证码存储/校验/重试/频控/记录单元测试
└── frontend/                             # Vue 3 前端
    ├── package.json
    ├── vite.config.ts                    # Vite 配置（含 /api 代理到后端）
    ├── tsconfig.json / tsconfig.node.json
    ├── index.html
    └── src/
        ├── main.ts                       # 入口（注册 Element Plus、路由）
        ├── App.vue                       # 根组件（布局）
        ├── api/                          # axios 封装 + 认证（含短信两步验证）/ 用户 / 短信记录接口
        ├── router/                       # vue-router 路由
        ├── utils/                        # 令牌与登录用户状态管理
        └── views/
            ├── Login.vue                 # 登录页（图片验证码 + 短信验证码两步验证）
            ├── Register.vue              # 注册页（手机号 + 图片验证码 + 短信验证码两步验证）
            └── UserManage.vue            # 用户管理页面（增删改查 + 短信发送记录查看）
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
| POST   | /api/auth/send-sms-code | 发送短信验证码（匿名访问，先校验图片验证码）|
| POST   | /api/auth/register      | 注册（需图片验证码 + 短信验证码 + 手机号）  |
| POST   | /api/auth/login         | 登录（需图片验证码 + 短信验证码，返回 JWT） |
| GET    | /api/auth/me            | 获取当前登录用户                           |
| POST   | /api/auth/logout        | 退出登录                                   |
| GET    | /api/users              | 查询用户列表                               |
| GET    | /api/users/{id}         | 查询单个用户                               |
| POST   | /api/users              | 新增用户                                   |
| PUT    | /api/users/{id}         | 更新用户                                   |
| DELETE | /api/users/{id}         | 删除用户                                   |
| GET    | /api/sms/records        | 查询短信发送记录（需登录访问，按时间倒序） |
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

注册 / 登录请求体需额外携带图片验证码与短信验证码相关字段：

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
  "captchaValue": "<用户输入的图片验证码字符>",
  "smsCode": "<用户收到的 6 位短信验证码>"
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

## 短信验证码两步验证与发送记录

登录 / 注册采用「图片验证码 → 短信验证码」两步串行验证：

1. 页面加载时通过 `GET /api/auth/captcha` 展示图片验证码（复用一次性 `captchaKey`）。
2. 用户输入图片验证码后点击「发送短信验证码」，前端把 `captchaKey` / `captchaValue` 连同目标手机号（注册场景由前端传入 `phone`）或用户名（登录场景由 `username` 提交、后端查手机号）一起发送到匿名接口 `POST /api/auth/send-sms-code`；**只有图片验证码校验通过**，后端才调用 `SmsService.sendVerifyCode(phone)` 真正发送 6 位数字验证码，否则返回明确提示且短信不发送。
3. 发送成功后按钮进入 60 秒倒计时禁用状态（与 `sms.rate-limit-seconds` 保持一致）；图片验证码一次性使用，每次成功发送后自动刷新，用户也可点击图片手动刷新。
4. 登录 / 注册请求体携带 `captchaKey` / `captchaValue` 与新增的 `smsCode`；后端 `AuthServiceImpl` 按「先校验图片验证码、再校验短信验证码」顺序执行，任一环节失败均返回统一 `Result` 错误（不泄露内部密钥）。

关键约束：

- `POST /api/auth/send-sms-code` 为 permitAll（登录前即可调用），内部先 `CaptchaService.validate` 再 `SmsService.sendVerifyCode(phone)`；防滥发依赖图片验证码 + 同一手机号 60 秒频控（超频返回 `code=429`）。
- `SmsService` 提供验证码「生成并存储 + 校验」的完整方法：验证码以手机号为 key 存于进程内内存（`ConcurrentHashMap`），有效期由 `sms.code-expire-seconds`（默认 300 秒，与图片验证码过期配置分开）控制，一次性使用（无论成功失败都消费同一个 key），校验逻辑与图片验证码一致。
- 短信发送记录：每次实际发送（成功 / 失败）都会记录手机号、验证码明文（演示用途）、发送时间与状态，保存在进程内内存列表（`ConcurrentLinkedDeque`，不落库），通过需 JWT 认证的 `GET /api/sms/records` 按时间倒序返回，前端用户管理页以表格展示并支持手动刷新。
- 前端不直接对接云片网 API；`apiKey`、`accountSid` 等敏感配置只在后端 `SmsService` 内部读取，不会出现在任何接口响应 / 异常信息中。
- 调用云片网接口失败时（网络异常 / 超时 / 5xx 可恢复错误）会按 `sms.max-retry` 重试，重试间隔按 `sms.retry-backoff`（`linear` 线性 / `exponential` 指数）退避；4xx 客户端错误（如 `apiKey` 错误）不重试直接失败，统一返回 `code=502`「短信发送失败，请稍后重试」，不泄露敏感配置细节，并记录一条 `FAILED` 发送记录。
- `sms.enabled=false`（默认值）时不调用真实云片网接口，仅打印日志，但**验证码仍会生成存储**以便本地联调；配置真实密钥后改为 `true` 才会实际发送短信。关闭状态下短信仍记为成功，可在用户管理页「短信发送记录」查看明文验证码。

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
  code-expire-seconds: 300            # 短信验证码有效期（秒），与图片验证码 captcha.expire-seconds 分开配置
```

## 异常处理与测试

后端通过 `common/GlobalExceptionHandler`（`@RestControllerAdvice`）将各类异常统一包装为前述 `Result` 结构返回。测试用例位于 `backend/src/test/`，基于 JUnit 5 + Spring Boot Test + MockMvc + Mockito（由已有的 `spring-boot-starter-test` 提供，无需额外依赖），共 58 个用例，覆盖以下场景：

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
| 短信验证码（服务） | 验证码生成 / 存储、正确校验、一次性使用、不匹配、过期、开关关闭、发送记录写入与倒序（`SmsServiceTest`，Mock `RestTemplate`，不发起真实网络请求） | 单元测试直接调用 `SmsService.send` / `validateCode` / `getRecords` | 400 / 429 / 502 | 不涉及 HTTP |
| 短信验证码（登录 / 注册两步验证） | 登录 / 注册缺失 smsCode（参数校验）、短信验证码错误 / 过期（`AuthControllerExceptionTest`，`@MockBean CaptchaService + SmsService`） | `GlobalExceptionHandler` | 400 | 200 |
| 发送短信验证码接口 | 图片验证码不通过不发送、通过则发送、频控 429、缺少验证码参数（`SendSmsCodeControllerTest`，`@MockBean`） | `GlobalExceptionHandler` / 过滤器 | 400 / 429 | 200 |
| 短信发送记录查询 | 无 Token 访问 /sms/records、有效 Token 返回记录列表（`SmsRecordsControllerTest`） | `SecurityConfig` 入口点 / 控制器 | 401 / 200 | 401 / 200 |
| 短信发送 | 频控 429、网络异常 / 5xx 触发重试、4xx 不重试（`SmsServiceTest`，Mock `RestTemplate`，不发起真实网络请求） | 单元测试直接调用 `SmsService.send` | 429 / 502 | 不涉及 HTTP |
| 兜底异常 `Exception` | 不可预期的运行时异常 | `GlobalExceptionHandler` | 500 | 200 |

> 说明：`GlobalExceptionHandler` 未标注 `@ResponseStatus`，因此**经由它处理的异常 HTTP 状态恒为 200**，真正的错误码体现在响应体的 `code` 字段；而「未认证 / 无效 Token」的 401 由 Spring Security 入口点与 JWT 过滤器**直接写出真实 HTTP 401**，二者路径不同，测试断言也据此区分。

测试类职责：

- `GlobalExceptionHandlerTest`：单元测试，直接构造各类异常调用处理器方法，稳定覆盖全部 handler 分支（含 403 授权、429 / 502 短信相关分支、JWT 过期 / 无效、500 兜底）。
- `AuthControllerExceptionTest`：MockMvc 集成测试，走真实 `/auth/register`、`/auth/login` 接口，验证业务异常、参数校验（含手机号、验证码、smsCode 字段）与登录凭证错误；通过 `@MockBean CaptchaService` 与 `@MockBean SmsService` 关闭真实验证码 / 短信验证码校验，使断言聚焦于注册 / 登录自身的参数与业务异常；另有登录 / 注册短信验证码错误、过期、smsCode 缺失等两步验证异常用例。
- `SendSmsCodeControllerTest`：MockMvc 集成测试，走匿名接口 `/auth/send-sms-code`，`@MockBean CaptchaService + SmsService` 验证「图片验证码不通过不发送」「通过则按手机号发送」「频控 429」「缺少验证码参数」等编排。
- `SmsRecordsControllerTest`：MockMvc 集成测试，验证需 JWT 认证的 `GET /sms/records`：无 Token 返回 401，有效 Token 返回按时间倒序的发送记录列表。
- `CaptchaServiceTest`：纯单元测试（不依赖 SpringBootTest / 数据库），验证图片验证码的生成、正确校验、一次性使用（成功 / 失败均消费同一个 key）、过期失效、开关关闭等规则。
- `SmsServiceTest`：纯单元测试（Mock `RestTemplate`，不发起真实网络请求），验证发送开关关闭时不触达真实接口但仍生成验证码、同一手机号频控、网络异常 / 5xx 触发重试且次数受 `maxRetry` 限制、4xx 不重试直接失败，以及验证码存储 / 校验 / 一次性 / 过期与发送记录写入及倒序。

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
