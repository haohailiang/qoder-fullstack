# 订单服务API

<cite>
**本文引用的文件**   
- [OrderController.java](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java)
- [OrderService.java](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java)
- [OrderServiceImpl.java](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java)
- [ProductClient.java](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/client/ProductClient.java)
- [SeckillOrderConsumer.java](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/consumer/SeckillOrderConsumer.java)
- [Order.java](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/Order.java)
- [OrderStatus.java](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatus.java)
- [OrderStatusTypeHandler.java](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatusTypeHandler.java)
- [schema.sql](file://backend/ecommerce-order/src/main/resources/db/schema.sql)
- [OrderMapper.xml](file://backend/ecommerce-order/src/main/resources/mapper/OrderMapper.xml)
- [Result.java](file://backend/ecommerce-common/src/main/java/com/ecommerce/common/Result.java)
- [BusinessException.java](file://backend/ecommerce-common/src/main/java/com/ecommerce/common/BusinessException.java)
- [SeckillMessage.java](file://backend/ecommerce-common/src/main/java/com/ecommerce/common/SeckillMessage.java)
- [ProductController.java](file://backend/ecommerce-product/src/main/java/com/ecommerce/product/controller/ProductController.java)
- [SeckillController.java](file://backend/ecommerce-product/src/main/java/com/ecommerce/product/controller/SeckillController.java)
- [PaymentController.java](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/PaymentController.java)
- [AlipayPaymentServiceImpl.java](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/AlipayPaymentServiceImpl.java)
- [PaymentStateManager.java](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/PaymentStateManager.java)
- [PaymentRecord.java](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/PaymentRecord.java)
- [init-sql/02-alipay-payment.sql](file://init-sql/02-alipay-payment.sql)
</cite>

## 更新摘要
**变更内容**   
- 引入了OrderStatus枚举替代魔法数字，增强了类型安全性
- 新增OrderStatusTypeHandler进行MyBatis自动转换
- 更新了订单状态管理接口的参数验证机制
- 改进了代码可维护性和类型安全

## 目录
1. [简介](#简介)
2. [项目结构](#项目结构)
3. [核心组件](#核心组件)
4. [架构总览](#架构总览)
5. [接口规范](#接口规范)
6. [详细组件分析](#详细组件分析)
7. [依赖关系分析](#依赖关系分析)
8. [性能与扩展性](#性能与扩展性)
9. [故障排查指南](#故障排查指南)
10. [结论](#结论)

## 简介
本文件面向订单服务的 API 文档，覆盖以下能力：
- 订单管理接口：创建、查询（按用户、按单号、全部）、取消、状态更新。
- 支付接口：创建支付宝沙箱支付、支付状态查询（可主动同步）、支付宝异步通知。
- 秒杀订单消费接口：基于 RabbitMQ 的异步消息处理，完成秒杀下单。
- 与商品服务的 OpenFeign 集成：库存扣减、商品信息校验。
- 订单状态流转、秒杀异步处理流程、分布式事务处理的技术说明。
- **新增**：基于枚举的类型安全的订单状态管理机制。

## 项目结构
订单服务位于 backend/ecommerce-order，对外暴露 REST 接口；通过 OpenFeign 调用商品服务 ecommerce-product；通过 RabbitMQ 接收秒杀消息并落库。

```mermaid
graph TB
Client["客户端"] --> Gateway["网关(认证透传)"]
Gateway --> OrderAPI["订单服务(OrderController)"]
OrderAPI --> OrderSvc["订单服务实现(OrderServiceImpl)"]
OrderSvc --> ProductFeign["商品服务Feign(ProductClient)"]
ProductFeign --> ProductAPI["商品服务(ProductController.deductStock)"]
ProductSeckill["商品服务秒杀(SeckillController)"] --> MQ["RabbitMQ"]
MQ --> SeckillConsumer["订单服务消费者(SeckillOrderConsumer)"]
SeckillConsumer --> DB["MySQL(orders表)"]
DB --> TypeHandler["MyBatis类型处理器(OrderStatusTypeHandler)"]
```

图表来源
- [OrderController.java:17-88](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java#L17-L88)
- [OrderServiceImpl.java:19-119](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L19-L119)
- [ProductClient.java:11-19](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/client/ProductClient.java#L11-L19)
- [ProductController.java:88-95](file://backend/ecommerce-product/src/main/java/com/ecommerce/product/controller/ProductController.java#L88-L95)
- [SeckillController.java:57-122](file://backend/ecommerce-product/src/main/java/com/ecommerce/product/controller/SeckillController.java#L57-L122)
- [SeckillOrderConsumer.java:35-67](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/consumer/SeckillOrderConsumer.java#L35-L67)
- [OrderStatusTypeHandler.java:15-40](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatusTypeHandler.java#L15-L40)

章节来源
- [OrderController.java:17-88](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java#L17-L88)
- [OrderServiceImpl.java:19-119](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L19-L119)
- [ProductClient.java:11-19](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/client/ProductClient.java#L11-L19)
- [ProductController.java:88-95](file://backend/ecommerce-product/src/main/java/com/ecommerce/product/controller/ProductController.java#L88-L95)
- [SeckillController.java:57-122](file://backend/ecommerce-product/src/main/java/com/ecommerce/product/controller/SeckillController.java#L57-L122)
- [SeckillOrderConsumer.java:35-67](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/consumer/SeckillOrderConsumer.java#L35-L67)

## 核心组件
- 控制器层：OrderController 提供订单相关 HTTP 接口；PaymentController 提供支付创建/查询/通知接口。
- 服务层：OrderService 定义业务契约；OrderServiceImpl 实现订单创建、查询、取消、状态更新等逻辑。
- 外部依赖：
  - OpenFeign 客户端 ProductClient 调用商品服务扣减库存。
  - RabbitMQ 消费者 SeckillOrderConsumer 处理秒杀订单消息。
- 数据模型：Order 实体对应 orders 表；PaymentRecord 对应支付流水表；统一响应 Result；业务异常 BusinessException；秒杀消息 SeckillMessage。
- **新增**：OrderStatus 枚举定义了所有订单状态；OrderStatusTypeHandler 实现了 MyBatis 类型自动转换。

章节来源
- [OrderController.java:17-88](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java#L17-L88)
- [OrderService.java:7-28](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java#L7-L28)
- [OrderServiceImpl.java:19-119](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L19-L119)
- [ProductClient.java:11-19](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/client/ProductClient.java#L11-L19)
- [SeckillOrderConsumer.java:21-67](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/consumer/SeckillOrderConsumer.java#L21-L67)
- [Order.java:11-35](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/Order.java#L11-L35)
- [OrderStatus.java:8-48](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatus.java#L8-L48)
- [OrderStatusTypeHandler.java:15-40](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatusTypeHandler.java#L15-L40)
- [Result.java:8-63](file://backend/ecommerce-common/src/main/java/com/ecommerce/common/Result.java#L8-L63)
- [BusinessException.java:6-22](file://backend/ecommerce-common/src/main/java/com/ecommerce/common/BusinessException.java#L6-L22)
- [SeckillMessage.java:10-25](file://backend/ecommerce-common/src/main/java/com/ecommerce/common/SeckillMessage.java#L10-L25)

## 架构总览
订单服务采用分层架构：
- 控制层负责参数校验、鉴权信息提取、统一响应封装。
- 服务层编排业务逻辑，包含与商品服务的 Feign 调用和数据库操作。
- 异步侧通过 RabbitMQ 解耦秒杀下单与订单落库。
- **新增**：通过 MyBatis 类型处理器实现枚举与数据库字段的自动转换。

```mermaid
classDiagram
class OrderController {
+createOrder(order, request)
+getMyOrders(request)
+getOrderByOrderNo(orderNo)
+getAllOrders()
+cancelOrder(orderId, request)
+updateStatus(orderId, status)
}
class OrderService {
<<interface>>
+createOrder(userId, order)
+getOrdersByUserId(userId)
+getOrderByOrderNo(orderNo)
+getOrderById(id)
+cancelOrder(orderId, userId)
+updateOrderStatus(orderId, status)
+getAllOrders()
}
class OrderServiceImpl {
-orderRepository
-productClient
+createOrder(userId, order)
+getOrdersByUserId(userId)
+getOrderByOrderNo(orderNo)
+getOrderById(id)
+cancelOrder(orderId, userId)
+updateOrderStatus(orderId, status)
+getAllOrders()
}
class ProductClient {
+deductStock(productId, quantity)
}
class SeckillOrderConsumer {
+onMessage(message)
}
class OrderStatus {
+PENDING_PAYMENT
+PAID
+SHIPPED
+COMPLETED
+CANCELLED
+fromCode(code)
+getDescriptionByCode(code)
}
class OrderStatusTypeHandler {
+setNonNullParameter(ps, i, parameter, jdbcType)
+getNullableResult(rs, columnName)
+getNullableResult(rs, columnIndex)
+getNullableResult(cs, columnIndex)
}
OrderController --> OrderService : "调用"
OrderServiceImpl ..|> OrderService : "实现"
OrderServiceImpl --> ProductClient : "Feign调用"
OrderServiceImpl --> OrderStatus : "使用"
OrderStatusTypeHandler --> OrderStatus : "转换"
SeckillOrderConsumer --> ProductClient : "Feign调用"
```

图表来源
- [OrderController.java:17-88](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java#L17-L88)
- [OrderService.java:7-28](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java#L7-L28)
- [OrderServiceImpl.java:19-119](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L19-L119)
- [ProductClient.java:11-19](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/client/ProductClient.java#L11-L19)
- [SeckillOrderConsumer.java:21-67](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/consumer/SeckillOrderConsumer.java#L21-L67)
- [OrderStatus.java:8-48](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatus.java#L8-L48)
- [OrderStatusTypeHandler.java:15-40](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatusTypeHandler.java#L15-L40)

## 接口规范
通用约定
- 基础路径：/orders
- 统一响应体：Result<T>，包含 code、message、data。成功时 code=200。
- 鉴权：请求头携带用户ID（由网关注入），未携带将返回未授权错误。
- 业务异常：抛出 BusinessException，由全局异常处理器转换为 Result(code, message)。

### 订单管理接口

#### 创建订单
- 方法：POST
- URL：/orders
- 请求头：需包含用户ID（由网关注入）
- 请求体字段（JSON）：
  - productId: Long，必填
  - quantity: Integer，必填
  - address: String，可选
  - remark: String，可选
  - productName: String，可选（冗余字段）
  - totalAmount: BigDecimal，可选（服务端可计算或忽略）
- 成功响应：code=200，data=Order
- 失败场景：
  - 未授权：code=401
  - 库存不足或商品不存在：code=400
  - 库存扣减异常：code=500

调用示例
- 请求
  - POST /orders
  - Header: X-User-Id: 1001
  - Body: {"productId": 1, "quantity": 2, "address": "上海市浦东新区..."}
- 成功响应
  - { "code": 200, "message": "success", "data": { "id": 1, "orderNo": "ORD2025...", "userId": 1001, "productId": 1, "productName": "...", "quantity": 2, "totalAmount": 0, "status": 0, "address": "...", "remark": null, "createdDate": "...", "updatedDate": "..." } }

章节来源
- [OrderController.java:28-34](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java#L28-L34)
- [OrderServiceImpl.java:33-61](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L33-L61)
- [ProductClient.java:11-19](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/client/ProductClient.java#L11-L19)
- [Result.java:8-63](file://backend/ecommerce-common/src/main/java/com/ecommerce/common/Result.java#L8-L63)
- [BusinessException.java:6-22](file://backend/ecommerce-common/src/main/java/com/ecommerce/common/BusinessException.java#L6-L22)

#### 查询我的订单
- 方法：GET
- URL：/orders/my
- 请求头：需包含用户ID
- 成功响应：code=200，data=List<Order>

调用示例
- 请求
  - GET /orders/my
  - Header: X-User-Id: 1001
- 成功响应
  - { "code": 200, "message": "success", "data": [ {...}, {...} ] }

章节来源
- [OrderController.java:36-42](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java#L36-L42)
- [OrderServiceImpl.java:63-66](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L63-L66)

#### 查询订单详情（按订单号）
- 方法：GET
- URL：/orders/{orderNo}
- 路径参数：orderNo（String）
- 成功响应：code=200，data=Order
- 失败场景：订单不存在，code=404

调用示例
- 请求
  - GET /orders/ORD20250101120000ABCD
- 成功响应
  - { "code": 200, "message": "success", "data": { ... } }

章节来源
- [OrderController.java:44-49](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java#L44-L49)
- [OrderServiceImpl.java:68-75](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L68-L75)

#### 查询所有订单（管理用）
- 方法：GET
- URL：/orders
- 成功响应：code=200，data=List<Order>

调用示例
- 请求
  - GET /orders
- 成功响应
  - { "code": 200, "message": "success", "data": [ {...}, {...} ] }

章节来源
- [OrderController.java:51-56](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java#L51-L56)
- [OrderServiceImpl.java:108-111](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L108-L111)

#### 取消订单
- 方法：POST
- URL：/orders/{orderId}/cancel
- 路径参数：orderId（Long）
- 请求头：需包含用户ID
- 成功响应：code=200，data=null
- 失败场景：
  - 无权操作：code=403
  - 当前状态不可取消：code=400

调用示例
- 请求
  - POST /orders/1/cancel
  - Header: X-User-Id: 1001
- 成功响应
  - { "code": 200, "message": "success", "data": null }

章节来源
- [OrderController.java:58-65](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java#L58-L65)
- [OrderServiceImpl.java:86-99](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L86-L99)

#### 更新订单状态
- 方法：PUT
- URL：/orders/{orderId}/status
- 路径参数：orderId（Long）
- 查询参数：status（Integer）
- **更新**：现在支持枚举验证，无效的状态值会返回错误
- 成功响应：code=200，data=null
- **新增**：失败场景包括无效状态码（code=400）

调用示例
- 请求
  - PUT /orders/1/status?status=1
- 成功响应
  - { "code": 200, "message": "success", "data": null }

章节来源
- [OrderController.java:67-78](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java#L67-L78)
- [OrderServiceImpl.java:101-106](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L101-L106)
- [OrderStatus.java:30-47](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatus.java#L30-L47)

### 支付接口（支付宝沙箱）

创建支付与查询需要登录态（网关注入的 X-User-Id）且只能操作自己的订单（ADMIN 放行）；异步通知由支付宝服务器直接调用，不带 JWT，安全性由验签保证。

#### 创建支付
- 方法：POST
- URL：/payments/alipay/{orderNo}
- 路径参数：orderNo（String）
- 成功响应：`Result<PaymentCreateResponse>`，data 中包含收银台 `payUrl`
- 失败场景：
  - 订单不存在：404
  - 订单不属于当前用户：403
  - 订单状态不是 PENDING_PAYMENT：400「当前订单状态不可支付」
  - 未配置 `ALIPAY_*`：503「支付宝支付未配置」

#### 查询支付状态
- 方法：GET
- URL：/payments/alipay/{orderNo}/status
- 查询参数：sync（Boolean，默认 true）——是否先向支付宝主动查询并同步本地状态
- 成功响应：`Result<PaymentStatusResponse>`，包含订单状态与支付状态
- 用途：支付结果页每 2 秒轮询，最多 10 次，命中终态即停止

#### 支付宝异步通知
- 方法：POST
- URL：/payments/alipay/notify
- 鉴权：网关白名单，无 JWT；依赖支付宝验签 + app_id + 金额校验
- 响应：`text/plain`，校验通过返回 `success`，否则 `failure`（必须为纯文本，否则支付宝会持续重试）

幂等与校验要点：

- 回调校验顺序：参数未配置 → 验签 → app_id → out_trade_no → trade_status → 金额 → markPaid。
- `PaymentStateManager` 使用 `WHERE status = PENDING_PAYMENT` 条件更新，只有更新成功才写流水；重复通知返回 `ALREADY_PAID`，不会二次变更。
- 订单已处于 PAID/SHIPPED/COMPLETED 时仅尝试补齐 `trade_no`；金额不一致或并发冲突返回 `CONFLICT`。
- `payment_record` 以 `UNIQUE(channel, order_no)` 与 `UNIQUE(trade_no)` 保证同一订单/交易号只有一条流水。

**章节来源**
- [PaymentController.java:28-95](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/PaymentController.java#L28-L95)
- [AlipayPaymentServiceImpl.java](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/AlipayPaymentServiceImpl.java)
- [PaymentStateManager.java](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/PaymentStateManager.java)
- [init-sql/02-alipay-payment.sql](file://init-sql/02-alipay-payment.sql)

### 秒杀订单消费接口（RabbitMQ）
- 类型：RabbitMQ 消费者监听
- 队列：SECKILL_QUEUE（由配置常量定义）
- 消息体：SeckillMessage（包含 userId、productId、quantity、orderNo、createTime）
- 处理逻辑：
  - 通过 Feign 校验商品存在（不实际扣库存）。
  - 创建订单记录，状态标记为已支付（秒杀直接支付）。
  - 保存至 MySQL。
- 失败处理：抛出异常触发重试；进入死信队列后可人工处理或定时重试。

章节来源
- [SeckillOrderConsumer.java:21-67](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/consumer/SeckillOrderConsumer.java#L21-L67)
- [SeckillMessage.java:10-25](file://backend/ecommerce-common/src/main/java/com/ecommerce/common/SeckillMessage.java#L10-L25)

## 详细组件分析

### 订单创建流程
- 控制器从请求头解析用户ID。
- 生成订单号，设置默认状态为待支付（使用枚举 OrderStatus.PENDING_PAYMENT）。
- 通过 Feign 调用商品服务扣减库存。
- 扣减成功后持久化订单。

```mermaid
sequenceDiagram
participant Client as "客户端"
participant Controller as "OrderController"
participant Service as "OrderServiceImpl"
participant Feign as "ProductClient"
participant Product as "ProductController.deductStock"
participant DB as "MySQL"
Client->>Controller : POST /orders
Controller->>Service : createOrder(userId, order)
Service->>Service : generateOrderNo()
Service->>Service : setStatus(OrderStatus.PENDING_PAYMENT)
Service->>Feign : deductStock(productId, quantity)
Feign->>Product : POST /product/products/deduct-stock
Product-->>Feign : Result<Boolean>
Feign-->>Service : Boolean
Service->>DB : save(order)
Service-->>Controller : Order
Controller-->>Client : Result<Order>
```

图表来源
- [OrderController.java:28-34](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java#L28-L34)
- [OrderServiceImpl.java:33-61](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L33-L61)
- [ProductClient.java:11-19](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/client/ProductClient.java#L11-L19)
- [ProductController.java:88-95](file://backend/ecommerce-product/src/main/java/com/ecommerce/product/controller/ProductController.java#L88-L95)

章节来源
- [OrderController.java:28-34](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java#L28-L34)
- [OrderServiceImpl.java:33-61](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L33-L61)
- [ProductClient.java:11-19](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/client/ProductClient.java#L11-L19)
- [ProductController.java:88-95](file://backend/ecommerce-product/src/main/java/com/ecommerce/product/controller/ProductController.java#L88-L95)

### 订单状态流转
- **更新**：使用 OrderStatus 枚举管理订单状态，提供更好的类型安全性
- 状态枚举：
  - PENDING_PAYMENT(0, "待支付")
  - PAID(1, "已支付")
  - SHIPPED(2, "已发货")
  - COMPLETED(3, "已完成")
  - CANCELLED(4, "已取消")
- 初始状态：创建时为 PENDING_PAYMENT。
- 取消：仅 PENDING_PAYMENT 可取消，更新为 CANCELLED。
- 更新：管理接口支持任意状态更新（用于后续流程如发货、完成）。

```mermaid
flowchart TD
Start(["创建订单"]) --> New["状态=PENDING_PAYMENT 待支付"]
New --> Cancel{"是否取消?"}
Cancel --> |是| Canceled["状态=CANCELLED 已取消"]
Cancel --> |否| Pay{"是否支付?"}
Pay --> |是| Paid["状态=PAID 已支付"]
Pay --> |否| New
Paid --> Ship{"是否发货?"}
Ship --> |是| Shipped["状态=SHIPPED 已发货"]
Ship --> |否| Paid
Shipped --> Done{"是否完成?"}
Done --> |是| Completed["状态=COMPLETED 已完成"]
Done --> |否| Shipped
```

图表来源
- [OrderStatus.java:11-15](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatus.java#L11-L15)
- [OrderServiceImpl.java:39-40](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L39-L40)
- [OrderServiceImpl.java:93-96](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L93-L96)
- [OrderServiceImpl.java:102-106](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L102-L106)

章节来源
- [OrderStatus.java:11-15](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatus.java#L11-L15)
- [OrderServiceImpl.java:39-40](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L39-L40)
- [OrderServiceImpl.java:93-96](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L93-L96)
- [OrderServiceImpl.java:102-106](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L102-L106)

### 秒杀订单异步处理流程
- 商品服务秒杀接口进行预扣减（Redis Lua），防重与时间校验。
- 发送 SeckillMessage 到 RabbitMQ。
- 订单服务消费者接收消息，校验商品存在，创建订单并落库。

```mermaid
sequenceDiagram
participant Client as "客户端"
participant ProductSC as "SeckillController"
participant Redis as "Redis(Lua)"
participant MQ as "RabbitMQ"
participant Consumer as "SeckillOrderConsumer"
participant DB as "MySQL"
Client->>ProductSC : POST /products/seckill/{productId}?quantity=1
ProductSC->>Redis : tryDecrStock(productId, quantity)
Redis-->>ProductSC : remaining
ProductSC->>MQ : send(SeckillMessage)
ProductSC-->>Client : {orderNo, status : "QUEUING"}
MQ-->>Consumer : onMessage(SeckillMessage)
Consumer->>Consumer : 校验商品存在(Feign)
Consumer->>DB : save(order)
Consumer-->>MQ : 成功/失败(重试或死信)
```

图表来源
- [SeckillController.java:57-122](file://backend/ecommerce-product/src/main/java/com/ecommerce/product/controller/SeckillController.java#L57-L122)
- [SeckillOrderConsumer.java:35-67](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/consumer/SeckillOrderConsumer.java#L35-L67)
- [SeckillMessage.java:10-25](file://backend/ecommerce-common/src/main/java/com/ecommerce/common/SeckillMessage.java#L10-L25)

章节来源
- [SeckillController.java:57-122](file://backend/ecommerce-product/src/main/java/com/ecommerce/product/controller/SeckillController.java#L57-L122)
- [SeckillOrderConsumer.java:35-67](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/consumer/SeckillOrderConsumer.java#L35-L67)
- [SeckillMessage.java:10-25](file://backend/ecommerce-common/src/main/java/com/ecommerce/common/SeckillMessage.java#L10-L25)

### 与商品服务的OpenFeign集成
- 订单服务通过 ProductClient 调用商品服务 /product/products/deduct-stock。
- 商品服务暴露该接口供订单服务在普通下单时扣减库存。

```mermaid
sequenceDiagram
participant OrderSvc as "OrderServiceImpl"
participant Feign as "ProductClient"
participant ProductCtrl as "ProductController.deductStock"
OrderSvc->>Feign : deductStock(productId, quantity)
Feign->>ProductCtrl : POST /product/products/deduct-stock
ProductCtrl-->>Feign : Result<Boolean>
Feign-->>OrderSvc : Boolean
```

图表来源
- [OrderServiceImpl.java:43-56](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L43-L56)
- [ProductClient.java:11-19](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/client/ProductClient.java#L11-L19)
- [ProductController.java:88-95](file://backend/ecommerce-product/src/main/java/com/ecommerce/product/controller/ProductController.java#L88-L95)

章节来源
- [OrderServiceImpl.java:43-56](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L43-L56)
- [ProductClient.java:11-19](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/client/ProductClient.java#L11-L19)
- [ProductController.java:88-95](file://backend/ecommerce-product/src/main/java/com/ecommerce/product/controller/ProductController.java#L88-L95)

### 订单状态枚举与类型处理器机制
**新增**：系统引入了类型安全的订单状态管理机制

- **OrderStatus 枚举**：定义了所有可能的订单状态，提供了状态码映射和描述信息
- **OrderStatusTypeHandler**：MyBatis 类型处理器，自动在数据库整数和 Java 枚举之间进行转换
- **类型安全优势**：避免了魔法数字的使用，提高了代码的可读性和可维护性

```mermaid
classDiagram
class OrderStatus {
+PENDING_PAYMENT(0, "待支付")
+PAID(1, "已支付")
+SHIPPED(2, "已发货")
+COMPLETED(3, "已完成")
+CANCELLED(4, "已取消")
+fromCode(int code) OrderStatus
+getDescriptionByCode(int code) String
}
class OrderStatusTypeHandler {
+setNonNullParameter(PreparedStatement, int, OrderStatus, JdbcType)
+getNullableResult(ResultSet, String) OrderStatus
+getNullableResult(ResultSet, int) OrderStatus
+getNullableResult(CallableStatement, int) OrderStatus
}
OrderStatusTypeHandler --> OrderStatus : "转换"
```

图表来源
- [OrderStatus.java:8-48](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatus.java#L8-L48)
- [OrderStatusTypeHandler.java:15-40](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatusTypeHandler.java#L15-L40)

章节来源
- [OrderStatus.java:8-48](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatus.java#L8-L48)
- [OrderStatusTypeHandler.java:15-40](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatusTypeHandler.java#L15-L40)
- [OrderMapper.xml:13](file://backend/ecommerce-order/src/main/resources/mapper/OrderMapper.xml#L13)
- [OrderMapper.xml:42](file://backend/ecommerce-order/src/main/resources/mapper/OrderMapper.xml#L42)
- [OrderMapper.xml:46](file://backend/ecommerce-order/src/main/resources/mapper/OrderMapper.xml#L46)

## 依赖关系分析
- 控制器依赖服务层，服务层依赖仓储与外部客户端。
- 消费者依赖仓储与外部客户端。
- 统一响应与异常在 common 模块中共享。
- **新增**：OrderStatus 枚举被多个组件依赖，OrderStatusTypeHandler 被 MyBatis 框架使用。

```mermaid
graph LR
OC["OrderController"] --> OS["OrderService"]
OS --> OSI["OrderServiceImpl"]
OSI --> PC["ProductClient"]
SOC["SeckillOrderConsumer"] --> PC
OSI --> DB["MySQL(orders)"]
SOC --> DB
Common["common(Result/BusinessException)"] --> OC
Common --> OSI
Common --> SOC
OSI --> OSEnum["OrderStatus"]
OSEnum --> OSMapper["OrderMapper.xml"]
OSMapper --> OTH["OrderStatusTypeHandler"]
OTH --> DB
```

图表来源
- [OrderController.java:17-88](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java#L17-L88)
- [OrderService.java:7-28](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java#L7-L28)
- [OrderServiceImpl.java:19-119](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L19-L119)
- [ProductClient.java:11-19](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/client/ProductClient.java#L11-L19)
- [SeckillOrderConsumer.java:21-67](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/consumer/SeckillOrderConsumer.java#L21-L67)
- [OrderStatus.java:8-48](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatus.java#L8-L48)
- [OrderStatusTypeHandler.java:15-40](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatusTypeHandler.java#L15-L40)
- [OrderMapper.xml:13](file://backend/ecommerce-order/src/main/resources/mapper/OrderMapper.xml#L13)
- [Result.java:8-63](file://backend/ecommerce-common/src/main/java/com/ecommerce/common/Result.java#L8-L63)
- [BusinessException.java:6-22](file://backend/ecommerce-common/src/main/java/com/ecommerce/common/BusinessException.java#L6-L22)

章节来源
- [OrderController.java:17-88](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java#L17-L88)
- [OrderService.java:7-28](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/OrderService.java#L7-L28)
- [OrderServiceImpl.java:19-119](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L19-L119)
- [ProductClient.java:11-19](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/client/ProductClient.java#L11-L19)
- [SeckillOrderConsumer.java:21-67](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/consumer/SeckillOrderConsumer.java#L21-L67)
- [OrderStatus.java:8-48](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatus.java#L8-L48)
- [OrderStatusTypeHandler.java:15-40](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatusTypeHandler.java#L15-L40)
- [OrderMapper.xml:13](file://backend/ecommerce-order/src/main/resources/mapper/OrderMapper.xml#L13)
- [Result.java:8-63](file://backend/ecommerce-common/src/main/java/com/ecommerce/common/Result.java#L8-L63)
- [BusinessException.java:6-22](file://backend/ecommerce-common/src/main/java/com/ecommerce/common/BusinessException.java#L6-L22)

## 性能与扩展性
- 秒杀场景通过 Redis Lua 预扣减库存，避免热点行竞争，提升吞吐。
- 订单落库异步化，削峰填谷，降低主链路延迟。
- **新增**：枚举类型提高了代码的可维护性，减少了状态管理的复杂性。
- 建议：
  - 对高频查询增加缓存（如最近订单列表）。
  - 消费者端增加幂等与去重机制（基于 orderNo）。
  - 引入重试与死信队列，保障消息可靠性。
  - 分库分表策略预留（按 user_id 或 product_id 索引优化）。
  - 考虑为订单状态添加索引以优化查询性能。

[本节为通用指导，不涉及具体文件分析]

## 故障排查指南
- 未授权访问：检查网关是否正确注入用户ID请求头。
- 库存不足：确认商品服务库存扣减接口返回 false 的原因（库存不足或商品不存在）。
- 订单不存在：核对 orderNo 或 orderId 是否正确。
- 秒杀排队中：等待消费者处理完成，再查询订单获取最终结果。
- 消费者失败：查看日志定位异常；检查 RabbitMQ 死信队列；必要时人工补偿。
- 支付相关：
  - 创建支付 503：确认 `ALIPAY_APP_ID`、`ALIPAY_PRIVATE_KEY`、`ALIPAY_PUBLIC_KEY` 是否全部配置。
  - 回调返回 failure：查看验签日志（`[ALIPAY-ALARM]`），重点确认使用「支付宝公钥」而非应用公钥、`app_id` 与金额一致。
  - 已付款但订单未变：检查 `ALIPAY_NOTIFY_URL` 是否公网可达；或调用 `status?sync=true` 主动查询兼容。
- **新增**：状态更新失败：检查传入的状态码是否在有效范围内（0-4），确认 OrderStatus.fromCode() 能正确解析状态值。

章节来源
- [OrderController.java:80-86](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/controller/OrderController.java#L80-L86)
- [OrderServiceImpl.java:43-56](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L43-L56)
- [OrderServiceImpl.java:68-75](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/service/impl/OrderServiceImpl.java#L68-L75)
- [SeckillOrderConsumer.java:62-67](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/consumer/SeckillOrderConsumer.java#L62-L67)
- [OrderStatus.java:30-47](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatus.java#L30-L47)

## 结论
订单服务提供了完整的订单管理能力，并通过 OpenFeign 与商品服务协作完成库存扣减；同时借助 RabbitMQ 实现了高并发秒杀下单的异步化处理，并通过支付宝沙箱支付补齐了交易闭环。**新增的枚举类型机制显著提升了代码的类型安全性和可维护性**。建议在现有基础上完善幂等、重试与补偿机制，进一步提升系统的健壮性与可观测性。

[本节为总结性内容，不涉及具体文件分析]

## 附录

### 数据模型与表结构
- 订单实体 Order 字段与 orders 表字段映射一致，包含唯一索引 order_no 及常用查询索引。
- **新增**：status 字段现在使用 OrderStatus 枚举进行类型安全的管理，通过 OrderStatusTypeHandler 实现自动转换。

章节来源
- [Order.java:11-35](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/Order.java#L11-L35)
- [OrderStatus.java:8-48](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatus.java#L8-L48)
- [OrderStatusTypeHandler.java:15-40](file://backend/ecommerce-order/src/main/java/com/ecommerce/order/entity/enums/OrderStatusTypeHandler.java#L15-L40)
- [OrderMapper.xml:13](file://backend/ecommerce-order/src/main/resources/mapper/OrderMapper.xml#L13)
- [schema.sql:6-23](file://backend/ecommerce-order/src/main/resources/db/schema.sql#L6-L23)