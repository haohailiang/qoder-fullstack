# 主应用(base-app)

<cite>
**本文引用的文件**   
- [main.ts](file://frontend/base-app/src/main.ts)
- [App.vue](file://frontend/base-app/src/App.vue)
- [index.ts（路由）](file://frontend/base-app/src/router/index.ts)
- [auth.ts（认证接口）](file://frontend/base-app/src/api/auth.ts)
- [request.ts（请求封装）](file://frontend/base-app/src/api/request.ts)
- [user.ts（用户接口）](file://frontend/base-app/src/api/user.ts)
- [sms.ts（短信记录接口）](file://frontend/base-app/src/api/sms.ts)
- [flight.ts（航班接口）](file://frontend/base-app/src/api/flight.ts)
- [FlightMap.vue](file://frontend/base-app/src/views/FlightMap.vue)
- [Login.vue](file://frontend/base-app/src/views/Login.vue)
- [Register.vue](file://frontend/base-app/src/views/Register.vue)
- [UserManage.vue](file://frontend/base-app/src/views/UserManage.vue)
- [i18n/index.ts](file://frontend/base-app/src/i18n/index.ts)
- [i18n/zh.ts](file://frontend/base-app/src/i18n/zh.ts)
- [i18n/en.ts](file://frontend/base-app/src/i18n/en.ts)
- [utils/auth.ts](file://frontend/base-app/src/utils/auth.ts)
- [utils/theme.ts](file://frontend/base-app/src/utils/theme.ts)
- [utils/lang.ts](file://frontend/base-app/src/utils/lang.ts)
- [micro/index.ts](file://frontend/base-app/src/micro/index.ts)
- [product-app i18n/index.ts](file://frontend/product-app/src/i18n/index.ts)
- [order-app i18n.ts](file://frontend/order-app/src/i18n.ts)
- [vite.config.ts](file://frontend/base-app/vite.config.ts)
</cite>

## 更新摘要
**变更内容**   
- 增强了前端国际化系统，实现了跨所有微应用的完整 i18n 支持
- 在 base-app 中添加了语言切换下拉菜单组件
- 实现了 Element Plus 动态语言切换功能
- 完善了子应用（product-app、order-app）的国际化配置
- 优化了语言状态管理和同步机制
- **新增** Admin 航线频率飞线图页面 FlightMap.vue（基于 ECharts 飞线图 + 中国地图），配套航班 API `api/flight.ts` 与受保护路由 `/admin/flights`（requiresAuth + adminOnly），支持按出发/落地城市筛选

## 目录
1. [简介](#简介)
2. [项目结构](#项目结构)
3. [核心组件](#核心组件)
4. [架构总览](#架构总览)
5. [详细组件分析](#详细组件分析)
6. [依赖关系分析](#依赖关系分析)
7. [性能与构建部署](#性能与构建部署)
8. [故障排查指南](#故障排查指南)
9. [结论](#结论)

## 简介
本文件面向基于 Vue 3 + Element Plus 的主应用 base-app，系统性说明其架构设计、路由与布局、页面与组件组织、用户认证流程、权限控制、用户管理界面与交互、国际化与主题切换、响应式适配，以及构建配置、部署策略和性能优化方案。该主应用作为微前端基座，通过 qiankun 加载 React 商品子应用与 Angular 订单子应用，统一提供导航、鉴权、语言与主题能力。

**更新** 本次更新重点增强了国际化系统，实现了跨所有微应用的完整 i18n 支持，包括 base-app 的语言切换下拉菜单和 Element Plus 动态语言切换功能。

## 项目结构
base-app 采用按功能域组织的目录结构：
- src/api：后端接口定义与请求封装
- src/views：页面级视图组件
- src/router：路由与守卫
- src/i18n：多语言资源与初始化（包含 zh.ts 和 en.ts）
- src/utils：认证状态、主题、语言等工具模块
- src/micro：qiankun 微前端集成
- src/styles：全局样式
- vite.config.ts：Vite 开发服务器与代理配置

```mermaid
graph TB
subgraph "主应用(base-app)"
A["main.ts<br/>应用入口"] --> B["App.vue<br/>根布局/导航/国际化/主题"]
A --> C["router/index.ts<br/>路由与守卫"]
A --> D["i18n/index.ts<br/>多语言初始化"]
A --> E["micro/index.ts<br/>qiankun 微前端"]
B --> F["views/*<br/>登录/注册/用户管理/子应用占位"]
B --> G["utils/*<br/>auth/theme/lang"]
F --> H["api/*<br/>auth/user/sms/request"]
D --> I["i18n/zh.ts<br/>中文语言包"]
D --> J["i18n/en.ts<br/>英文语言包"]
end
subgraph "子应用"
K["product-app<br/>React 商品应用"] --> L["i18n/index.ts<br/>react-i18next"]
M["order-app<br/>Angular 订单应用"] --> N["i18n.ts<br/>轻量级 i18n"]
end
subgraph "开发环境"
O["vite.config.ts<br/>代理 /api -> 网关:8080"]
end
O --> |"HTTP"| 网关["后端网关(8080)"]
```

图表来源
- [main.ts:1-48](file://frontend/base-app/src/main.ts#L1-L48)
- [App.vue:1-215](file://frontend/base-app/src/App.vue#L1-L215)
- [index.ts（路由）:1-61](file://frontend/base-app/src/router/index.ts#L1-L61)
- [i18n/index.ts:1-25](file://frontend/base-app/src/i18n/index.ts#L1-L25)
- [micro/index.ts:1-89](file://frontend/base-app/src/micro/index.ts#L1-L89)
- [product-app i18n/index.ts:1-30](file://frontend/product-app/src/i18n/index.ts#L1-L30)
- [order-app i18n.ts:1-129](file://frontend/order-app/src/i18n.ts#L1-L129)
- [vite.config.ts:1-26](file://frontend/base-app/vite.config.ts#L1-L26)

章节来源
- [main.ts:1-48](file://frontend/base-app/src/main.ts#L1-L48)
- [vite.config.ts:1-26](file://frontend/base-app/vite.config.ts#L1-L26)

## 核心组件
- 应用入口 main.ts：创建 Vue 应用，挂载 Element Plus、图标库、路由、国际化；启动主题与语言初始化；启动 qiankun。
- 根组件 App.vue：使用 el-config-provider 包裹，提供顶部导航、**语言切换下拉菜单**、主题切换、用户信息展示与退出；根据路由判断渲染基座视图或子应用容器。**新增 Element Plus 动态语言切换功能**。
- 路由 index.ts：定义公开与受保护路由，包含登录、注册、用户管理、商品与订单子应用路由；beforeEach 实现基于 token 的访问控制。
- 请求封装 request.ts：Axios 实例，统一拦截器处理 Authorization、业务码校验、401/403 与"被挤下线"逻辑、错误提示。
- 认证接口 auth.ts：登录、注册、验证码、短信验证码、当前用户、登出等 API 类型与调用。
- 用户接口 user.ts：用户 CRUD 接口类型与调用。
- 短信记录接口 sms.ts：查询短信发送记录。
- 航班接口 flight.ts：封装 `/product/flights/frequency`、`/product/flights/search`、`/product/flights` 三个只读接口及 `CityFrequency`/`FrequencyData` 类型，供 Admin 飞线图使用。
- 工具模块：
  - utils/auth.ts：token 与用户信息的响应式状态与持久化。
  - utils/theme.ts：主题模式 dark/light 的读取、切换、应用到 <html>、与后端同步。
  - **utils/lang.ts：语言 zh/en 的读取、切换、与 vue-i18n 同步、与后端同步，包含完整的语言状态管理**。
- **i18n 系统**：
  - **i18n/index.ts：vue-i18n 初始化配置**
  - **i18n/zh.ts：中文语言包，包含 app、login、register、users、lang、common 等模块**
  - **i18n/en.ts：英文语言包，结构与中文包对应**
- 微前端 micro/index.ts：qiankun 注册 product-app 与 order-app，透传 getToken/getTheme/getLang/globalActions，并监听主题/语言变化同步到子应用。

章节来源
- [main.ts:1-48](file://frontend/base-app/src/main.ts#L1-L48)
- [App.vue:1-215](file://frontend/base-app/src/App.vue#L1-L215)
- [index.ts（路由）:1-61](file://frontend/base-app/src/router/index.ts#L1-L61)
- [request.ts:1-110](file://frontend/base-app/src/api/request.ts#L1-L110)
- [auth.ts:1-87](file://frontend/base-app/src/api/auth.ts#L1-L87)
- [user.ts:1-43](file://frontend/base-app/src/api/user.ts#L1-L43)
- [sms.ts:1-25](file://frontend/base-app/src/api/sms.ts#L1-L25)
- [flight.ts:1-41](file://frontend/base-app/src/api/flight.ts#L1-L41)
- [FlightMap.vue:1-430](file://frontend/base-app/src/views/FlightMap.vue#L1-L430)
- [utils/auth.ts:1-60](file://frontend/base-app/src/utils/auth.ts#L1-L60)
- [utils/theme.ts:1-88](file://frontend/base-app/src/utils/theme.ts#L1-L88)
- [utils/lang.ts:1-68](file://frontend/base-app/src/utils/lang.ts#L1-L68)
- [i18n/index.ts:1-25](file://frontend/base-app/src/i18n/index.ts#L1-L25)
- [i18n/zh.ts:1-144](file://frontend/base-app/src/i18n/zh.ts#L1-L144)
- [i18n/en.ts:1-144](file://frontend/base-app/src/i18n/en.ts#L1-L144)
- [micro/index.ts:1-89](file://frontend/base-app/src/micro/index.ts#L1-L89)

## 架构总览
主应用作为微前端基座，承担以下职责：
- 统一 UI 框架与基础组件（Element Plus）
- 统一鉴权与路由守卫
- **统一的国际化与主题管理**
- 承载自有页面（登录、注册、用户管理）
- 通过 qiankun 动态加载子应用（商品、订单）

```mermaid
graph TB
Client["浏览器客户端"] --> Base["主应用(base-app)<br/>Vue3 + ElementPlus"]
Base --> Router["路由与守卫"]
Base --> Auth["认证状态(utils/auth)"]
Base --> I18N["国际化(i18n)<br/>vue-i18n + 语言包"]
Base --> Theme["主题(utils/theme)"]
Base --> Micro["微前端(qiankun)"]
Micro --> SubProduct["子应用: 商品(product-app)<br/>react-i18next"]
Micro --> SubOrder["子应用: 订单(order-app)<br/>轻量级 i18n"]
Base --> API["API 层(request.ts)"]
API --> Gateway["后端网关(8080)"]
I18N --> EP["Element Plus<br/>动态语言切换"]
```

图表来源
- [main.ts:1-48](file://frontend/base-app/src/main.ts#L1-L48)
- [App.vue:1-215](file://frontend/base-app/src/App.vue#L1-L215)
- [index.ts（路由）:1-61](file://frontend/base-app/src/router/index.ts#L1-L61)
- [micro/index.ts:1-89](file://frontend/base-app/src/micro/index.ts#L1-L89)
- [request.ts:1-110](file://frontend/base-app/src/api/request.ts#L1-L110)

## 详细组件分析

### 路由与权限控制
- 路由定义：
  - 公开路由：/login、/register（meta.public=true）
  - 受保护路由：/users（requiresAuth=true）、/product/*、/order/*（isSubApp=true）
  - 默认重定向至 /users
- 路由守卫 beforeEach：
  - 若目标为公开路由且已登录，则重定向到 /users
  - 若目标需要认证但未携带 token，则跳转 /login 并附带 redirect 参数
- 子应用路由：
  - /product/:pathMatch(.*)* 与 /order/:pathMatch(.*)* 由 qiankun 接管渲染

```mermaid
flowchart TD
Start(["进入路由"]) --> CheckPublic{"是否公开路由?"}
CheckPublic --> |是| HasToken{"是否已登录?"}
HasToken --> |是| Redirect["重定向到 /users"]
HasToken --> |否| AllowPublic["允许访问"]
CheckPublic --> |否| NeedAuth{"是否需要认证?"}
NeedAuth --> |否| AllowRoute["放行"]
NeedAuth --> |是| HasToken2{"是否有 token?"}
HasToken2 --> |否| ToLogin["跳转到 /login?redirect=..."]
HasToken2 --> |是| AllowRoute
```

图表来源
- [index.ts（路由）:1-61](file://frontend/base-app/src/router/index.ts#L1-L61)

章节来源
- [index.ts（路由）:1-61](file://frontend/base-app/src/router/index.ts#L1-L61)

### 用户认证流程（登录/注册/权限）
- 登录流程：
  - 非 admin 用户需图片验证码与短信验证码；admin 跳过验证码
  - 提交后保存 JWT 与用户信息到 localStorage，并应用用户偏好（主题/语言）
  - 成功后跳转 redirect 或 /users
- 注册流程：
  - 表单校验（用户名、密码、确认密码、姓名、邮箱、手机号、年龄、验证码、短信验证码）
  - 发送短信验证码前校验手机号格式与图片验证码
  - 成功后跳转登录页
- 权限验证：
  - 路由守卫基于 token 控制
  - Axios 响应拦截器对 401/403 进行统一处理，区分"被挤下线"与"普通未授权"，必要时强制跳转登录

```mermaid
sequenceDiagram
participant U as "用户"
participant L as "登录页(Login.vue)"
participant A as "认证接口(auth.ts)"
participant R as "请求封装(request.ts)"
participant S as "认证状态(utils/auth.ts)"
participant T as "主题(utils/theme.ts)"
participant G as "语言(utils/lang.ts)"
participant RT as "路由(router)"
U->>L : 输入账号/密码/验证码/短信验证码
L->>A : 调用 login()
A->>R : POST /user/auth/login
R-->>A : 返回 {token, expiresIn, user}
A-->>L : 返回结果
L->>S : setAuth(token, user)
L->>T : setTheme(user.themePreference)
L->>G : setLang(user.languagePreference)
L->>RT : 跳转 redirect 或 /users
```

图表来源
- [Login.vue:1-320](file://frontend/base-app/src/views/Login.vue#L1-L320)
- [auth.ts:1-87](file://frontend/base-app/src/api/auth.ts#L1-L87)
- [request.ts:1-110](file://frontend/base-app/src/api/request.ts#L1-L110)
- [utils/auth.ts:1-60](file://frontend/base-app/src/utils/auth.ts#L1-L60)
- [utils/theme.ts:1-88](file://frontend/base-app/src/utils/theme.ts#L1-L88)
- [utils/lang.ts:1-68](file://frontend/base-app/src/utils/lang.ts#L1-L68)

章节来源
- [Login.vue:1-320](file://frontend/base-app/src/views/Login.vue#L1-L320)
- [Register.vue:1-305](file://frontend/base-app/src/views/Register.vue#L1-L305)
- [auth.ts:1-87](file://frontend/base-app/src/api/auth.ts#L1-L87)
- [request.ts:1-110](file://frontend/base-app/src/api/request.ts#L1-L110)
- [utils/auth.ts:1-60](file://frontend/base-app/src/utils/auth.ts#L1-L60)

### 用户管理界面与交互
- 列表展示：表格展示用户基本信息，支持关键词过滤（名称/邮箱）
- 新增/编辑：弹窗表单，含必填校验、手机号正则校验、年龄范围限制
- 删除：二次确认后删除
- 短信记录：独立卡片区域，展示最近短信发送记录（手机号、验证码、状态、时间），支持刷新
- 数据源：
  - 用户列表/CRUD：user.ts
  - 短信记录：sms.ts

```mermaid
flowchart TD
Load["加载用户列表"] --> Table["渲染用户表格"]
Table --> Search["关键词过滤"]
Table --> Actions["操作列: 编辑/删除"]
Actions --> Edit["打开编辑弹窗"]
Actions --> Delete["确认删除"]
Edit --> Submit["提交表单(新增/更新)"]
Submit --> Load
Delete --> Load
Records["加载短信记录"] --> RecordTable["渲染记录表格"]
```

图表来源
- [UserManage.vue:1-255](file://frontend/base-app/src/views/UserManage.vue#L1-L255)
- [user.ts:1-43](file://frontend/base-app/src/api/user.ts#L1-L43)
- [sms.ts:1-25](file://frontend/base-app/src/api/sms.ts#L1-L25)

章节来源
- [UserManage.vue:1-255](file://frontend/base-app/src/views/UserManage.vue#L1-L255)
- [user.ts:1-43](file://frontend/base-app/src/api/user.ts#L1-L43)
- [sms.ts:1-25](file://frontend/base-app/src/api/sms.ts#L1-L25)

### 航线频率飞线图（FlightMap）
面向管理员的数据可视化页面，路由 `/admin/flights`（`requiresAuth` + `adminOnly`），用于展示各城市间航线的航班频率。
- 数据获取
  - 通过 `flight.ts` 的 `getFlightFrequency()` 调用 GET /api/product/flights/frequency，取响应体 `cityFrequency` 列表
  - 每条 `CityFrequency` 含出发/落地城市、经纬度、frequency、flightNumber、duration 及关联商品信息
- 可视化
  - 基于 ECharts 飞线图（lines + effect）叠加中国地图，按频率最大值归一化飞线粗细与颜色
  - 内置中国主要城市坐标表（cityCoords），避免额外地理编码请求
- 交互筛选
  - 从数据中提取出发/落地城市下拉选项，`departureCity`/`arrivalCity` 变更时通过 computed `filteredData` 实时重绘
  - 加载失败统一 `ElMessage.error(t('flightMap.loadFailed'))`，文案随 i18n 切换
- 生命周期：`onMounted` 初始化图表并加载数据，`onBeforeUnmount` 销毁实例并解绑 resize 监听

```mermaid
sequenceDiagram
participant View as "FlightMap.vue"
participant API as "flight.ts"
participant Gateway as "后端网关"
View->>API : getFlightFrequency()
API->>Gateway : GET /api/product/flights/frequency
Gateway-->>API : Result{flights, cityFrequency}
API-->>View : FrequencyData
View->>View : 提取城市/坐标 + 归一化频率
View->>View : echarts.setOption(飞线图)
View->>View : 筛选出发/落地城市重绘
```

图表来源
- [FlightMap.vue:1-430](file://frontend/base-app/src/views/FlightMap.vue#L1-L430)
- [flight.ts:1-41](file://frontend/base-app/src/api/flight.ts#L1-L41)

章节来源
- [FlightMap.vue:1-430](file://frontend/base-app/src/views/FlightMap.vue#L1-L430)
- [flight.ts:1-41](file://frontend/base-app/src/api/flight.ts#L1-L41)
- [index.ts（路由）:1-61](file://frontend/base-app/src/router/index.ts#L1-L61)

### 国际化配置与语言切换

**更新** 本次更新重点增强了国际化系统，实现了跨所有微应用的完整 i18n 支持。

#### 主应用国际化架构
- **i18n 初始化**：
  - 使用 vue-i18n v9 Composition API
  - 本地缓存键 LANG_KEY，默认 zh，fallbackLocale 为 zh
  - 支持中英文语言包动态加载
- **语言切换组件**：
  - **App.vue 中实现了语言切换下拉菜单**
  - 使用 el-dropdown 组件提供语言选择界面
  - 显示当前语言标签（中文/EN）
  - 禁用当前已选语言选项
- **Element Plus 动态语言切换**：
  - **通过 computed 属性 epLocale 动态切换 Element Plus 语言**
  - 根据 langRef.value 自动切换 zhCn 或 enUs
  - 所有 Element Plus 组件语言跟随应用语言设置
- **语言状态管理**：
  - utils/lang.ts 维护响应式 langRef
  - 切换时更新 localStorage 与 i18n.global.locale
  - 已登录时异步将语言偏好保存到后端
- **应用内联动**：
  - App.vue 中根据语言设置页面标题与 Element Plus 组件语言
  - Login/Register 表单提示与按钮文案均通过 t() 获取
  - 所有界面文本完全国际化

```mermaid
flowchart TD
Init["应用启动 initLang()"] --> Local["读取本地缓存语言"]
Local --> Apply["设置 i18n.global.locale"]
Apply --> Sync["如已登录，从后端拉取最新语言偏好"]
Sync --> Update["更新 langRef 与 localStorage"]
UserChange["用户切换语言"] --> Dropdown["语言切换下拉菜单"]
Dropdown --> SetLang["setLang(lang)"]
SetLang --> EP["Element Plus 动态语言切换"]
EP --> Apply
```

图表来源
- [i18n/index.ts:1-25](file://frontend/base-app/src/i18n/index.ts#L1-L25)
- [utils/lang.ts:1-68](file://frontend/base-app/src/utils/lang.ts#L1-L68)
- [App.vue:1-215](file://frontend/base-app/src/App.vue#L1-L215)

#### 子应用国际化支持
- **product-app（React）**：
  - 使用 react-i18next 库
  - 独立的 i18n 配置和语言包
  - 通过 qiankun props 接收主应用语言状态
- **order-app（Angular）**：
  - 轻量级自定义 i18n 实现
  - 内置翻译函数 t(key)
  - 支持中英文完整覆盖

章节来源
- [i18n/index.ts:1-25](file://frontend/base-app/src/i18n/index.ts#L1-L25)
- [i18n/zh.ts:1-144](file://frontend/base-app/src/i18n/zh.ts#L1-L144)
- [i18n/en.ts:1-144](file://frontend/base-app/src/i18n/en.ts#L1-L144)
- [utils/lang.ts:1-68](file://frontend/base-app/src/utils/lang.ts#L1-L68)
- [App.vue:1-215](file://frontend/base-app/src/App.vue#L1-L215)
- [product-app i18n/index.ts:1-30](file://frontend/product-app/src/i18n/index.ts#L1-L30)
- [order-app i18n.ts:1-129](file://frontend/order-app/src/i18n.ts#L1-L129)

### 主题切换与响应式设计
- 主题模式：dark/light，默认 dark
- 主题切换：
  - utils/theme.ts 维护 themeRef，切换时写入 localStorage 并给 <html> 添加/移除 class="dark"
  - 已登录时异步将主题偏好保存到后端
- 应用内联动：
  - App.vue 头部显示当前主题状态并提供切换按钮
  - 登录成功后应用用户主题偏好
- 响应式：
  - 使用 Flex 布局与 CSS 变量，适配不同屏幕尺寸
  - Element Plus 暗黑模式通过 html.dark 生效

```mermaid
flowchart TD
Toggle["点击主题切换"] --> Next["计算下一个主题"]
Next --> Persist["写入 localStorage 与 themeRef"]
Persist --> Apply["applyTheme(): 设置 html.class='dark' 或移除"]
Apply --> Backend["异步保存到后端(不阻塞UI)"]
```

图表来源
- [utils/theme.ts:1-88](file://frontend/base-app/src/utils/theme.ts#L1-L88)
- [App.vue:1-215](file://frontend/base-app/src/App.vue#L1-L215)

章节来源
- [utils/theme.ts:1-88](file://frontend/base-app/src/utils/theme.ts#L1-L88)
- [App.vue:1-215](file://frontend/base-app/src/App.vue#L1-L215)

### 微前端集成（qiankun）
- 子应用注册：
  - product-app（React）：entry 指向 localhost:7100，activeRule=/product
  - order-app（Angular）：entry 指向 localhost:7200，activeRule=/order
- 全局状态：
  - 初始 state 包含 token、user、theme、lang
  - 监听 themeRef/langRef 变化，同步到子应用
- 生命周期钩子：
  - beforeLoad/beforeMount/afterUnmount 打印日志
  - 针对 React 子应用注入 Vite React Refresh 兼容标志
- 样式隔离：
  - sandbox.experimentalStyleIsolation=true

```mermaid
sequenceDiagram
participant Base as "主应用"
participant Q as "qiankun"
participant P as "商品子应用"
participant O as "订单子应用"
Base->>Q : registerMicroApps(apps)
Q->>P : beforeLoad/beforeMount
Q->>O : beforeLoad/beforeMount
Note over Base,Q : 监听 theme/lang 变化并同步到子应用
Base->>Q : start({sandbox})
```

图表来源
- [micro/index.ts:1-89](file://frontend/base-app/src/micro/index.ts#L1-L89)

章节来源
- [micro/index.ts:1-89](file://frontend/base-app/src/micro/index.ts#L1-L89)

## 依赖关系分析
- 模块耦合：
  - main.ts 聚合 Element Plus、i18n、router、micro 与样式
  - App.vue 依赖 router、auth、theme、lang、i18n
  - 路由守卫依赖 utils/auth.getToken
  - 所有 API 调用经 request.ts 统一封装
  - 微前端模块依赖 auth/theme/lang 的状态读取函数
- 外部依赖：
  - axios：HTTP 客户端
  - element-plus：UI 组件库与图标
  - vue-router：路由与守卫
  - vue-i18n：国际化
  - qiankun：微前端运行时

```mermaid
graph LR
main_ts["main.ts"] --> app_vue["App.vue"]
main_ts --> router_idx["router/index.ts"]
main_ts --> i18n_idx["i18n/index.ts"]
main_ts --> micro_idx["micro/index.ts"]
app_vue --> utils_auth["utils/auth.ts"]
app_vue --> utils_theme["utils/theme.ts"]
app_vue --> utils_lang["utils/lang.ts"]
app_vue --> i18n_zh["i18n/zh.ts"]
app_vue --> i18n_en["i18n/en.ts"]
router_idx --> utils_auth
views_login["views/Login.vue"] --> api_auth["api/auth.ts"]
views_register["views/Register.vue"] --> api_auth
views_usermanage["views/UserManage.vue"] --> api_user["api/user.ts"]
views_usermanage --> api_sms["api/sms.ts"]
api_auth --> api_request["api/request.ts"]
api_user --> api_request
api_sms --> api_request
micro_idx --> utils_auth
micro_idx --> utils_theme
micro_idx --> utils_lang
micro_idx --> sub_product["product-app i18n"]
micro_idx --> sub_order["order-app i18n"]
```

图表来源
- [main.ts:1-48](file://frontend/base-app/src/main.ts#L1-L48)
- [App.vue:1-215](file://frontend/base-app/src/App.vue#L1-L215)
- [index.ts（路由）:1-61](file://frontend/base-app/src/router/index.ts#L1-L61)
- [Login.vue:1-320](file://frontend/base-app/src/views/Login.vue#L1-L320)
- [Register.vue:1-305](file://frontend/base-app/src/views/Register.vue#L1-L305)
- [UserManage.vue:1-255](file://frontend/base-app/src/views/UserManage.vue#L1-L255)
- [auth.ts:1-87](file://frontend/base-app/src/api/auth.ts#L1-L87)
- [user.ts:1-43](file://frontend/base-app/src/api/user.ts#L1-L43)
- [sms.ts:1-25](file://frontend/base-app/src/api/sms.ts#L1-L25)
- [request.ts:1-110](file://frontend/base-app/src/api/request.ts#L1-L110)
- [micro/index.ts:1-89](file://frontend/base-app/src/micro/index.ts#L1-L89)
- [i18n/zh.ts:1-144](file://frontend/base-app/src/i18n/zh.ts#L1-L144)
- [i18n/en.ts:1-144](file://frontend/base-app/src/i18n/en.ts#L1-L144)
- [product-app i18n/index.ts:1-30](file://frontend/product-app/src/i18n/index.ts#L1-L30)
- [order-app i18n.ts:1-129](file://frontend/order-app/src/i18n.ts#L1-L129)

## 性能与构建部署
- 构建与开发
  - Vite 插件 @vitejs/plugin-vue
  - 路径别名 @ -> src
  - 开发服务器 host 0.0.0.0，端口 5173
  - 开发代理：/api -> http://localhost:8080（网关）
- 部署建议
  - 生产环境建议使用反向代理（如 Nginx）将 /api 转发至网关，避免跨域问题
  - 静态资源开启 CDN 与缓存策略（Cache-Control、ETag）
  - 启用 Gzip/Brotli 压缩
- 性能优化
  - Element Plus 按需引入：在演示项目中采用完整引入以简化，生产可改为按需引入减少体积
  - 路由懒加载：已对页面组件使用动态 import
  - 微前端子应用按需加载：仅在匹配 activeRule 时加载
  - 图片与静态资源优化：使用现代图片格式与懒加载
  - 首屏优化：关键 CSS 内联、预加载关键字体与资源
  - **国际化资源优化：按需加载语言包，减少首屏体积**
- 安全与健壮性
  - Token 存储于 localStorage，适合演示；生产建议结合短期 Token + 刷新机制
  - 统一错误处理与用户提示，避免白屏
  - 防重复提交与倒计时控制（短信验证码）

章节来源
- [vite.config.ts:1-26](file://frontend/base-app/vite.config.ts#L1-L26)
- [index.ts（路由）:1-61](file://frontend/base-app/src/router/index.ts#L1-L61)
- [micro/index.ts:1-89](file://frontend/base-app/src/micro/index.ts#L1-L89)

## 故障排查指南
- 无法访问受保护路由
  - 检查路由守卫是否正确判断 requiresAuth 与 token
  - 确认 localStorage 中存在 demo_token
- 登录后仍被跳转回登录页
  - 检查 Axios 响应拦截器对 401/403 的处理逻辑
  - 确认后端返回的业务码是否为 40101（被挤下线）
- **语言切换相关问题**：
  - **检查 utils/lang.ts 是否正确写入 localStorage 与响应式状态**
  - **确认 App.vue 中 Element Plus 动态语言切换逻辑是否正常**
  - **检查语言切换下拉菜单的事件绑定是否正确**
  - **验证 i18n.global.locale 是否正确更新**
- **国际化相关错误**：
  - **检查语言包文件（zh.ts、en.ts）语法是否正确**
  - **确认所有需要的翻译键都已定义**
  - **检查子应用的 i18n 配置是否与主应用同步**
- 主题/语言切换无效
  - 检查 utils/theme.ts 与 utils/lang.ts 是否正确写入 localStorage 与响应式状态
  - 确认 App.vue 中是否正确使用 computed 与 watch 联动
- 微前端子应用无法加载
  - 检查 micro/index.ts 中 entry 地址与 activeRule 配置
  - 确认子应用服务已启动并可访问
- 短信验证码相关
  - 检查图片验证码 key/value 是否正确传递
  - 确认短信频控与倒计时逻辑正常

章节来源
- [request.ts:1-110](file://frontend/base-app/src/api/request.ts#L1-L110)
- [index.ts（路由）:1-61](file://frontend/base-app/src/router/index.ts#L1-L61)
- [utils/theme.ts:1-88](file://frontend/base-app/src/utils/theme.ts#L1-L88)
- [utils/lang.ts:1-68](file://frontend/base-app/src/utils/lang.ts#L1-L68)
- [i18n/index.ts:1-25](file://frontend/base-app/src/i18n/index.ts#L1-L25)
- [i18n/zh.ts:1-144](file://frontend/base-app/src/i18n/zh.ts#L1-L144)
- [i18n/en.ts:1-144](file://frontend/base-app/src/i18n/en.ts#L1-L144)
- [micro/index.ts:1-89](file://frontend/base-app/src/micro/index.ts#L1-L89)

## 结论
base-app 作为微前端基座，围绕 Vue 3 + Element Plus 构建了清晰的路由与权限体系、统一的认证与状态管理、**完善的国际化与主题能力**，并通过 qiankun 实现了多技术栈子应用的无缝集成。**本次更新重点增强了国际化系统，实现了跨所有微应用的完整 i18n 支持，包括 base-app 的语言切换下拉菜单和 Element Plus 动态语言切换功能**。配合 Vite 的开发体验与可扩展的构建配置，既满足演示场景的快速迭代，也具备向生产环境演进的基础能力。建议在后续版本中逐步推进 Element Plus 按需引入、Token 刷新机制与更细粒度的权限控制，进一步提升性能与安全。

**更新总结** 本次更新成功实现了全面的国际化系统，包括：
- 主应用中完整的 vue-i18n 配置和多语言包
- 语言切换下拉菜单组件
- Element Plus 动态语言切换
- 子应用的国际化支持
- 语言状态的全局管理和同步机制