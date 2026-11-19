---
kind: external_dependency
name: 支付宝沙箱支付（Alipay Sandbox）
slug: alipay-sandbox
category: external_dependency
category_hints:
    - vendor_identity
    - client_constraint
scope:
    - '**'
---

### 支付宝开放平台（沙箱）
- 角色：订单服务的支付渠道，提供收银台跳转、异步通知与主动查询三种交互；当前接入沙箱环境，未切换生产网关。
- 集成点：`backend/ecommerce-order`；`alipay-sdk-java` + `AlipayProperties`（前缀 `alipay`），凭据 `ALIPAY_APP_ID` / `ALIPAY_PRIVATE_KEY` / `ALIPAY_PUBLIC_KEY` 三者齐备才算可用（`isConfigured()`），否则创建支付直接返回业务码 503。
- 默认参数：网关 `https://openapi-sandbox.dl.alipaydev.com/gateway.do`，`signType=RSA2`，`charset=UTF-8`，`timeoutExpress=30m`；`ALIPAY_NOTIFY_URL` 必须公网可达，为空时只能依赖前端轮询的主动查询。
- 接口：`POST /payments/alipay/{orderNo}`（仅待支付订单、校验归属）、`GET /payments/alipay/{orderNo}/status?sync=true`（主动查单并回写）、`POST /payments/alipay/notify`（网关白名单，`produces=text/plain` 返回 `success` / `failure`）。
- 回调校验顺序：参数已配置 → RSA2 验签 → `app_id` → `out_trade_no` → `trade_status`（仅 TRADE_SUCCESS / TRADE_FINISHED）→ `total_amount` → 落库；任一步失败记录 `[ALIPAY-ALARM]` 告警并返回 `failure`。
- 幂等：`payment_record` 以 `uk_channel_order(channel, order_no)` 与 `uk_trade_no(trade_no)` 唯一约束去重，状态流转由 `PaymentStateManager` 条件更新（`WHERE status=PENDING_PAYMENT`），重复通知只会生效一次，已支付时返回 `ALREADY_PAID`。