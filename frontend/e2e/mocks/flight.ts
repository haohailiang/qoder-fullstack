/**
 * 航班相关 API Mock Handlers
 *
 * 覆盖机票详情接口与 Admin 航线频率接口，使机票链路 E2E 不依赖后端服务。
 */
import { type Page } from '@playwright/test'
import {
  apiSuccess,
  mockFlightDetail,
  mockFrequencyData,
} from '../fixtures/test-data'

/**
 * 注册航班相关的 API mock 路由拦截。
 */
export async function setupFlightMocks(page: Page): Promise<void> {
  // 航班详情：/api/product/flights/:id
  await page.route('**/api/product/flights/*', (route) => {
    const url = route.request().url()

    // 频率统计
    if (url.includes('/flights/frequency')) {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          apiSuccess({ flights: [], cityFrequency: mockFrequencyData(3) }),
        ),
      })
      return
    }

    // 城市筛选
    if (url.includes('/flights/search')) {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiSuccess(mockFrequencyData(2))),
      })
      return
    }

    // 单航班详情
    const idMatch = url.match(/\/flights\/(\d+)/)
    const id = idMatch ? parseInt(idMatch[1]) : 1
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiSuccess(mockFlightDetail({ id }))),
    })
  })
}
