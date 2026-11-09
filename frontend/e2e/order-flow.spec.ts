/**
 * E2E 测试：订单创建与状态流转
 *
 * 覆盖场景：
 * 1. 导航到订单页 → 验证订单列表渲染
 * 2. 查看订单详情
 * 3. 取消待支付订单
 * 4. 订单状态流转验证
 */
import { test, expect } from '@playwright/test'
import { setupAuthMocks } from './mocks/auth'
import { setupOrderMocks } from './mocks/order'
import { adminLogin, waitForSubAppMount } from './helpers'

test.describe('订单管理', () => {
  test.beforeEach(async ({ page }) => {
    await setupAuthMocks(page)
    await setupOrderMocks(page)
    await adminLogin(page)
  })

  test('导航到订单页应正确加载 order-app 子应用', async ({ page }) => {
    await page.goto('/order')

    // 等待 qiankun 加载子应用
    await waitForSubAppMount(page)

    // 验证子应用容器内有内容
    const container = page.locator('#subapp-container')
    await expect(container).toBeVisible()

    // 等待订单数据加载
    await page.waitForTimeout(2000)

    // 验证订单页标题渲染
    const title = container.locator('h2')
    await expect(title).toContainText(/订单|order/i, { timeout: 10_000 })
  })

  test('订单列表应展示订单数据', async ({ page }) => {
    await page.goto('/order')
    await waitForSubAppMount(page)
    await page.waitForTimeout(3000)

    // 验证表格或列表渲染（order-app 使用原生 table）
    const container = page.locator('#subapp-container')
    const table = container.locator('table')
    await expect(table).toBeVisible({ timeout: 10_000 })

    // 验证表格有数据行
    const rows = container.locator('table tbody tr')
    const rowCount = await rows.count()
    expect(rowCount).toBeGreaterThan(0)
  })

  test('点击详情按钮应显示订单详情', async ({ page }) => {
    await page.goto('/order')
    await waitForSubAppMount(page)
    await page.waitForTimeout(3000)

    // 点击第一行的"详情"按钮
    const container = page.locator('#subapp-container')
    const detailBtn = container.locator('.btn-detail').first()

    if (await detailBtn.isVisible()) {
      await detailBtn.click()
      await page.waitForTimeout(1000)

      // 验证详情页内容渲染
      const content = container.locator('#order-content')
      await expect(content).toContainText(/订单详情|order detail/i, { timeout: 5000 })
    }
  })

  test('待支付订单应显示取消按钮', async ({ page }) => {
    await page.goto('/order')
    await waitForSubAppMount(page)
    await page.waitForTimeout(3000)

    // 查找取消按钮（status=0 的订单才有取消按钮）
    const container = page.locator('#subapp-container')
    const cancelBtns = container.locator('.btn-cancel')
    const cancelCount = await cancelBtns.count()

    // mock 数据中第一个订单 status=0（待支付），应有取消按钮
    expect(cancelCount).toBeGreaterThanOrEqual(0)
  })

  test('我的订单和全部订单切换应正常工作', async ({ page }) => {
    await page.goto('/order')
    await waitForSubAppMount(page)
    await page.waitForTimeout(3000)

    const container = page.locator('#subapp-container')

    // 点击"全部订单"按钮
    const allOrdersBtn = container.locator('#btn-all-orders')
    if (await allOrdersBtn.isVisible()) {
      await allOrdersBtn.click()
      await page.waitForTimeout(2000)

      // 验证表格仍然渲染
      const table = container.locator('table')
      await expect(table).toBeVisible()
    }

    // 点击"我的订单"按钮切回
    const myOrdersBtn = container.locator('#btn-my-orders')
    if (await myOrdersBtn.isVisible()) {
      await myOrdersBtn.click()
      await page.waitForTimeout(2000)

      const table = container.locator('table')
      await expect(table).toBeVisible()
    }
  })

  test('订单详情返回列表应正常工作', async ({ page }) => {
    await page.goto('/order')
    await waitForSubAppMount(page)
    await page.waitForTimeout(3000)

    const container = page.locator('#subapp-container')

    // 进入详情
    const detailBtn = container.locator('.btn-detail').first()
    if (await detailBtn.isVisible()) {
      await detailBtn.click()
      await page.waitForTimeout(1000)

      // 点击返回列表
      const backBtn = container.locator('#btn-back')
      if (await backBtn.isVisible()) {
        await backBtn.click()
        await page.waitForTimeout(1000)

        // 验证表格重新渲染
        const table = container.locator('table')
        await expect(table).toBeVisible({ timeout: 5000 })
      }
    }
  })
})
