# demo-fullstack

前后端分离的全栈示例项目，包含：

- **backend/**：Spring Boot 3.x + Java 17 + Maven，提供用户资源 REST API（内存存储，开箱即用）
- **frontend/**：Vue 3 + TypeScript + Vite + Element Plus，提供用户管理示例页面（表格 + 表单，完整增删改查）

## 目录结构

```
demo-fullstack/
├── backend/                              # Spring Boot 后端
│   ├── pom.xml                           # Maven 配置（Spring Boot 3.3.x，Java 17）
│   ├── src/main/
│   │   ├── java/com/example/demo/
│   │   │   ├── DemoApplication.java      # 启动入口
│   │   │   ├── common/                   # 统一响应 Result / 业务异常 / 全局异常处理
│   │   │   ├── config/                   # CORS 跨域配置
│   │   │   ├── controller/               # 控制层（REST API）
│   │   │   ├── entity/                   # 实体类
│   │   │   ├── repository/               # 仓储层（ConcurrentHashMap 内存实现）
│   │   │   └── service/                  # 服务层（接口 + 实现）
│   │   └── resources/
│   │       └── application.yml           # 端口 8081，上下文路径 /api
│   └── src/test/                         # 异常处理相关测试用例
│       └── java/com/example/demo/
│           ├── common/
│           │   └── GlobalExceptionHandlerTest.java   # 全局异常处理器单元测试
│           └── controller/
│               ├── AuthControllerExceptionTest.java  # 注册/登录接口异常集成测试
│               └── SecurityExceptionTest.java        # 认证/JWT/授权安全链路测试
└── frontend/                             # Vue 3 前端
    ├── package.json
    ├── vite.config.ts                    # Vite 配置（含 /api 代理到后端）
    ├── tsconfig.json / tsconfig.node.json
    ├── index.html
    └── src/
        ├── main.ts                       # 入口（注册 Element Plus、路由）
        ├── App.vue                       # 根组件（布局）
        ├── api/                          # axios 封装 + 用户接口
        ├── router/                       # vue-router 路由
        └── views/
            └── UserManage.vue            # 用户管理页面（增删改查示例）
```

## 环境要求

- JDK 17+
- Maven 3.6+
- Node.js 18+（推荐 20 LTS）

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

| 方法   | 路径            | 说明         |
| ------ | --------------- | ------------ |
| GET    | /api/users      | 查询用户列表 |
| GET    | /api/users/{id} | 查询单个用户 |
| POST   | /api/users      | 新增用户     |
| PUT    | /api/users/{id} | 更新用户     |
| DELETE | /api/users/{id} | 删除用户     |

请求 / 响应体示例（新增用户）：

```json
{
  "name": "王五",
  "email": "wangwu@example.com",
  "age": 28
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

## 异常处理与测试

后端通过 `common/GlobalExceptionHandler`（`@RestControllerAdvice`）将各类异常统一包装为前述 `Result` 结构返回。测试用例位于 `backend/src/test/`，基于 JUnit 5 + Spring Boot Test + MockMvc + Mockito（由已有的 `spring-boot-starter-test` 提供，无需额外依赖），共 24 个用例，覆盖以下异常类型：

| 异常类型 | 触发场景 | 处理器 | `Result.code` | HTTP 状态 |
| -------- | -------- | ------ | ------------- | --------- |
| 业务异常 `BusinessException` | 认证标识已存在（409）、确认密码不一致（400）、用户不存在（404）等 | `GlobalExceptionHandler` | 400 / 404 / 409 | 200 |
| 参数校验异常 `MethodArgumentNotValidException` | `@Valid` 触发的字段校验失败（username 为空、密码长度不合法、邮箱格式错误、年龄 < 0、姓名/确认密码缺失等） | `GlobalExceptionHandler` | 400 | 200 |
| 认证异常 | 登录凭证错误（认证标识不存在 / 密码不正确） | `GlobalExceptionHandler`（`BusinessException` 分支） | 401 | 200 |
| 认证异常 | 已抛出到控制器层的 `AuthenticationException` | `GlobalExceptionHandler` | 401 | 200 |
| 授权异常 `AccessDeniedException` | 已登录但权限不足 | `GlobalExceptionHandler` | 403 | 200 |
| JWT 异常 | Token 过期 `ExpiredJwtException`、签名/格式非法 `JwtException` | `GlobalExceptionHandler` | 401 | 200 |
| 未认证 / 无效 Token（过滤器层） | 无 Token、Token 无效、Token 过期访问受保护接口 | `SecurityConfig` 入口点 / `JwtAuthenticationFilter` | 401 | 401 |
| 兜底异常 `Exception` | 不可预期的运行时异常 | `GlobalExceptionHandler` | 500 | 200 |

> 说明：`GlobalExceptionHandler` 未标注 `@ResponseStatus`，因此**经由它处理的异常 HTTP 状态恒为 200**，真正的错误码体现在响应体的 `code` 字段；而「未认证 / 无效 Token」的 401 由 Spring Security 入口点与 JWT 过滤器**直接写出真实 HTTP 401**，二者路径不同，测试断言也据此区分。

测试类职责：

- `GlobalExceptionHandlerTest`：单元测试，直接构造各类异常调用处理器方法，稳定覆盖全部 handler 分支（含 403 授权、JWT 过期/无效、500 兜底）。
- `AuthControllerExceptionTest`：MockMvc 集成测试，走真实 `/auth/register`、`/auth/login` 接口，验证业务异常、参数校验与登录凭证错误。
- `SecurityExceptionTest`：MockMvc 集成测试，走安全过滤链，验证无 Token / 无效 Token / 过期 Token 的 401，以及有效 Token 查询不存在用户的 404。

### 本地运行测试

```bash
cd backend
# 运行全部测试
mvn test
# 仅运行指定测试类
mvn test -Dtest=GlobalExceptionHandlerTest
mvn test -Dtest=AuthControllerExceptionTest,SecurityExceptionTest
```

## CORS 说明

后端已通过 `CorsConfig` 配置全局 CORS（允许任意来源、任意方法）。开发环境下前端经 Vite 代理访问，不会触发跨域；该配置用于前端直连后端（如生产环境不同域名部署）的场景。

## 说明

- 后端数据存储在内存中（`ConcurrentHashMap`），重启后数据重置；预置了「张三」「李四」两条示例数据，启动后可直接联调。
- 前端 Element Plus 采用完整引入方式（简单可靠）；生产环境可改用 `unplugin-auto-import` + `unplugin-vue-components` 实现按需引入。
