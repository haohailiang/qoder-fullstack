/**
 * E2E 测试：加载态（loading）展示与关闭
 *
 * 覆盖场景：
 * 1. product-app 慢接口 → 骨架屏出现，数据返回后消失
 * 2. product-app 快接口（<200ms）→ 不出现骨架屏（防闪烁）
 * 3. order-app 慢接口 → 首屏骨架出现，数据返回后消失
 * 4. order-app 切换订单范围 → 已有数据时展示居中遮罩，返回后关闭
 * 5. base-app 用户列表慢接口 → el-skeleton 出现，数据返回后消失
 * 6. qiankun 子应用加载占位 → 英文文案 + 暗色主题背景适配
 *
 * 慢接口通过 page.route 内 setTimeout 模拟，不依赖后端。
 */
import { test, expect } from '@playwright/test'
import { setupAuthMocks } from './mocks/auth'
import { setupProductMocks } from './mocks/product'
import { setupOrderMocks } from './mocks/order'
import { setupUserMocks } from './mocks/user'
import { apiSuccess, mockOrderList, mockProductList, mockUser } from './fixtures/test-data'
import {
  adminLogin,
  waitForSubAppMount,
  delayApiResponse,
  delayUrl,
} from './helpers'

test.describe('加载态展示与关闭', () => {
  test.beforeEach(async ({ page }) => {
    await setupAuthMocks(page)
    await setupProductMocks(page)
    await setupOrderMocks(page)
    await setupUserMocks(page)
    await adminLogin(page)
  })

  test('商品列表慢接口应展示骨架屏并在返回后隐藏', async ({ page }) => {
    // 后注册的 handler 优先，覆盖 setupProductMocks 中的快速响应
    await delayApiResponse(
      page,
      '**/api/product/products/search**',
      800,
      apiSuccess(mockProductList(4)),
    )

    await page.goto('/product/list')
    await waitForSubAppMount(page)

    const skeleton = page.locator('#subapp-container [data-testid="page-skeleton"]')
    await expect(skeleton).toBeVisible({ timeout: 6_000 })

    await expect(page.locator('#subapp-container [data-product-id]').first()).toBeVisible({
      timeout: 10_000,
    })
    await expect(skeleton).toHaveCount(0)
  })

  test('商品列表快速返回时不应出现骨架屏', async ({ page }) => {
    await delayApiResponse(
      page,
      '**/api/product/products/search**',
      50,
      apiSuccess(mockProductList(4)),
    )

    await page.goto('/product/list')
    await waitForSubAppMount(page)

    const skeleton = page.locator('#subapp-container [data-testid="page-skeleton"]')
    await expect(page.locator('#subapp-container [data-product-id]').first()).toBeVisible({
      timeout: 10_000,
    })
    // 超过 200ms 防闪烁阈值后仍不应出现骨架
    await page.waitForTimeout(400)
    await expect(skeleton).toHaveCount(0)
  })

  test('订单列表慢接口应展示首屏骨架并在返回后隐藏', async ({ page }) => {
    await delayApiResponse(page, '**/api/order/orders/my', 800, apiSuccess(mockOrderList(3)))

    await page.goto('/order')
    await waitForSubAppMount(page)

    const skeleton = page.locator('#order-content [data-oa-skeleton]')
    await expect(skeleton).toBeVisible({ timeout: 6_000 })

    await expect(page.locator('#order-content table')).toBeVisible({ timeout: 10_000 })
    await expect(skeleton).toHaveCount(0)
  })

  test('订单切换范围时应展示居中遮罩并在返回后关闭', async ({ page }) => {
    await page.goto('/order')
    await waitForSubAppMount(page)
    await expect(page.locator('#order-content table')).toBeVisible({ timeout: 10_000 })

    // 刷新场景走遮罩而不是骨架
    await delayApiResponse(page, '**/api/order/orders', 800, apiSuccess(mockOrderList(5)))

    await page.locator('#btn-all-orders').click()
    const overlay = page.locator('#order-content .oa-loading-overlay.is-visible')
    await expect(overlay).toBeVisible({ timeout: 6_000 })

    await expect(page.locator('#order-content table')).toBeVisible({ timeout: 10_000 })
    await expect(overlay).toHaveCount(0)
  })

  test('用户列表慢接口应展示骨架屏并在返回后隐藏', async ({ page }) => {
    const users = [
      mockUser({ id: 1, username: 'admin', name: '管理员', email: 'admin@example.com' }),
      mockUser({ id: 2, username: 'user1', name: '用户一', email: 'user1@example.com' }),
    ]
    await page.route('**/api/user/users', async (route) => {
      if (route.request().method() !== 'GET') {
        await route.fallback()
        return
      }
      await new Promise((resolve) => setTimeout(resolve, 800))
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiSuccess(users)),
      })
    })

    await page.goto('/users')

    const skeleton = page.locator('.el-skeleton').first()
    await expect(skeleton).toBeVisible({ timeout: 6_000 })

    const userTable = page.locator('.el-table').first()
    await expect(userTable.locator('.el-table__row').first()).toBeVisible({ timeout: 10_000 })
    await expect(page.locator('.el-skeleton')).toHaveCount(0)
  })

  test('子应用加载占位应展示并可随语言/主题切换', async ({ page }) => {
    // 登录后偏好会从后端拉取，这里让偏好接口返回英文 + 暗色，覆盖 initTheme/initLang
    await page.route('**/api/user/users/language', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiSuccess({ language: 'en' })),
      }),
    )
    await page.route('**/api/user/users/theme', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiSuccess({ theme: 'dark' })),
      }),
    )

    // 拖慢子应用 entry 资源，让占位层有稳定的可见窗口
    await delayUrl(page, 'http://localhost:7100/**', 600)

    await page.goto('/product')

    const placeholder = page.locator('.subapp-loading')
    await expect(placeholder).toBeVisible({ timeout: 6_000 })
    await expect(placeholder).toContainText(/正在加载子应用|Loading sub application/)

    // 暗色主题下占位背景不应是白色
    const bg = await placeholder.evaluate((el) => getComputedStyle(el).backgroundColor)
    expect(bg).not.toBe('rgb(255, 255, 255)')

    await waitForSubAppMount(page)
    await expect(placeholder).toHaveCount(0)
  })
})
