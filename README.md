# qoder-fullstack

前后端分离的微服务电商示例项目，基于 Spring Cloud 微服务架构 + qiankun 微前端架构：

- **backend/**：Spring Boot 3.3.x + Java 17 + Maven + Spring Cloud + Spring Cloud Alibaba，拆分为 5 个微服务模块
  - `ecommerce-common` — 公共模块（统一响应 Result / 业务异常 / 全局异常处理 / 秒杀消息体 / 网关身份签名工具）
  - `ecommerce-gateway` — API 网关（Spring Cloud Gateway，JWT 全局鉴权 + Redis 会话校验，按路径路由到下游微服务，聚合 Swagger 文档）
  - `ecommerce-user` — 用户服务（注册 / 登录 / 图片验证码 / 短信验证码 / 用户 CRUD / 会话管理 / 主题偏好）
  - `ecommerce-product` — 商品服务（商品 CRUD / Elasticsearch 搜索 / 秒杀库存预扣 / Lua 脚本 / 机票航班 CRUD 与航线频率统计 / 商品图片上传 OSS）
  - `ecommerce-order` — 订单服务（订单 CRUD / 秒杀订单异步消费 / OpenFeign 调用商品服务 / 支付宝沙箱支付）
- **ai-service/**：Python 3.11 + FastAPI + LangChain/LangGraph + Milvus 的保险条款问答微服务（PDF 知识库构建 / SSE 流式回答 / 会话与检查点回溯）
- **frontend/**：基于 qiankun 微前端框架，拆分为 3 个子应用
  - `base-app` — Vue 3 + TypeScript + Vite + Element Plus，主应用（登录 / 注册按角色分流 / 用户管理 / 子应用容器 / 全局主题切换 / Admin 航线频率地图 / 商品图片管理 / 被挤下线感知）
  - `product-app` — React 18 + TypeScript + Vite + Ant Design，商品子应用（商品列表 / 商品详情 / 秒杀 / 保险咨询机器人抽屉 / Markdown 消息渲染 / 暗黑模式适配 / 用户行为埋点 / 机票详情 2D·3D 航线可视化）
  - `order-app` — 原生 TypeScript + Vite + Axios，订单子应用（我的订单 / 全部订单 / 订单详情 / 支付宝支付跳转与结果页轮询 / 暗黑模式适配）

## 架构概览

```
                        ┌────────────────────────────────────────────────┐
                        │             Spring Cloud Gateway               │
                        │  (port 8080, JWT 鉴权 + Redis 会话, Swagger 聚合) │
                        └──┬────────────┬────────────┬──────────────┬───┘
                           │            │            │              │
          /api/user/**     │ /api/product/**         │ /api/order/**│ /api/ai/**
                           │            │            │              │
              ┌────────────┴──┐ ┌───────┴─────┐ ┌────┴────────┐ ┌───┴──────────┐
              │ ecommerce-user│ │ecommerce-   │ │ecommerce-   │ │ ai-service   │
              │  (port 8081)  │ │ product     │ │ order       │ │ (port 8084)  │
              │  + Swagger UI │ │(port 8082)  │ │(port 8083)  │ │ FastAPI + RAG│
              └───────┬───────┘ │+ Swagger UI │ │+ Swagger UI │ └──────┬───────┘
                      │         └──────┬──────┘ └──────┬──────┘        │
              ┌───────┴────────────────┴───────────────┴───────────────┴────┐
              │                   中间件服务（Docker）                       │
              │  MySQL 8.0 · Redis 7 · Nacos v2.3.0                        │
              │  RabbitMQ 3 · Elasticsearch 8.11.0 · Milvus v2.4.15        │
              └────────────────────────────────────────────────────────────┘

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
qoder-fullstack/
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
│   │       │   ├── controller/               # OrderController / PaymentController（含 OpenAPI 注解）
│   │       │   ├── service/                  # 订单服务 + 支付服务（PaymentService / PaymentStateManager）
│   │       │   │   ├── alipay/               # AlipayProperties 密钥工具与客户端工厂
│   │       │   │   └── impl/AlipayPaymentServiceImpl.java  # 创建支付 / 异步通知 / 主动查询
│   │       │   ├── entity/                   # Order / PaymentRecord 实体
│   │       │   ├── repository/               # MyBatis Mapper 访问层（含 PaymentRecordMapper + XML）
│   │       │   ├── client/                   # ProductClient（OpenFeign 调用商品服务）
│   │       │   ├── consumer/                 # SeckillOrderConsumer（RabbitMQ 秒杀消息消费者）
│   │       │   ├── config/                   # AlipayProperties（alipay.* 配置项）
│   │       │   └── test/                     # JUnit 5 单元测试（11 个测试类，含支付回调验签/幂等）
│   │       └── resources/
│   │           ├── application.yml           # 端口 8083，context-path /order（含 alipay.* 配置）
│   │           ├── db/schema.sql             # 订单表 + 支付流水表建表 SQL
│   │           └── mapper/                   # OrderMapper.xml、PaymentRecordMapper.xml
│   ├── Dockerfile.gateway                    # 网关 Docker 构建文件
│   ├── Dockerfile.user                       # 用户服务 Docker 构建文件
│   ├── Dockerfile.product                    # 商品服务 Docker 构建文件
│   ├── Dockerfile.order                      # 订单服务 Docker 构建文件
│   ├── run-order.ps1 / run-product.ps1       # 本地服务启动脚本
│   └── start-order.bat
├── ai-service/                               # 保险咨询机器人（Python + FastAPI，端口 8084）
│   ├── app/
│   │   ├── main.py                           # FastAPI 入口（建表 + Milvus 集合初始化 + 路由挂载）
│   │   ├── config.py                         # pydantic-settings，读取进程环境 / ai-service/.env / 根 .env
│   │   ├── deps.py                           # 身份依赖（X-User-*，可选 HMAC 验签）与管理员校验
│   │   ├── db.py / models.py                 # SQLAlchemy：ai_insurance_document / ai_chat_session / ai_chat_message
│   │   ├── api/chat.py                       # POST /chat（SSE 流式问答）
│   │   ├── api/sessions.py                   # 会话 CRUD + 检查点列表/回溯
│   │   ├── api/documents.py                  # PDF 上传 / 列表 / 重建 / 删除（写操作仅 ADMIN）
│   │   └── rag/                              # MinerU 解析 / 切分入库 / 百炼 Embedding / Milvus / LangGraph / 提示词
│   ├── scripts/make_sample_pdfs.py           # 生成示例条款 PDF
│   ├── samples/                              # 示例保险条款 PDF（验收用）
│   ├── tests/                                # pytest（切分 / 检索门控 / SSE / 会话隔离 / 检查点）
│   ├── requirements.txt / pytest.ini / run.ps1
│   └── README.md                             # 服务级详细说明（环境变量、Milvus 部署、验收清单）
├── frontend/                                 # qiankun 微前端
│   ├── base-app/                             # 主应用（Vue 3 + Element Plus）
│   │   ├── src/
│   │   │   ├── main.ts                       # 入口（注册 Element Plus、路由、qiankun 启动、主题初始化）
│   │   │   ├── App.vue                       # 根组件（布局 + 子应用容器 + 主题切换按钮）
│   │   │   ├── micro/index.ts                # qiankun 子应用注册与全局状态管理
│   │   │   ├── api/                          # axios 封装 + 认证 / 用户 / 短信 / 航班接口（flight.ts）/ 按角色首页解析
│   │   │   ├── router/                       # vue-router（按角色分流 + 管理员页面守卫 + 子应用路由占位）
│   │   │   ├── utils/                        # 令牌与登录用户状态管理（auth.ts，含 isAdminUser / resolveHomePath）+ 主题管理（theme.ts）
│   │   │   ├── components/ImageUploader.vue   # 商品图片上传组件（类型/大小预校验 + 自定义上传）
│   │   │   ├── __tests__/                    # Vitest 单元测试（api（含 flight）/ router / utils）
│   │   │   └── views/
│   │   │       ├── Login.vue                 # 登录页（图片验证码 + 短信验证码两步验证 + 登录后按角色跳转）
│   │   │       ├── Register.vue              # 注册页（手机号 + 图片验证码 + 短信验证码两步验证）
│   │   │       ├── UserManage.vue            # 用户管理页面（增删改查 + 短信发送记录查看）
│   │   │       ├── ProductImageManage.vue    # 商品图片管理（选商品 → 上传 → 回写 imageUrl）
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
│   │   │   ├── api/insuranceAgent.ts         # 保险咨询 API 客户端（会话/知识库/SSE 解析与流式消费）
│   │   │   ├── components/                   # 航线可视化组件（FlightMap2D/3D、FlightInfoPanel、FlightAnimationController）
│   │   │   │   └── insurance/                # 保险咨询抽屉组件（Drawer / ChatPanel / KnowledgePanel / CheckpointPanel / MarkdownMessage）
│   │   │   ├── public/models/plane.glb       # Cesium 3D 飞机模型
│   │   │   └── pages/
│   │   │       ├── ProductList.tsx           # 商品列表页（含 IntersectionObserver 曝光埋点 + 机票/保险分类入口）
│   │   │       ├── ProductDetail.tsx         # 商品详情页（含秒杀入口、保险咨询机器人入口与抽屉）
│   │   │       └── FlightDetail.tsx          # 机票详情页（2D/3D 地图切换 + 飞机视角动画）
│   │   ├── vite.config.ts                    # Vite 配置（端口 7100，/api 代理到网关 8080）
│   │   ├── vitest.config.ts                  # Vitest 单元测试配置
│   │   └── package.json
│   ├── order-app/                            # 订单子应用（原生 TypeScript）
│   │   ├── src/
│   │   │   ├── main.ts                       # 入口（qiankun 生命周期导出 + 独立运行支持 + 主题同步）
│   │   │   ├── app.ts                        # OrderApp 类（订单列表 / 详情 / 取消 / 去支付 / 支付结果页 / 轮询）
│   │   │   ├── payment.ts                    # 支付纯逻辑（结果页路径与订单号解析、状态映射）+ axios 调用
│   │   │   └── route.ts                      # 轻量路由解析（/order/pay-result、/order/detail/{orderNo}）
│   │   ├── __tests__/                        # Vitest 单元测试（app / i18n / payment / route）
│   │   ├── vite.config.ts                    # Vite 配置（端口 7200，/api 代理到网关 8080）
│   │   ├── vitest.config.ts                  # Vitest 单元测试配置
│   │   └── package.json
│   └── e2e/                                  # Playwright 端到端测试
│       ├── fixtures/test-data.ts             # 测试数据夹具
│       ├── helpers.ts                        # 通用测试辅助工具
│       ├── mocks/                            # 接口 Mock（auth / order（含支付）/ product / user / flight）
│       ├── login.spec.ts                     # 登录流程 E2E 测试
│       ├── product-search.spec.ts            # 商品搜索 E2E 测试
│       ├── flight-flow.spec.ts               # 机票浏览与详情 E2E 测试
│       ├── order-flow.spec.ts                # 订单流程 E2E 测试
│       ├── payment-flow.spec.ts              # 支付宝支付 E2E 测试（立即购买 / 去支付 / 503 / 秒杀不受影响）
│       ├── micro-frontend.spec.ts            # 微前端加载 E2E 测试
│       ├── theme-sync.spec.ts                # 主题同步 E2E 测试
│       ├── playwright.config.ts              # Playwright 配置（自动启动三个子应用 dev server）
│       └── package.json
├── init-sql/
│   ├── 01-init-database.sql                  # 数据库初始化脚本（建库 + 建表 + 演示数据）
│   ├── 02-alipay-payment.sql                 # 支付宝支付流水表（可重复执行）
│   └── 03-insurance-agent.sql                # 保险商品演示数据 + ai_* 三张表（可重复执行）
├── docker-compose.yml                        # 全栈 Docker 编排（基础设施 + 后端微服务 + 前端子应用）
├── docker-compose-infra.yml                  # 仅基础设施 Docker 编排（MySQL / Redis / Nacos / RabbitMQ / ES）
├── docker-compose.milvus.yml                  # Milvus 单机版编排（etcd + minio + milvus-standalone，部署在 VM）
├── scripts/                                  # 运维脚本（deploy-milvus.ps1 / inspect-milvus.py / milvus-console.py / smoke-milvus.py）
├── package.json                              # 根 package.json（统一前端单元测试 / E2E 测试脚本）
├── .env.example                              # 环境变量模板（支付宝 / 智谱 / MinerU / 百炼 / Milvus / OSS）
└── vm-ssh.js                                 # 远程服务器 SSH 工具脚本
```

## 环境要求

- JDK 17+
- Maven 3.6+（推荐使用项目自带的 Maven Wrapper `mvnw`）
- Node.js 18+（推荐 20 LTS）
- Python 3.11 / 3.12（仅 ai-service 需要）
- MySQL 8.x
- Redis 7.x
- Nacos v2.3.0
- RabbitMQ 3.x（含 Management 插件）
- Elasticsearch 8.11.0
- Milvus v2.4.15（仅保险咨询机器人需要，可用 `docker-compose.milvus.yml` 单独部署）

## 数据库初始化

后端启动前，需要先创建数据库和表。推荐使用项目根目录的初始化脚本 [`init-sql/01-init-database.sql`](init-sql/01-init-database.sql)，包含：

- 创建数据库 `qoder_fullstack`（utf8mb4）
- `users` 表 — 用户数据（含审计字段 `created_by / created_date / last_modified_by / last_modified_date`，主题偏好 `theme_preference`）
- `products` 表 — 商品数据（含秒杀字段 `is_seckill / seckill_price / seckill_start_time / seckill_end_time`；机票类商品 `category = 'flight'`）
- `flights` 表 — 航班数据（机票商品特有字段：`product_id`、`departure_city / arrival_city`、经纬度、`flight_number`、`duration`、`frequency`、`route_coordinates` JSON，通过 `product_id` 关联 `products`）
- `orders` 表 — 订单数据（状态流转使用 `OrderStatus` 枚举：`0待支付 → 1已支付 → 2已发货 → 3已完成 → 4已取消`，MyBatis 通过自定义 TypeHandler 自动转换）
- `payment_record` 表 — 支付宝支付流水（见 [`init-sql/02-alipay-payment.sql`](init-sql/02-alipay-payment.sql)：唯一键 `(channel, order_no)` 与 `trade_no` 保证重复创建支付 / 重复通知不会产生重复流水）
- `ai_insurance_document` / `ai_chat_session` / `ai_chat_message` 表 — 保险咨询机器人的知识库文档、会话与消息（见 [`init-sql/03-insurance-agent.sql`](init-sql/03-insurance-agent.sql)；ai-service 启动时也会用 SQLAlchemy `create_all` 自动建表）
- 保险类商品 — 3 个 `category = 'insurance'` 的演示商品（安心百万医疗险 / 守护重疾险 / 出行无忧意外险）
- 演示数据 — 3 个用户（admin / zhangsan / lisi，密码均为 `123456`）+ 6 个普通商品（含 3 个秒杀商品）+ 8 个机票商品及对应 8 条航班数据

```bash
# 方式一：Docker 部署时自动执行（挂载 init-sql 目录到 /docker-entrypoint-initdb.d）
#         脚本开头的 SET NAMES utf8mb4 已保证容器内客户端按 UTF-8 解释，无需额外参数
# 方式二：手动执行（必须带 --default-character-set=utf8mb4，
#         否则 mysql 客户端默认 latin1，会把脚本里的中文二次编码成乱码）
mysql -u root -p --default-character-set=utf8mb4 < init-sql/01-init-database.sql

# 支付宝支付流水表（可重复执行，已存在则跳过）
mysql -u root -p --default-character-set=utf8mb4 < init-sql/02-alipay-payment.sql

# 保险商品 + 保险咨询机器人表（可重复执行，已存在则跳过）
mysql -u root -p --default-character-set=utf8mb4 < init-sql/03-insurance-agent.sql
```

各微服务的 `application.yml` 中 `spring.datasource.password` 需从占位符改为本机 MySQL 的真实密码（该配置仅供本地开发使用，不要把真实密码提交到仓库）。

## 后端启动

### 1. 启动基础设施

确保 MySQL、Redis、Nacos、RabbitMQ、Elasticsearch 已启动。可使用 Docker 一键部署：

```bash
# 仅启动基础设施
docker compose -f docker-compose-infra.yml up -d
```

> 保险咨询机器人额外需要 Milvus 向量库（etcd + minio + milvus-standalone）：`docker compose -f docker-compose.milvus.yml up -d`（默认部署在 VM，可用 `scripts/deploy-milvus.ps1` 远程部署），并用 `Test-NetConnection <host> -Port 19530` 确认 gRPC 端口可达。运维辅助脚本：`scripts/smoke-milvus.py`（连通性自检）、`scripts/inspect-milvus.py`（集合/分区检查）、`scripts/milvus-console.py`（向量查询控制台）。

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
| 订单服务 | 8083 | /order | 订单管理 / 秒杀订单消费 / 支付宝支付（沙箱） |
| 保险咨询机器人 ai-service | 8084 | / | PDF 知识库构建 / SSE 流式问答 / 会话与检查点（Python FastAPI，经网关 `/api/ai/**` 转发） |
| Milvus 向量库 | 19530 / 9091 | — | 保险条款向量检索（默认部署在 VM，`docker-compose.milvus.yml`） |

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

### 5. 启动保险咨询机器人（可选）

仅当需要使用「保险咨询机器人」时启动。需先在根目录 `.env` 填好 `ZHIPU_API_KEY` / `DASHSCOPE_API_KEY` / `MINERU_API_TOKEN` / `MILVUS_URI`（模板见 [`.env.example`](.env.example)），并在 `ai-service/README.md` 确认 Milvus 可达。

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\run.ps1
# 等价于 .\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8084
```

启动时会自动创建 `ai_*` 三张表并初始化 Milvus 集合（Milvus 不可达仅告警，服务仍可启动以便先联调前端）。健康检查：`GET http://localhost:8084/health` → `{"status":"ok"}`。

> 推荐 Python 3.11 / 3.12；3.13 下 `pymilvus` / `langgraph` 如遇安装或运行异常，请改用 3.11 创建虚拟环境。

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
| `payment-flow.spec.ts` | 支付宝支付链路（立即购买 / 去支付 / 503 降级 / 秒杀不受影响） |
| `micro-frontend.spec.ts` | qiankun 子应用加载与切换 |
| `theme-sync.spec.ts` | 全局主题同步（暗黑/亮色） |

运行命令：

```bash
# 首次运行前安装浏览器（仅需一次）
npx --prefix frontend/e2e playwright install chromium

# 运行 E2E 测试（自动启动三个子应用 dev server）
npm run test:e2e

# 只跑某个用例文件
npm run test:e2e -- payment-flow.spec.ts

# 打开 Playwright UI 模式（可视化调试）
npm run test:e2e:ui
```

### 保险咨询微服务测试（pytest）

`ai-service/tests/` 覆盖切分参数与中文分隔符、检索无命中/低分走拒答且不调用 LLM、上传非 PDF / 超限 / 非管理员被拒、会话跨用户 404、SSE 帧格式与消息落库、检查点回溯 409 与成功分支。

```powershell
cd ai-service
.\.venv\Scripts\python.exe -m pip install pytest pytest-asyncio
.\.venv\Scripts\python.exe -m pytest tests -q
```

product-app 侧的保险咨询组件（`insuranceAgent` API / `ChatPanel` / `MarkdownMessage` / `ProductDetail` 保险入口 / 中英文案 key）同样由 Vitest 覆盖，随 `npm run test:unit:product` 执行。

### 后端单元测试（JUnit 5）

三个业务微服务均提供 JUnit 5 单元测试，覆盖 controller、service、repository、entity 各层：

| 微服务 | 测试类数量 | 主要测试内容 |
|--------|-----------|--------------|
| ecommerce-user | 9 个测试类 | 认证控制器、用户控制器、验证码/短信服务、全局异常处理 |
| ecommerce-product | 7 个测试类 | 商品控制器、商品服务、秒杀库存服务、仓储层；航班 FlightController/Service/Repository |
| ecommerce-order | 11 个测试类 | 订单控制器、订单服务、订单状态枚举、Feign 客户端、仓储层；支付控制器 / 支付服务（回调验签、幂等、异常分支）/ 支付状态流转 / 密钥工具 / 流水仓储 |

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
| POST | /api/product/files/images | 上传商品图片（multipart `file`，≤5MB，JPG/PNG/WEBP/GIF） | JWT + ADMIN |
| DELETE | /api/product/files/images | 按 `objectKey` 删除图片对象 | JWT + ADMIN |
| GET | /api/product/files/local/** | 读取本地兜底存储的图片（浏览器 img 直链） | 匿名（网关白名单） |
| PUT | /api/product/products/{id}/image | 仅更新商品图片 URL（传空字符串表示清除） | JWT + ADMIN |

### 订单服务（/api/order/**）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | /api/order/orders | 创建订单 | JWT |
| GET | /api/order/orders/my | 查询当前用户的订单列表 | JWT |
| GET | /api/order/orders/{orderNo} | 根据订单号查询订单 | JWT |
| GET | /api/order/orders | 查询所有订单（管理用） | JWT |
| POST | /api/order/orders/{orderId}/cancel | 取消订单 | JWT |
| PUT | /api/order/orders/{orderId}/status | 更新订单状态（参数：0-4） | JWT |
| POST | /api/order/payments/alipay/{orderNo} | 创建支付宝支付，返回收银台 `payUrl`（未配置时业务码 503） | JWT + 订单归属 |
| GET | /api/order/payments/alipay/{orderNo}/status | 查询支付状态（`sync=true` 时主动向支付宝查询并同步，默认 true） | JWT + 订单归属 |
| POST | /api/order/payments/alipay/notify | 支付宝异步通知（验签 + 金额/订单号校验 + 幂等），返回纯文本 `success`/`failure` | 支付宝验签（白名单） |

### 保险咨询机器人（/api/ai/**）

网关 `Path=/api/ai/**` + `StripPrefix=2` 转发到 ai-service（8084），即 `/api/ai/chat` → `http://localhost:8084/chat`；网关校验 JWT 后注入 `X-User-Id` / `X-User-Name` / `X-User-Role`（及可选 HMAC 签名），ai-service 自身不解析 JWT。

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | /api/ai/chat | SSE 流式问答，body `{sessionId, productId, message}` | JWT |
| POST | /api/ai/sessions | 新建会话（`productId`） | JWT |
| GET | /api/ai/sessions?productId= | 当前用户在该商品下的会话列表 | JWT |
| GET | /api/ai/sessions/{id}/messages | 历史消息 | JWT + 会话属主 |
| DELETE | /api/ai/sessions/{id} | 删除会话与消息 | JWT + 会话属主 |
| GET | /api/ai/sessions/{id}/checkpoints | 检查点列表（回溯入口） | JWT + 会话属主 |
| POST | /api/ai/sessions/{id}/restore | 回溯到指定 `checkpointId` | JWT + 会话属主 |
| POST | /api/ai/documents/upload | 上传保险条款 PDF（multipart：`productId` + `file`，≤20MB） | JWT + ADMIN |
| GET | /api/ai/documents?productId= | 文档列表与解析状态（processing / ready / failed） | JWT |
| POST | /api/ai/documents/{id}/reindex | 重新解析入库 | JWT + ADMIN |
| DELETE | /api/ai/documents/{id} | 删除文档、向量与文件 | JWT + ADMIN |

SSE 事件序列：`meta`（回传 sessionId）→ `token`（文本增量）→ `done`（`refused` + `sources`）或 `error`。

## 支付宝沙箱支付

商品详情页「立即购买」与订单列表「去支付」均走支付宝电脑网站支付（page pay）沙箱，链路：

1. 前端创建订单（`POST /api/order/orders`）；
2. 前端请求 `POST /api/order/payments/alipay/{orderNo}` 拿到 `payUrl`，`window.location.assign(payUrl)` 整页跳转收银台；
3. 用户在沙箱收银台付款，支付宝回调 `notify_url`（异步）并同步回跳 `return_url`（`/order/pay-result`）；
4. 支付结果页每 2 秒轮询 `status` 接口（`sync=true`），命中终态即停止；即使 `notify_url` 不可达也能通过主动查询确认结果；
5. 订单状态仅在验签通过、`app_id` / 订单号 / 金额均匹配时由 `待支付(0)` 条件更新为 `已支付(1)`，重复通知不会二次变更。

### 环境变量

未配置任何变量时服务仍可正常启动，仅创建支付返回业务码 `503`（前端提示后可稍后在订单详情重试）。

| 变量 | 必填 | 说明 |
|------|------|------|
| `ALIPAY_APP_ID` | 是 | 沙箱应用 APPID |
| `ALIPAY_PRIVATE_KEY` | 是 | 应用私钥（PKCS8，支持单行 Base64 / 含 `\n` / PEM 头尾） |
| `ALIPAY_PUBLIC_KEY` | 是 | 支付宝公钥（沙箱控制台「支付宝公钥」，非应用公钥） |
| `ALIPAY_GATEWAY_URL` | 否 | 默认 `https://openapi-sandbox.dl.alipaydev.com/gateway.do` |
| `ALIPAY_NOTIFY_URL` | 建议 | 异步通知地址，必须是支付宝服务器可达的公网地址；为空时仅依赖主动查询 |
| `ALIPAY_RETURN_URL` | 否 | 同步回跳地址，默认 `http://localhost:5173/order/pay-result` |
| `ALIPAY_SIGN_TYPE` | 否 | 默认 `RSA2` |
| `ALIPAY_TIMEOUT_EXPRESS` | 否 | 订单支付超时，默认 `30m` |

本地开发若需要真实异步通知，可用内网穿透把公网地址指到网关：

```text
ALIPAY_NOTIFY_URL=https://<隧道域名>/api/order/payments/alipay/notify
```

> 网关已将 `/api/order/payments/alipay/notify` 加入鉴权白名单（支付宝回调不带 JWT）；创建支付 / 查询接口仍走 JWT。
> 密钥一律通过环境变量注入，**禁止写入仓库**；根目录提供 [`.env.example`](.env.example) 占位模板，复制为 `.env` 后填写（`.env` 已在 `.gitignore` 中）。

### 沙箱验证步骤

1. 登录支付宝开放平台「沙箱控制台」，获取 `APPID`，用官方工具生成 RSA2 密钥对并上传应用公钥，复制「支付宝公钥」与沙箱买家账号。
2. 导出环境变量（PowerShell 示例）：

```powershell
$env:ALIPAY_APP_ID="2021xxxxxxxxxxxx"
$env:ALIPAY_PRIVATE_KEY="MIIEv..."
$env:ALIPAY_PUBLIC_KEY="MIIBIj..."
$env:ALIPAY_NOTIFY_URL="https://<公网地址>/api/order/payments/alipay/notify"
$env:ALIPAY_RETURN_URL="http://localhost:5173/order/pay-result"
```

3. 执行 `init-sql/02-alipay-payment.sql`，启动订单服务；日志出现「支付宝沙箱支付已启用」即为配置成功（未配置时为「支付宝支付未配置」告警）。
4. 启动三个前端 → 登录 → 商品详情 → 立即购买 → 跳转沙箱收银台 → 用沙箱买家账号付款。
5. 回到 `/order/pay-result?orderNo=...`：结果页展示支付成功；订单列表 / 详情状态由「待支付」变为「已支付」；刷新或重复回调不会二次变更。
6. 负向验证：临时改错 `ALIPAY_PUBLIC_KEY` → 回调返回 `failure` 且日志输出 `[ALIPAY-ALARM] 验签失败`，订单状态不变；清空所有 `ALIPAY_*` → 服务正常启动，创建支付返回 `code=503`。

> 同步回跳（`return_url`）只代表用户回到了本站，真正的支付结果以异步通知或主动查询为准；两者共用同一幂等状态流转。

## 商品图片上传（阿里云 OSS / 本地兜底）

商品图片由后端代理上传（前端 → 网关 → 商品服务 → OSS），不暴露任何存储凭证：

1. **存储路由**：`FileStorageServiceRouter` 在启动时根据 `oss.isConfigured()` 选择实现——配置了 `bucket` 与 AK/SK 时写阿里云 OSS；未配置（本地开发/测试）自动写本地磁盘（默认 `ecommerce-product/uploads`），由 `GET /api/product/files/local/**` 静态映射对外提供，返回的相对 URL 经网关白名单读取，无需硬编码主机端口。
2. **三重校验**：文件大小（默认 ≤ 5MB，与 `spring.servlet.multipart.max-file-size` 一致）、MIME 类型（JPG/PNG/WEBP/GIF）、文件头魔数。
3. **对象 Key**：`{oss.dir}/yyyy/MM/dd/{uuid}.{ext}`，删除前校验 Key 前缀防越权。
4. **回写接口**：`PUT /api/product/products/{id}/image` 只更新 `image_url` 并同步 Elasticsearch，避免整实体 PUT 导致字段丢失。
5. **管理入口**：base-app「商品图片管理」页（`/admin/product-images`）选商品 → 上传 → 回写；nginx 已放开 `client_max_body_size 6m`。

| 变量 | 说明 |
|------|------|
| `OSS_ENABLED` | 总开关，默认 `true` |
| `OSS_ENDPOINT` / `OSS_BUCKET` | 地域 Endpoint（默认 `oss-cn-hangzhou.aliyuncs.com`）与 Bucket（需公有读或配合 CDN） |
| `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | 仅通过环境变量注入，**禁止写入仓库**；为空时自动走本地存储 |
| `OSS_DIR` | 对象 Key 前缀，默认 `product` |
| `OSS_PUBLIC_URL_PREFIX` | CDN/自定义域名前缀，留空则用 `https://{bucket}.{endpoint}` |
| `OSS_LOCAL_DIR` / `OSS_LOCAL_URL_PREFIX` | 本地兜底目录（默认 `uploads`）与对外路径前缀（默认 `/api/product/files/local`） |

## 保险商品与保险咨询机器人

保险类商品（`products.category = 'insurance'`）在商品详情页展示「保险咨询机器人」入口，以抽屉形式提供页签：**对话**（SSE 流式问答 + Markdown 渲染 + 参考片段）、**回溯**（会话检查点恢复）、**知识库**（仅管理员可见：上传/重建/删除条款 PDF）。

### 知识库构建链路

`MinerU v4 解析（失败降级 pypdf）→ clean_markdown → RecursiveCharacterTextSplitter(chunk_size=800 / overlap=120，含中文标点分隔符) → 百炼 text-embedding-v4（1024 维）→ Milvus 集合 insurance_chunks（COSINE + AUTOINDEX，按 product_id 过滤检索）`

### 回答范围与拒答

- LangGraph 图：`retrieve → generate | refuse`；检索无命中或最高相似度低于 `RAG_MIN_SCORE`（默认 0.35）时直接输出固定拒答话术，**不调用大模型**（可复现、不编造）。
- `generate` 用 `glm-5.3` 流式回答，系统提示词约束「只使用文档片段、标注来源、片段不足必须原样输出拒答话术、禁止使用自身知识推测」。
- 多轮上下文与回溯由 `InMemorySaver` 检查点维护，`thread_id = u{userId}:p{productId}:s{sessionId}`；检查点存于进程内存，**服务重启后回溯返回 409**，已落库消息仍可在「对话」页查看。

### 权限与会话隔离

- 上传 / 重建 / 删除文档：仅 `ADMIN`（由网关注入的 `X-User-Role` 判定）；
- 会话与消息按 `user_id` 隔离，跨用户访问返回 404；
- 前端 `product-app` 只渲染接口返回内容：`react-markdown` + `remark-gfm` / `remark-breaks`，原始 HTML 被转义为文本以防 XSS，流式追加时按消息 memo 避免全量重渲染。

### 环境变量

全部变量见 [`.env.example`](.env.example)：「智谱 GLM / MinerU / 百炼 Embedding / Milvus / RAG 参数」与「网关身份头签名（`GATEWAY_JWT_SECRET` + `VERIFY_GATEWAY_SIGNATURE`）」、「LangSmith 追踪（可选）」。配置加载顺序：进程环境变量 > `ai-service/.env` > 仓库根 `.env`。

> 服务级详细说明（目录结构、Milvus 部署、接口清单、联调验收清单、常见问题）见 [`ai-service/README.md`](ai-service/README.md)。

### 联调验收步骤

1. 启动 Milvus、ai-service（8084）、网关（8080）与三个前端；
2. 管理员登录 → 商品列表筛选「保险」→ 进入详情 → 点「保险咨询机器人」；
3. 在「知识库」上传 `ai-service/samples/安心百万医疗险条款.pdf`，状态由 `processing` 变 `ready` 且片段数 > 0；
4. 就条款内容提问（如「等待期多久？」）→ 逐字流式回答，可展开参考片段；
5. 问无关问题（如「今天天气如何？」）→ 固定拒答话术；
6. 普通用户 A/B 会话互不可见；「回溯」页签可恢复历史检查点。

## 登录角色分流与被挤下线感知

- **按角色分流**：`base-app/src/utils/auth.ts` 的 `resolveHomePath()` 决定落地页——`ADMIN` → 用户管理页 `/users`，普通用户 → 商品中心 `/product`；路由守卫对 `meta.adminOnly` 页面再拦一层，普通用户直接回到 `/product`，不再一进管理页就吃到 403。
- **被挤下线**：用户服务将 `login:token:{uid}` 写入 Redis，网关每次请求比对；不一致返回 HTTP 401 + 业务码 `40101`。前端响应拦截器识别 `40101` → 清本地登录态 → `replace` 跳登录页（不保留 `redirect`，避免登录后又被顶回旧页），并提示「账号已在其他设备登录」。
- **主动感知**：`App.vue` 登录后每 30 秒静默调用一次 `GET /api/user/auth/me` 作为心跳（页面隐藏时自动跳过），并在 `visibilitychange` 重新可见时立即补检，使被挤下线在无业务请求时也能及时生效。
- **知识库文档**：`.qoder/repowiki/zh/content/`（wiki）与 `.qoder/repowiki/knowledge/zh/`（知识卡）已同步本次新增能力的页面与卡片，新增主题包括 AI 问答微服务、Milvus、支付宝沙箱支付、商品图片上传、保险咨询机器人。

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
  - 客户端缓存：`localStorage` 存储 `qoder_theme` 键值，保证页面刷新即时恢复
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

- **白名单放行**：`/api/user/auth/login`、`/api/user/auth/register`、`/api/user/auth/captcha`、`/api/user/auth/send-sms-code`、`/api/user/health`、`/api/order/payments/alipay/notify`（支付宝回调验签），以及所有 Swagger 文档路径（`/api/*/swagger-ui/**`、`/api/*/v3/api-docs/**`）与本地图片读取路径（`/api/product/files/local/**`）
- **内部接口封禁**：`/api/product/products/deduct-stock`、`/api/product/products/seckill/rollback` 仅供服务间直连，经网关访问一律 403
- **身份头防伪造**：无论后续走哪条分支，先剥离客户端自带的 `X-User-*` 与网关签名头，只允许网关自身注入
- **Token 提取**：从 `Authorization: Bearer <token>` 头提取 JWT
- **校验与透传**：校验 JWT 签名与有效期，通过后从 Claims 提取 `userId` / `username` / `role`，先比对 Redis 会话 `login:token:{uid}`（登出与单设备互踢立即生效），再写入 `X-User-Id` / `X-User-Name` / `X-User-Role` 与 HMAC 签名头透传给下游微服务；会话不一致返回业务码 `40101`，Redis 不可用时降级为仅校验 JWT（可用 `GATEWAY_SESSION_CHECK=false` 临时关闭会话比对）
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

> `ai-service`（Python）与 Milvus 未纳入全栈 `docker-compose.yml`：Milvus 使用独立的 `docker-compose.milvus.yml` 部署（etcd + minio + milvus-standalone），ai-service 以本地 Python 进程运行并连接该 Milvus；商品服务容器已预留 `OSS_*` 环境变量，未配置时图片落到容器内本地磁盘。

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
    url: jdbc:mysql://<Docker服务器IP>:3306/qoder_fullstack?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
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
- **保险咨询机器人**：`products.category = 'insurance'` 的保险商品在详情页自带 AI 咨询抽屉；后端为独立 Python 微服务（FastAPI + LangGraph + Milvus），知识限定于管理员上传的条款 PDF，检索低分直接拒答不调大模型；网关只负责鉴权与透传，业务态（文档/会话/消息）落在 `ai_*` 表。
- **图片存储双轨**：`FileStorageServiceRouter` 按 `oss.isConfigured()` 在 OSS 与本地磁盘之间切换，同一套接口与 URL 语义（OSS 返回公网/CDN 地址，本地返回经网关白名单的相对路径），开发环境无需任何云凭证即可跑通上传。
- **会话安全**：网关在 JWT 校验之外增加 Redis 会话比对（`login:token:{uid}`），登出与单设备互踢即时生效；客户端自带 `X-User-*` 头一律剥离，下游可用 `security.trusted-header.verify-signature` 强制要求网关 HMAC 签名。
