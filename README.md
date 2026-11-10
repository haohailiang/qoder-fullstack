# demo-fullstack

前后端分离的微服务电商示例项目，基于 Spring Cloud 微服务架构 + qiankun 微前端架构：

- **backend/**：Spring Boot 3.3.x + Java 17 + Maven + Spring Cloud + Spring Cloud Alibaba，拆分为 5 个微服务模块
  - `ecommerce-common` — 公共模块（统一响应 Result / 业务异常 / 全局异常处理 / 秒杀消息体）
  - `ecommerce-gateway` — API 网关（Spring Cloud Gateway，JWT 全局鉴权，按路径路由到下游微服务，聚合 Swagger 文档）
  - `ecommerce-user` — 用户服务（注册 / 登录 / 图片验证码 / 短信验证码 / 用户 CRUD / 会话管理 / 主题偏好）
  - `ecommerce-product` — 商品服务（商品 CRUD / Elasticsearch 搜索 / 秒杀库存预扣 / Lua 脚本 / 机票航班 CRUD 与航线频率统计）
  - `ecommerce-order` — 订单服务（订单 CRUD / 秒杀订单异步消费 / OpenFeign 调用商品服务）
- **frontend/**：基于 qiankun 微前端框架，拆分为 3 个子应用
  - `base-app` — Vue 3 + TypeScript + Vite + Element Plus，主应用（登录 / 注册 / 用户管理 / 子应用容器 / 全局主题切换 / Admin 航线频率地图）
  - `product-app` — React 18 + TypeScript + Vite + Ant Design，商品子应用（商品列表 / 商品详情 / 秒杀 / 暗黑模式适配 / 用户行为埋点 / 机票详情 2D·3D 航线可视化）
  - `order-app` — 原生 TypeScript + Vite + Axios，订单子应用（我的订单 / 全部订单 / 订单详情 / 暗黑模式适配）

## 架构概览

```
                          ┌─────────────────────────────────────────┐
                          │           Spring Cloud Gateway          │
                          │    (port 8080, JWT 鉴权, Swagger 聚合)   │
                          └──────┬──────────┬──────────┬────────────┘
                                 │          │          │
                    /api/user/**  │ /api/product/** │ /api/order/**
                                 │          │          │
                    ┌────────────┴──┐ ┌─────┴──────┐ ┌┴─────────────┐
                    │ ecommerce-user│ │ecommerce-  │ │ecommerce-    │
                    │  (port 8081)  │ │ product    │ │  order       │
                    │  + Swagger UI │ │(port 8082) │ │ (port 8083)  │
                    └───────┬───────┘ │+ Swagger UI│ │+ Swagger UI  │
                            │         └─────┬──────┘ └┬─────────────┘
              ┌─────────────┴───────────────┴──────────┴─────────┐
              │              中间件服务（Docker）                    │
              │  MySQL 8.0 · Redis 7 · Nacos v2.3.0              │
              │  RabbitMQ 3 · Elasticsearch 8.11.0                │
              └───────────────────────────────────────────────────┘

    ┌──────────────────────────────────────────────────────────────┐
    │                   qiankun 微前端                              │
    │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐ │
    │  │  base-app      │  │  product-app   │  │  order-app     │ │
    │  │  Vue 3 :5173   │→ │  React :7100   │  │  TS    :7200   │ │
    │  │  主题切换+暗黑  │→ │  Ant Design    │  │  暗黑模式适配   │ │
    │  └────────────────┘  │  暗黑/亮色主题  │  └────────────────┘ │
    │                         └────────────────┘                  │
    └──────────────────────────────────────────────────────────────┘
```

## 目录结构

```
demo-fullstack/
├── backend/                                  # Spring Cloud 微服务后端
│   ├── pom.xml                               # Maven 父 POM（Spring Boot 3.3.x, Java 17, Spring Cloud 2023.0.x, springdoc 2.6.0）
│   ├── ecommerce-common/                     # 公共模块
│   │   └── src/main/java/com/ecommerce/common/
│   │       ├── Result.java                   # 统一响应结构
│   │       ├── BusinessException.java        # 业务异常
│   │       ├── GlobalExceptionHandler.java   # 全局异常处理器
│   │       ├── SeckillMessage.java           # 秒杀消息体（MQ 传输）
│   │       ├── config/
│   │       │   ├── SecurityConfig.java       # 统一安全配置（SecurityFilterChain）
│   │       │   └── SeckillRabbitConfig.java  # RabbitMQ 秒杀队列配置
│   │       └── security/
│   │           └── TrustedAuthenticationFilter.java  # 可信认证过滤器（信任网关请求头）
│   ├── ecommerce-gateway/                    # API 网关服务
│   │   └── src/main/java/com/ecommerce/gateway/
│   │       ├── GatewayApplication.java       # 网关启动入口
│   │       ├── config/                       # CORS 等网关配置
│   │       └── filter/
│   │           └── AuthGlobalFilter.java     # JWT 全局鉴权过滤器（白名单放行 + Token 校验 + 用户信息透传 + Swagger 路径放行）
│   ├── ecommerce-user/                       # 用户微服务
│   │   └── src/main/
│   │       ├── java/com/ecommerce/user/
│   │       │   ├── controller/               # AuthController / CaptchaController / SmsController / UserController（含主题 API） / HealthController
│   │       │   ├── service/                  # 认证 / 用户 / 图片验证码 / 短信 / Redis 会话服务
│   │       │   ├── entity/                   # User 实体（含审计字段 + 主题偏好字段）
│   │       │   ├── repository/               # MyBatis Mapper 访问层
│   │       │   ├── config/                   # UserSecurityConfig（PasswordEncoder）
│   │       │   ├── security/                 # JWT 工具与认证过滤器
│   │       │   └── test/                     # JUnit 5 单元测试（9 个测试类）
│   │       └── resources/
│   │           ├── application.yml           # 端口 8081，context-path /user
│   │           ├── db/schema.sql             # MySQL 建表 SQL（users 表）
│   │           └── mapper/UserMapper.xml     # 用户表 SQL
│   ├── ecommerce-product/                    # 商品微服务
│   │   └── src/main/
│   │       ├── java/com/ecommerce/product/
│   │       │   ├── controller/               # ProductController / SeckillController / FlightController（含 OpenAPI 注解）
│   │       │   ├── service/                  # 商品 / 秒杀库存 / ES 搜索 / 航班（FlightService）服务
│   │       │   ├── entity/                   # Product、Flight 实体
│   │       │   ├── repository/               # MyBatis Mapper 访问层（含 FlightRepository / FlightMapper）
│   │       │   ├── elasticsearch/            # ES 商品文档与 Repository
│   │       │   ├── config/                   # Redis / ES 配置
│   │       │   └── test/                     # JUnit 5 单元测试（7 个测试类：商品 4 + 航班 3）
│   │       └── resources/
│   │           ├── application.yml           # 端口 8082，context-path /product
│   │           ├── db/schema.sql             # 商品表 + 航班表（flights）建表 SQL
│   │           ├── lua/seckill.lua           # Redis Lua 秒杀库存预扣脚本
│   │           └── mapper/                   # ProductMapper.xml、FlightMapper.xml（JOIN products）
│   ├── ecommerce-order/                      # 订单微服务
│   │   └── src/main/
│   │       ├── java/com/ecommerce/order/
│   │       │   ├── controller/               # OrderController（含 OpenAPI 注解）
│   │       │   ├── service/                  # 订单服务（含秒杀订单异步消费）
│   │       │   ├── entity/                   # Order 实体
│   │       │   ├── repository/               # MyBatis Mapper 访问层
│   │       │   ├── client/                   # ProductClient（OpenFeign 调用商品服务）
│   │       │   ├── consumer/                 # SeckillOrderConsumer（RabbitMQ 秒杀消息消费者）
│   │       │   ├── config/                   # 无（安全配置已由 common 模块统一提供）
│   │       │   └── test/                     # JUnit 5 单元测试（5 个测试类）
│   │       └── resources/
│   │           ├── application.yml           # 端口 8083，context-path /order
│   │           ├── db/schema.sql             # 订单表建表 SQL
│   │           └── mapper/OrderMapper.xml    # 订单表 SQL
│   ├── Dockerfile.gateway                    # 网关 Docker 构建文件
│   ├── Dockerfile.user                       # 用户服务 Docker 构建文件
│   ├── Dockerfile.product                    # 商品服务 Docker 构建文件
│   └── Dockerfile.order                      # 订单服务 Docker 构建文件
├── frontend/                                 # qiankun 微前端
│   ├── base-app/                             # 主应用（Vue 3 + Element Plus）
│   │   ├── src/
│   │   │   ├── main.ts                       # 入口（注册 Element Plus、路由、qiankun 启动、主题初始化）
│   │   │   ├── App.vue                       # 根组件（布局 + 子应用容器 + 主题切换按钮）
│   │   │   ├── micro/index.ts                # qiankun 子应用注册与全局状态管理
│   │   │   ├── api/                          # axios 封装 + 认证 / 用户 / 短信 / 航班接口（flight.ts）
│   │   │   ├── router/                       # vue-router（含子应用路由占位）
│   │   │   ├── utils/                        # 令牌与登录用户状态管理 + 主题管理 (theme.ts)
│   │   │   ├── __tests__/                    # Vitest 单元测试（api（含 flight）/ router / utils）
│   │   │   └── views/
│   │   │       ├── Login.vue                 # 登录页（图片验证码 + 短信验证码两步验证 + 登录后同步主题）
│   │   │       ├── Register.vue              # 注册页（手机号 + 图片验证码 + 短信验证码两步验证）
│   │   │       ├── UserManage.vue            # 用户管理页面（增删改查 + 短信发送记录查看）
│   │   │       ├── FlightMap.vue             # Admin 航线频率地图（ECharts 飞线图 + 城市筛选）
│   │   │       └── SubAppPlaceholder.vue     # 子应用容器占位组件
│   │   ├── vite.config.ts                    # Vite 配置（/api 代理到网关 8080）
│   │   ├── vitest.config.ts                  # Vitest 单元测试配置
│   │   └── package.json
│   ├── product-app/                          # 商品子应用（React 18 + Ant Design）
│   │   ├── src/
│   │   │   ├── main.tsx                      # 入口（qiankun 生命周期导出 + 独立运行支持 + 主题同步）
│   │   │   ├── App.tsx                       # 根组件（React Router + Ant Design ConfigProvider 暗黑/亮色主题）
│   │   │   ├── utils/
│   │   │   │   └── tracker.ts                # 前端埋点工具（页面访问 / 商品曝光 / 交互点击）
│   │   │   ├── __tests__/                    # Vitest 单元测试（utils/tracker、pages/ProductList）
│   │   │   ├── components/                   # 航线可视化组件（FlightMap2D/3D、FlightInfoPanel、FlightAnimationController）
│   │   │   ├── public/models/plane.glb       # Cesium 3D 飞机模型
│   │   │   └── pages/
│   │   │       ├── ProductList.tsx           # 商品列表页（含 IntersectionObserver 曝光埋点 + 机票分类入口）
│   │   │       ├── ProductDetail.tsx         # 商品详情页（含秒杀入口）
│   │   │       └── FlightDetail.tsx          # 机票详情页（2D/3D 地图切换 + 飞机视角动画）
│   │   ├── vite.config.ts                    # Vite 配置（端口 7100，/api 代理到网关 8080）
│   │   ├── vitest.config.ts                  # Vitest 单元测试配置
│   │   └── package.json
│   ├── order-app/                            # 订单子应用（原生 TypeScript）
│   │   ├── src/
│   │   │   ├── main.ts                       # 入口（qiankun 生命周期导出 + 独立运行支持 + 主题同步）
│   │   │   └── app.ts                        # OrderApp 类（订单列表 / 详情 / 取消 / 暗黑模式颜色适配）
│   │   ├── __tests__/                        # Vitest 单元测试（app / i18n）
│   │   ├── vite.config.ts                    # Vite 配置（端口 7200，/api 代理到网关 8080）
│   │   ├── vitest.config.ts                  # Vitest 单元测试配置
│   │   └── package.json
│   └── e2e/                                  # Playwright 端到端测试
│       ├── fixtures/test-data.ts             # 测试数据夹具
│       ├── helpers.ts                        # 通用测试辅助工具
│       ├── mocks/                            # 接口 Mock（auth / order / product / user / flight）
│       ├── login.spec.ts                     # 登录流程 E2E 测试
│       ├── product-search.spec.ts            # 商品搜索 E2E 测试
│       ├── flight-flow.spec.ts               # 机票浏览与详情 E2E 测试
│       ├── order-flow.spec.ts                # 订单流程 E2E 测试
│       ├── micro-frontend.spec.ts            # 微前端加载 E2E 测试
│       ├── theme-sync.spec.ts                # 主题同步 E2E 测试
│       ├── playwright.config.ts              # Playwright 配置（自动启动三个子应用 dev server）
│       └── package.json
├── init-sql/
│   └── 01-init-database.sql                  # 数据库初始化脚本（建库 + 建表 + 演示数据）
├── docker-compose.yml                        # 全栈 Docker 编排（基础设施 + 后端微服务 + 前端子应用）
├── docker-compose-infra.yml                  # 仅基础设施 Docker 编排（MySQL / Redis / Nacos / RabbitMQ / ES）
├── package.json                              # 根 package.json（npm workspaces 统一管理前端测试脚本）
└── vm-ssh.js                                 # 远程服务器 SSH 工具脚本
```

## 环境要求

- JDK 17+
- Maven 3.6+（推荐使用项目自带的 Maven Wrapper `mvnw`）
- Node.js 18+（推荐 20 LTS）
- MySQL 8.x
- Redis 7.x
- Nacos v2.3.0
- RabbitMQ 3.x（含 Management 插件）
- Elasticsearch 8.11.0

## 数据库初始化

后端启动前，需要先创建数据库和表。推荐使用项目根目录的初始化脚本 [`init-sql/01-init-database.sql`](init-sql/01-init-database.sql)，包含：

- 创建数据库 `demo_fullstack`（utf8mb4）
- `users` 表 — 用户数据（含审计字段 `created_by / created_date / last_modified_by / last_modified_date`，主题偏好 `theme_preference`）
- `products` 表 — 商品数据（含秒杀字段 `is_seckill / seckill_price / seckill_start_time / seckill_end_time`；机票类商品 `category = 'flight'`）
- `flights` 表 — 航班数据（机票商品特有字段：`product_id`、`departure_city / arrival_city`、经纬度、`flight_number`、`duration`、`frequency`、`route_coordinates` JSON，通过 `product_id` 关联 `products`）
- `orders` 表 — 订单数据（状态流转使用 `OrderStatus` 枚举：`0待支付 → 1已支付 → 2已发货 → 3已完成 → 4已取消`，MyBatis 通过自定义 TypeHandler 自动转换）
- 演示数据 — 3 个用户（admin / zhangsan / lisi，密码均为 `123456`）+ 6 个普通商品（含 3 个秒杀商品）+ 8 个机票商品及对应 8 条航班数据

```bash
# 方式一：Docker 部署时自动执行（挂载 init-sql 目录到 /docker-entrypoint-initdb.d）
#         脚本开头的 SET NAMES utf8mb4 已保证容器内客户端按 UTF-8 解释，无需额外参数
# 方式二：手动执行（必须带 --default-character-set=utf8mb4，
#         否则 mysql 客户端默认 latin1，会把脚本里的中文二次编码成乱码）
mysql -u root -p --default-character-set=utf8mb4 < init-sql/01-init-database.sql
```

各微服务的 `application.yml` 中 `spring.datasource.password` 需从占位符改为本机 MySQL 的真实密码（该配置仅供本地开发使用，不要把真实密码提交到仓库）。

## 后端启动

### 1. 启动基础设施

确保 MySQL、Redis、Nacos、RabbitMQ、Elasticsearch 已启动。可使用 Docker 一键部署：

```bash
# 仅启动基础设施
docker compose -f docker-compose-infra.yml up -d
```

### 2. 启动微服务

按以下顺序依次启动（网关最后启动）：

```bash
# 用户服务（port 8081）
cd backend/ecommerce-user
mvn spring-boot:run

# 商品服务（port 8082）
cd backend/ecommerce-product
mvn spring-boot:run

# 订单服务（port 8083）
cd backend/ecommerce-order
mvn spring-boot:run

# API 网关（port 8080）
cd backend/ecommerce-gateway
mvn spring-boot:run
```

> 也可在项目根目录使用 Maven Wrapper 统一构建：`cd backend; ./mvnw clean package -DskipTests`

### 3. 服务地址

| 微服务 | 端口 | context-path | 说明 |
|--------|------|-------------|------|
| API 网关 | 8080 | / | 统一入口，JWT 鉴权，路由分发，Swagger 文档聚合 |
| 用户服务 | 8081 | /user | 认证 / 用户 / 短信 / 验证码 / 主题偏好 |
| 商品服务 | 8082 | /product | 商品 CRUD / 搜索 / 秒杀 |
| 订单服务 | 8083 | /order | 订单管理 / 秒杀订单消费 |

> 前端及外部调用统一走网关 `http://localhost:8080`，由网关按路径前缀路由到对应微服务。

### 4. Swagger API 文档

各微服务均集成 `springdoc-openapi`，可通过网关聚合入口或各服务独立访问：

| 访问方式 | 地址 |
|----------|------|
| 网关聚合（推荐） | `http://localhost:8080/swagger-ui/index.html` |
| 用户服务 | `http://localhost:8081/user/swagger-ui/index.html` |
| 商品服务 | `http://localhost:8082/product/swagger-ui/index.html` |
| 订单服务 | `http://localhost:8083/order/swagger-ui/index.html` |

> 网关聚合页面提供下拉选择各微服务文档，Swagger 路径已加入鉴权白名单无需 Token 即可访问。

## 前端启动

### 1. 安装依赖

```bash
cd frontend/base-app && npm install
cd frontend/product-app && npm install
cd frontend/order-app && npm install
```

> 也可在 `frontend/` 根目录使用 npm workspaces 统一安装：`npm install`（需先在根 `package.json` 中配置 workspaces）

### 2. 启动子应用（先启动子应用，再启动主应用）

```bash
# 商品子应用（port 7100）
cd frontend/product-app
npm run dev

# 订单子应用（port 7200）
cd frontend/order-app
npm run dev

# 主应用（port 5173）
cd frontend/base-app
npm run dev
```

### 3. 访问地址

| 子应用 | 开发端口 | 技术栈 | 说明 |
|--------|---------|--------|------|
| 主应用 base-app | 5173 | Vue 3 + Element Plus | 登录 / 注册 / 用户管理 / qiankun 容器 / 全局主题切换 |
| 商品子应用 product-app | 7100 | React 18 + Ant Design | 商品列表 / 商品详情 / 秒杀 / 暗黑模式适配 |
| 订单子应用 order-app | 7200 | 原生 TypeScript | 我的订单 / 全部订单 / 订单详情 / 暗黑模式适配 |

- 主应用访问地址：http://localhost:5173
- 开发代理：各子应用均将 `/api` 前缀请求转发到 `http://localhost:8080`（API 网关）
- 微前端：主应用通过 qiankun 注册商品 / 订单子应用，路由 `/product/**` 和 `/order/**` 自动加载对应子应用

## 测试

项目已建立覆盖前后端的完整测试体系，包含单元测试与端到端测试。

### 前端单元测试（Vitest）

三个前端子应用均集成 Vitest 测试框架，配合各技术栈的测试工具：

| 子应用 | 测试工具 | 测试目录 |
|--------|---------|----------|
| base-app | Vitest + @vue/test-utils + jsdom | `frontend/base-app/src/__tests__/` |
| product-app | Vitest + @testing-library/react + jsdom | `frontend/product-app/src/__tests__/` |
| order-app | Vitest + jsdom | `frontend/order-app/src/__tests__/` |

运行命令：

```bash
# 运行所有子应用的单元测试
npm run test:unit

# 运行单个子应用的单元测试
npm run test:unit:base      # base-app
npm run test:unit:product   # product-app
npm run test:unit:order     # order-app

# 监听模式（开发时推荐）
cd frontend/base-app && npm run test:watch
```

### 端到端测试（Playwright）

`frontend/e2e/` 目录提供基于 Playwright 的 E2E 测试，覆盖核心业务流程：

| 测试文件 | 覆盖场景 |
|---------|----------|
| `login.spec.ts` | 登录流程（验证码 → 短信 → JWT） |
| `product-search.spec.ts` | 商品搜索与列表展示 |
| `flight-flow.spec.ts` | 机票浏览与航线详情跳转链路 |
| `order-flow.spec.ts` | 订单创建与状态流转 |
| `micro-frontend.spec.ts` | qiankun 子应用加载与切换 |
| `theme-sync.spec.ts` | 全局主题同步（暗黑/亮色） |

运行命令：

```bash
# 运行 E2E 测试（自动启动三个子应用 dev server）
npm run test:e2e

# 打开 Playwright UI 模式（可视化调试）
npm run test:e2e:ui
```

### 后端单元测试（JUnit 5）

三个业务微服务均提供 JUnit 5 单元测试，覆盖 controller、service、repository、entity 各层：

| 微服务 | 测试类数量 | 主要测试内容 |
|--------|-----------|--------------|
| ecommerce-user | 9 个测试类 | 认证控制器、用户控制器、验证码/短信服务、全局异常处理 |
| ecommerce-product | 7 个测试类 | 商品控制器、商品服务、秒杀库存服务、仓储层；航班 FlightController/Service/Repository |
| ecommerce-order | 5 个测试类 | 订单控制器、订单服务、订单状态枚举、Feign 客户端、仓储层 |

运行命令：

```bash
cd backend
./mvnw test                           # 运行所有模块测试
./mvnw test -pl ecommerce-user        # 运行单个模块测试
```

## API 一览

所有外部请求统一经 API 网关（port 8080）路由到下游微服务，网关 `AuthGlobalFilter` 负责 JWT 鉴权并将 `X-User-Id` / `X-User-Name` 写入请求头透传给下游。

### 用户服务（/api/user/**）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | /api/user/auth/captcha | 获取图片验证码（返回 Base64 PNG + captchaKey） | 匿名 |
| POST | /api/user/auth/send-sms-code | 发送短信验证码（先校验图片验证码，60 秒频控） | 匿名 |
| POST | /api/user/auth/register | 注册（需图片验证码 + 短信验证码 + 手机号） | 匿名 |
| POST | /api/user/auth/login | 登录（需图片验证码 + 短信验证码，返回 JWT + 用户信息含主题偏好） | 匿名 |
| GET | /api/user/auth/me | 获取当前登录用户信息 | JWT |
| POST | /api/user/auth/logout | 退出登录（清除 Redis 会话 Token） | JWT |
| GET | /api/user/users | 查询用户列表 | JWT |
| GET | /api/user/users/{id} | 查询单个用户 | JWT |
| POST | /api/user/users | 新增用户 | JWT |
| PUT | /api/user/users/{id} | 更新用户 | JWT |
| DELETE | /api/user/users/{id} | 删除用户 | JWT |
| GET | /api/user/sms/records | 查询短信发送记录（按时间倒序） | JWT |
| GET | /api/user/health | 健康检查 | 匿名 |
| GET | /api/user/users/theme | 获取当前用户主题偏好（dark / light） | JWT |
| PUT | /api/user/users/theme | 更新当前用户主题偏好 | JWT |

### 商品服务（/api/product/**）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | /api/product/products | 查询所有商品 | JWT |
| GET | /api/product/products/{id} | 根据 ID 查询商品 | JWT |
| GET | /api/product/products/category/{category} | 根据分类查询商品 | JWT |
| GET | /api/product/products/search | 搜索商品（关键词 / 分类 / 价格区间） | JWT |
| POST | /api/product/products | 创建商品 | JWT |
| PUT | /api/product/products/{id} | 更新商品 | JWT |
| DELETE | /api/product/products/{id} | 删除商品 | JWT |
| POST | /api/product/products/sync-es | 全量同步商品到 Elasticsearch | JWT |
| POST | /api/product/products/deduct-stock | 扣减库存（供订单服务 Feign 内部调用） | JWT |
| POST | /api/product/products/seckill/init/{productId} | 初始化秒杀库存（ Redis 预加载） | JWT |
| POST | /api/product/products/seckill/{productId} | 秒杀抢购（Lua 预扣 + MQ 异步下单） | JWT |
| GET | /api/product/products/seckill/stock/{productId} | 查询秒杀库存 | JWT |
| GET | /api/product/flights | 查询全部航班（含关联商品信息） | JWT |
| GET | /api/product/flights/{id} | 根据 ID 查询航班详情 | JWT |
| GET | /api/product/flights/search | 按出发/落地城市筛选航班 | JWT |
| GET | /api/product/flights/frequency | 城市间航班频率统计（供飞线图） | JWT |
| POST | /api/product/flights | 创建航班 | JWT |
| PUT | /api/product/flights/{id} | 更新航班 | JWT |
| DELETE | /api/product/flights/{id} | 删除航班 | JWT |

### 订单服务（/api/order/**）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | /api/order/orders | 创建订单 | JWT |
| GET | /api/order/orders/my | 查询当前用户的订单列表 | JWT |
| GET | /api/order/orders/{orderNo} | 根据订单号查询订单 | JWT |
| GET | /api/order/orders | 查询所有订单（管理用） | JWT |
| POST | /api/order/orders/{orderId}/cancel | 取消订单 | JWT |
| PUT | /api/order/orders/{orderId}/status | 更新订单状态（参数：0-4） | JWT |

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

- 前端登录 / 注册页面通过 `GET /api/user/auth/captcha` 获取验证码图片（Base64 PNG）与一次性 `captchaKey`，点击图片可刷新。
- 提交登录 / 注册时须携带 `captchaKey` 与用户输入的 `captchaValue`；后端在执行业务逻辑（查重、密码校验）之前先校验图片验证码，校验失败（不存在 / 已过期 / 已被使用 / 不匹配）统一返回错误提示，前端自动刷新验证码图片并清空输入框。
- 验证码内容用纯 JDK AWT 生成并保存在进程内存（不落库），有效期与一次性使用规则见用户服务 `application.yml` 的 `captcha.*` 配置。

## 短信验证码两步验证与发送记录

登录 / 注册采用「图片验证码 → 短信验证码」两步串行验证：

1. 页面加载时获取图片验证码（复用一次性 `captchaKey`）。
2. 用户输入图片验证码后点击「发送短信验证码」，前端把 `captchaKey` / `captchaValue` 连同目标手机号（注册）或用户名（登录）发送到匿名接口 `POST /api/user/auth/send-sms-code`；**只有图片验证码校验通过**，才真正发送 6 位数字验证码。
3. 发送成功后按钮进入 60 秒倒计时禁用状态；图片验证码一次性使用，每次成功发送后自动刷新。
4. 登录 / 注册请求体携带 `captchaKey` / `captchaValue` 与 `smsCode`；后端按「先校验图片验证码、再校验短信验证码」顺序执行。

关键约束：

- 防滥发依赖图片验证码 + 同一手机号 60 秒频控（超频返回 `code=429`）。
- 短信验证码以手机号为 key 存于进程内存，有效期 300 秒，一次性使用。
- 短信发送记录保存在进程内存列表（不落库），通过需 JWT 认证的 `GET /api/user/sms/records` 按时间倒序返回。
- `sms.enabled=false`（默认值）时不调用真实云片网接口，仅打印日志，验证码仍会生成存储以便本地联调。

## 秒杀流程

商品服务支持秒杀功能，核心流程：

1. **库存初始化**：`POST /api/product/products/seckill/init/{productId}` 将秒杀库存加载到 Redis。
2. **Lua 脚本预扣减**：`POST /api/product/products/seckill/{productId}` 通过 Redis Lua 脚本原子扣减库存，防止超卖。
3. **MQ 异步下单**：扣减成功后发送 `SeckillMessage` 到 RabbitMQ，订单服务 `SeckillOrderConsumer` 异步消费创建订单。
4. **防重复购买**：Redis 记录用户已购买标识，同一用户同一秒杀商品不可重复购买。

相关配置：

- Redis Lua 脚本：`ecommerce-product/src/main/resources/lua/seckill.lua`
- RabbitMQ 队列配置：`ecommerce-common/config/SeckillRabbitConfig.java`（公共模块统一提供）
- 秒杀消费者：`ecommerce-order/consumer/SeckillOrderConsumer.java`

## 全局主题切换

前端支持暗黑模式 / 亮色模式全局切换，主题偏好持久化到数据库：

- **默认主题**：暗黑模式（dark）
- **切换入口**：主应用导航栏右侧主题切换按钮（始终可见）
- **持久化策略**：
  - 客户端缓存：`localStorage` 存储 `demo_theme` 键值，保证页面刷新即时恢复
  - 数据库持久化：`users.theme_preference` 字段存储用户偏好，登录时自动同步
- **跨应用同步**：通过 qiankun 全局状态 + `MutationObserver` + `storage` 事件实现主应用与子应用实时同步

各前端应用的主题适配方式：

| 应用 | 技术栈 | 适配方式 |
|------|--------|----------|
| base-app | Vue 3 + Element Plus | `<html class="dark">` + Element Plus 暗黑 CSS 变量 |
| product-app | React 18 + Ant Design 5 | `ConfigProvider` + `theme.darkAlgorithm` / `theme.defaultAlgorithm` |
| order-app | 原生 TypeScript | CSS 变量（`--bg-primary` 等）+ 主题感知颜色计算 |

相关代码：

- 主题核心模块：`frontend/base-app/src/utils/theme.ts`
- 主题 API：`GET / PUT /api/user/users/theme`（用户服务）
- 数据库字段：`users.theme_preference VARCHAR(10) NOT NULL DEFAULT 'dark'`

## 网关鉴权

API 网关 `AuthGlobalFilter` 实现全局 JWT 鉴权：

- **白名单放行**：`/api/user/auth/login`、`/api/user/auth/register`、`/api/user/auth/captcha`、`/api/user/auth/send-sms-code`、`/api/user/health`，以及所有 Swagger 文档路径（`/api/*/swagger-ui/**`、`/api/*/v3/api-docs/**`）
- **Token 提取**：从 `Authorization: Bearer <token>` 头提取 JWT
- **校验与透传**：校验 JWT 签名与有效期，通过后从 Claims 提取 `userId` / `username`，写入 `X-User-Id` / `X-User-Name` 请求头透传给下游微服务
- **下游信任**：各微服务通过 `TrustedAuthenticationFilter` 读取网关注入的请求头，无需重复校验 JWT

## 异常处理

后端通过 `ecommerce-common` 的 `GlobalExceptionHandler`（`@RestControllerAdvice`）将各类异常统一包装为 `Result` 结构返回：

| 异常类型 | 触发场景 | `Result.code` | HTTP 状态 |
|---------|---------|---------------|----------|
| `BusinessException` | 业务校验（资源不存在 / 已存在 / 频控 / 秒杀校验等） | 400 / 404 / 409 / 429 / 502 | 200 |
| `MethodArgumentNotValidException` | `@Valid` 字段校验失败 | 400 | 200 |
| `ExpiredJwtException` | Token 过期 | 401 | 401（网关直接返回） |
| `JwtException` | Token 签名 / 格式非法 | 401 | 401（网关直接返回） |
| `AccessDeniedException` | 已登录但权限不足 | 403 | 200 |
| `Exception` | 不可预期的运行时异常 | 500 | 200 |

> 说明：经由 `GlobalExceptionHandler` 处理的异常 HTTP 状态恒为 200，真正的错误码体现在响应体的 `code` 字段；而 JWT 鉴权失败的 401 由网关 `AuthGlobalFilter` **直接写出真实 HTTP 401**。

## Docker 全栈部署

### 方式一：仅部署基础设施

使用 `docker-compose-infra.yml` 部署中间件，后端和前端单独启动：

```bash
docker compose -f docker-compose-infra.yml up -d
```

### 方式二：全栈部署

使用 `docker-compose.yml` 一键部署所有服务（基础设施 + 后端微服务 + 前端子应用）：

```bash
docker compose up -d
```

### 服务清单

| 序号 | 服务 | 镜像 / 构建 | 端口 | 用途 |
|------|------|------------|------|------|
| 1 | MySQL | `mysql:8.0` | 3306 | 关系型数据库（用户 / 商品 / 订单） |
| 2 | Redis | `redis:7-alpine` | 6379 | 缓存 / 会话 Token / 秒杀库存预扣 |
| 3 | Nacos | `nacos/nacos-server:v2.3.0` | 8848, 9848, 9849 | 微服务注册与配置中心 |
| 4 | RabbitMQ | `rabbitmq:3-management-alpine` | 5672, 15672 | 消息队列（秒杀订单异步处理） |
| 5 | Elasticsearch | `elasticsearch:8.11.0` | 9200, 9300 | 商品搜索引擎 |
| 6 | API 网关 | 构建自 `Dockerfile.gateway` | 8080 | Spring Cloud Gateway + Swagger 聚合 |
| 7 | 用户服务 | 构建自 `Dockerfile.user` | 8081 | 认证 / 用户管理 / 主题偏好 / Swagger |
| 8 | 商品服务 | 构建自 `Dockerfile.product` | 8082 | 商品 / 秒杀 / Swagger |
| 9 | 订单服务 | 构建自 `Dockerfile.order` | 8083 | 订单 / 秒杀消费 / Swagger |
| 10 | 前端主应用 | 构建自 `frontend/base-app/Dockerfile` | 80 | Nginx 托管 Vue 主应用（含全局主题切换） |
| 11 | 商品子应用 | 构建自 `frontend/product-app/Dockerfile` | 7100 | Nginx 托管 React 子应用（含暗黑模式适配） |
| 12 | 订单子应用 | 构建自 `frontend/order-app/Dockerfile` | 7200 | Nginx 托管原生 TS 子应用（含暗黑模式适配） |

### 访问地址

| 服务 | 地址 | 备注 |
|------|------|------|
| 前端主应用 | `http://服务器IP:80` | Nginx 托管，登录后自动加载子应用，支持主题切换 |
| 商品子应用 | `http://服务器IP:7100` | 由主应用 qiankun 加载，也可独立访问 |
| 订单子应用 | `http://服务器IP:7200` | 由主应用 qiankun 加载，也可独立访问 |
| API 网关 | `http://服务器IP:8080` | 统一 API 入口 |
| Swagger API 文档 | `http://服务器IP:8080/swagger-ui/index.html` | 网关聚合所有微服务 API 文档，无需登录 |
| Nacos 控制台 | `http://服务器IP:8848/nacos/` | 用户名 nacos / 密码 nacos |
| RabbitMQ 管理界面 | `http://服务器IP:15672/` | 用户名 guest / 密码 guest |
| Elasticsearch | `http://服务器IP:9200/` | 无认证 |

### 后端配置适配

部署完成后，各微服务的 `application.yml` 中中间件连接地址需指向 Docker 所在服务器 IP，涉及以下文件：

- `backend/ecommerce-user/src/main/resources/application.yml`
- `backend/ecommerce-product/src/main/resources/application.yml`
- `backend/ecommerce-order/src/main/resources/application.yml`
- `backend/ecommerce-gateway/src/main/resources/application.yml`
- `backend/ecommerce-gateway/src/main/resources/bootstrap.yml`

需要修改的配置项示例（以 MySQL 为例）：
```yaml
spring:
  datasource:
    url: jdbc:mysql://<Docker服务器IP>:3306/demo_fullstack?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
  data:
    redis:
      host: <Docker服务器IP>
  rabbitmq:
    host: <Docker服务器IP>
  cloud:
    nacos:
      discovery:
        server-addr: <Docker服务器IP>:8848
```

## 说明

- **微服务通信**：各微服务通过 Nacos 服务发现注册，网关通过路由规则将请求分发到对应微服务；商品服务与订单服务之间通过 OpenFeign 进行同步调用，秒杀场景通过 RabbitMQ 异步解耦。
- **数据持久化**：MySQL 存储用户、商品、订单核心数据；Redis 存储会话 Token、秒杀库存、验证码等热点数据；Elasticsearch 存储商品搜索索引。
- **认证链路**：登录返回 JWT → 前端存储到 localStorage → 每次请求携带 `Authorization: Bearer <token>` → 网关校验 JWT 并透传用户信息 → 下游微服务信任网关注入的请求头。
- **前端微前端**：主应用（base-app）通过 qiankun 注册并加载商品子应用（product-app）和订单子应用（order-app），子应用可独立开发、独立部署、独立运行。
- **图片验证码与短信频控**：均为进程内内存实现（重启后失效 / 重置），未做分布式持久化；如需多实例共享需替换为 Redis 存储方案。
- **前端 Element Plus / Ant Design**：均采用完整引入方式（简单可靠）；生产环境可改用按需引入插件优化包体积。
- **Swagger API 文档**：各微服务集成 `springdoc-openapi`（网关用 `webflux-ui`，业务服务用 `webmvc-ui`），网关聚合所有下游服务文档，Swagger 路径已加入鉴权白名单。
- **全局主题切换**：默认暗黑模式，支持亮色/暗黑切换，主题偏好通过数据库持久化；Element Plus 通过 `class="dark"` + CSS 变量适配，Ant Design 通过 `ConfigProvider` 主题算法切换，原生 HTML 子应用通过 CSS 变量 + 主题感知颜色适配。
- **前端测试体系**：三个子应用均使用 Vitest 进行单元测试，E2E 测试使用 Playwright 覆盖登录、商品搜索、订单流程、微前端加载、主题同步等核心场景；根 `package.json` 提供统一测试脚本（`test:unit` / `test:e2e` / `test:all`）。
- **后端测试体系**：三个业务微服务均提供 JUnit 5 单元测试，覆盖 controller / service / repository / entity 各层，使用 Mockito 隔离依赖。
- **前端埋点**：product-app 内置 `tracker.ts` 埋点工具，支持页面访问（PageView）、商品曝光（ItemExpose，基于 IntersectionObserver）、交互点击（Click）三类事件，上报批处理异步发送，异常静默捕获。
- **机票商品与航线可视化**：新增 `flight` 类商品类型与 `flights` 表，后端提供航班 CRUD 及 `/flights/frequency` 频率统计接口；Admin 端（base-app）以 ECharts 飞线图展示城市间航线频率；用户端（product-app）机票详情页支持 2D（ECharts）/3D（Cesium）地图切换、飞机视角飞行动画与实时飞行信息面板（依赖 echarts、cesium、vite-plugin-cesium）。
