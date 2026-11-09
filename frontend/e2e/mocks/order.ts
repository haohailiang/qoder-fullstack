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

  // 获取全部订单
  await page.route('**/api/order/orders', (route) => {
    if (route.request().method() === 'GET') {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiSuccess(orders)),
      })
    }
  })

  // 创建订单
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
    }
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
