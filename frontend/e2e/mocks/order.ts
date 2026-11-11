/**
 * 订单相关 API Mock Handlers
 */
import { type Page } from '@playwright/test'
import { mockOrder, mockOrderList, apiSuccess } from '../fixtures/test-data'

// 内存中的订单列表（测试期间维护状态）
let orders = mockOrderList(5)

/**
 * 注册订单相关的 API mock 路由拦截。
 */
export async function setupOrderMocks(page: Page): Promise<void> {
  // 重置订单列表
  orders = mockOrderList(5)

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
