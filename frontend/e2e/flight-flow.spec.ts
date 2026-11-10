/**
 * E2E 测试：机票浏览与详情链路
 *
 * 覆盖场景：
 * 1. 商品列表存在机票商品 → 点击"查看详情" → 路由跳转到 /product/flight/:id
 * 2. 机票详情页渲染航班信息（航班号、出发/落地城市）
 *
 * 不依赖后端：通过 mock 商品搜索接口返回机票商品、mock 航班详情接口返回航线数据。
 */
import { test, expect } from '@playwright/test'
import { setupAuthMocks } from './mocks/auth'
import { setupFlightMocks } from './mocks/flight'
import { adminLogin, waitForSubAppMount } from './helpers'
import { apiSuccess, mockProduct } from './fixtures/test-data'

const flightProduct = mockProduct({
  id: 7,
  name: '北京→上海 机票',
  category: 'flight',
  price: 1280,
  description: '首都机场至虹桥机场 CA1234',
})

test.describe('机票浏览与详情', () => {
  test.beforeEach(async ({ page }) => {
    await setupAuthMocks(page)
    await setupFlightMocks(page)

    // 商品搜索接口仅返回一条机票商品，便于定位跳转
    await page.route('**/api/product/products/search**', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiSuccess([flightProduct])),
      })
    })

    await adminLogin(page)
  })

  test('点击机票商品应跳转到机票详情页', async ({ page }) => {
    await page.goto('/product/list')
    await waitForSubAppMount(page)
    await page.waitForTimeout(2000)

    // 点击"查看详情"
    const detailBtn = page
      .locator('#subapp-container')
      .getByText(/查看详情|view detail/i)
      .first()
    await expect(detailBtn).toBeVisible({ timeout: 10_000 })
    await detailBtn.click()

    // 验证 URL 变为机票详情路由（而非普通商品详情）
    await expect(page).toHaveURL(/\/product\/flight\/7/, { timeout: 10_000 })
  })

  test('机票详情页应渲染航班基础信息', async ({ page }) => {
    await page.goto('/product/flight/7')
    await waitForSubAppMount(page)
    await page.waitForTimeout(2000)

    const container = page.locator('#subapp-container')
    // 航班号与城市应出现在航班信息卡中
    await expect(container).toContainText(/CA1234/, { timeout: 10_000 })
    await expect(container).toContainText(/北京/, { timeout: 10_000 })
    await expect(container).toContainText(/上海/, { timeout: 10_000 })
  })
})
