# demo-fullstack

前后端分离的全栈示例项目，包含：

- **backend/**：Spring Boot 3.x + Java 17 + Maven，提供用户资源 REST API（内存存储，开箱即用）
- **frontend/**：Vue 3 + TypeScript + Vite + Element Plus，提供用户管理示例页面（表格 + 表单，完整增删改查）

## 目录结构

```
demo-fullstack/
├── backend/                              # Spring Boot 后端
│   ├── pom.xml                           # Maven 配置（Spring Boot 3.3.x，Java 17）
│   └── src/main/
│       ├── java/com/example/demo/
│       │   ├── DemoApplication.java      # 启动入口
│       │   ├── common/                   # 统一响应 Result / 业务异常 / 全局异常处理
│       │   ├── config/                   # CORS 跨域配置
│       │   ├── controller/               # 控制层（REST API）
│       │   ├── entity/                   # 实体类
│       │   ├── repository/               # 仓储层（ConcurrentHashMap 内存实现）
│       │   └── service/                  # 服务层（接口 + 实现）
│       └── resources/
│           └── application.yml           # 端口 8081，上下文路径 /api
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

## CORS 说明

后端已通过 `CorsConfig` 配置全局 CORS（允许任意来源、任意方法）。开发环境下前端经 Vite 代理访问，不会触发跨域；该配置用于前端直连后端（如生产环境不同域名部署）的场景。

## 说明

- 后端数据存储在内存中（`ConcurrentHashMap`），重启后数据重置；预置了「张三」「李四」两条示例数据，启动后可直接联调。
- 前端 Element Plus 采用完整引入方式（简单可靠）；生产环境可改用 `unplugin-auto-import` + `unplugin-vue-components` 实现按需引入。
