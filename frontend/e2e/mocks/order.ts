/**
 * 订单相关 API Mock Handlers
 */
import { type Page } from '@playwright/test'
import { mockOrder, mockOrderList, apiSuccess } from '../fixtures/test-data'

// 内存中的订单列表（测试期间维护状态）
let orders = mockOrderList(5)

// 已发起支付的订单号（模拟支付宝侧已完成付款）
let paidOrderNos = new Set<string>()

/**
 * 注册订单相关的 API mock 路由拦截。
 */
export async function setupOrderMocks(page: Page): Promise<void> {
  // 重置订单列表
  orders = mockOrderList(5)
  paidOrderNos = new Set<string>()

  // 获取我的订单
  await page.route('**/api/order/orders/my', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiSuccess(orders.filter((o) => o.userId === 1))),
    })
  })

  // 订单集合接口：同一 URL 上 GET（全部订单，管理接口）与 POST（创建订单）合并注册，
  // 分两次注册会让后注册的 handler 遮蔽前一个路由，导致未命中方法的请求挂起
  await page.route('**/api/order/orders', (route) => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON()
      const newOrder = mockOrder({
        id: orders.length + 1,
        orderNo: `ORD-NEW-${String(orders.length + 1).padStart(3, '0')}`,
        ...body,
        status: 0,
        createdDate: new Date().toISOString(),
      })
      orders.unshift(newOrder)

      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiSuccess(newOrder)),
      })
      return
    }

    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiSuccess(orders)),
    })
  })

  // 查询支付状态：结果页轮询使用，返回内存中的最新状态
  // 注意：结果页会带 ?sync=true 查询参数，glob 匹配不上 query，故用正则
  await page.route(/\/api\/order\/payments\/alipay\/[^/?]+\/status(\?.*)?$/, (route) => {
    const url = route.request().url()
    const orderNoMatch = url.match(/\/payments\/alipay\/([^/?]+)\/status/)
    const orderNo = orderNoMatch ? decodeURIComponent(orderNoMatch[1]) : ''
    const order = orders.find((o) => o.orderNo === orderNo)
    const paid = paidOrderNos.has(orderNo) || order?.status === 1

    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        apiSuccess({
          orderNo,
          orderStatus: order?.status ?? 0,
          payStatus: paid ? 'PAID' : 'PENDING',
          tradeNo: paid ? `MOCK-TRADE-${orderNo}` : null,
        }),
      ),
    })
  })

  // 创建支付宝支付：不跳真实沙箱收银台，直接回到本站支付结果页（模拟同步回跳）
  await page.route(/\/api\/order\/payments\/alipay\/[^/?]+(\?.*)?$/, (route) => {
    const url = route.request().url()
    const orderNoMatch = url.match(/\/payments\/alipay\/([^/?]+)$/)
    const orderNo = orderNoMatch ? decodeURIComponent(orderNoMatch[1]) : ''
    const order = orders.find((o) => o.orderNo === orderNo)

    // 模拟用户已付款：本地标记为已支付，结果页主动查询即可确认成功
    paidOrderNos.add(orderNo)
    if (order) order.status = 1

    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        apiSuccess({
          orderNo,
          channel: 'ALIPAY',
          amount: order?.totalAmount ?? 0,
          payUrl: `/order/pay-result?orderNo=${encodeURIComponent(orderNo)}`,
          timeoutExpress: '30m',
        }),
      ),
    })
  })

  // 取消订单
  await page.route('**/api/order/orders/*/cancel', (route) => {
    const url = route.request().url()
    const idMatch = url.match(/\/orders\/(\d+)\/cancel/)
    const id = idMatch ? parseInt(idMatch[1]) : 0

    const order = orders.find((o) => o.id === id)
    if (order) {
      order.status = 4 // 已取消
    }

    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiSuccess(order || null)),
    })
  })
}

/**
 * 获取当前内存中的订单列表（用于断言）。
 */
export function getMockOrders() {
  return orders
}

/** 模拟“支付宝参数未配置”场景：创建支付返回业务码 503 */
export async function mockAlipayNotConfigured(page: Page): Promise<void> {
  // 后注册的 handler 优先生效，覆盖 setupOrderMocks 中的默认实现
  await page.route(/\/api\/order\/payments\/alipay\/[^/?]+(\?.*)?$/, (route) => {
    if (route.request().method() !== 'POST') {
      route.fallback()
      return
    }
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 503, message: '支付宝支付未配置，请联系管理员', data: null }),
    })
  })
}
