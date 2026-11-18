/**
 * E2E 测试：支付宝支付链路
 *
 * 覆盖场景（全部走 mock，不依赖真实沙箱）：
 * 1. 商品详情页「立即购买」→ 创建订单 → 创建支付 → 跳转结果页 → 显示支付成功
 * 2. 订单列表「去支付」→ 跳转结果页 → 显示支付成功 → 可进入订单详情
 * 3. 支付宝未配置（业务码 503）→ 弹窗提示，停留在原页面
 * 4. 秒杀入口仍走原秒杀接口，不受支付改造影响
 */
import { test, expect } from '@playwright/test'
import { setupAuthMocks } from './mocks/auth'
import { setupProductMocks } from './mocks/product'
import { mockAlipayNotConfigured, setupOrderMocks } from './mocks/order'
import { adminLogin, waitForSubAppMount } from './helpers'

/** 结果页文案（兼容中英文） */
const PAY_SUCCESS = /支付成功|payment successful/i
const PAY_UNAVAILABLE = /支付宝|alipay/i

test.describe('支付宝支付', () => {
  test.beforeEach(async ({ page }) => {
    await setupAuthMocks(page)
    await setupProductMocks(page)
    await setupOrderMocks(page)
    await adminLogin(page)
  })

  test('商品详情页立即购买应跳转支付结果页并显示支付成功', async ({ page }) => {
    await page.goto('/product/list')
    await waitForSubAppMount(page)
    await page.waitForTimeout(2000)

    // 进入第一个商品详情（product-app 在 qiankun 下使用 MemoryRouter，需从列表点击进入）
    const container = page.locator('#subapp-container')
    await container.getByText(/查看详情|view detail/i).first().click()
    await page.waitForTimeout(1000)

    // 点击「立即购买」
    await container.getByRole('button', { name: /立即购买|buy now/i }).click()

    // 创建支付后整页跳转到结果页
    await page.waitForURL('**/order/pay-result**', { timeout: 20_000 })
    await waitForSubAppMount(page)

    // 结果页主动查询后展示支付成功
    await expect(page.locator('#pay-status')).toContainText(PAY_SUCCESS, { timeout: 20_000 })
    // 成功态提供「查看订单详情」入口
    await expect(page.locator('#btn-pay-view-order')).toBeVisible()
  })

  test('订单列表去支付应跳转支付结果页并显示支付成功', async ({ page }) => {
    await page.goto('/order')
    await waitForSubAppMount(page)
    await page.waitForTimeout(2000)

    // mock 数据第一单为待支付，应有「去支付」按钮
    const payBtn = page.locator('#subapp-container .btn-pay').first()
    await expect(payBtn).toBeVisible({ timeout: 10_000 })
    await payBtn.click()

    await page.waitForURL('**/order/pay-result**', { timeout: 20_000 })
    await waitForSubAppMount(page)

    await expect(page.locator('#pay-status')).toContainText(PAY_SUCCESS, { timeout: 20_000 })

    // 查看订单详情：整页跳转到 /order/detail/{orderNo}
    await page.locator('#btn-pay-view-order').click()
    await page.waitForURL('**/order/detail/**', { timeout: 20_000 })
    await waitForSubAppMount(page)
    await expect(page.locator('#order-content')).toContainText(/订单详情|order detail/i, {
      timeout: 15_000,
    })
  })

  test('支付宝未配置时下单应提示且不跳转收银台', async ({ page }) => {
    // 覆盖默认支付 mock：创建支付返回业务码 503
    await mockAlipayNotConfigured(page)

    let dialogMessage = ''
    page.on('dialog', async (dialog) => {
      dialogMessage = dialog.message()
      await dialog.dismiss()
    })

    await page.goto('/order')
    await waitForSubAppMount(page)
    await page.waitForTimeout(2000)

    const payBtn = page.locator('#subapp-container .btn-pay').first()
    await expect(payBtn).toBeVisible({ timeout: 10_000 })
    await payBtn.click()

    await expect.poll(() => dialogMessage, { timeout: 10_000 }).toMatch(PAY_UNAVAILABLE)
    // 未跳转结果页，仍停留在订单列表
    expect(page.url()).not.toContain('/order/pay-result')
  })

  test('秒杀入口仍走原秒杀接口', async ({ page }) => {
    let seckillCalled = false
    // 后注册的 handler 优先，覆盖 setupProductMocks 中的秒杀实现并记录调用
    await page.route('**/api/product/products/seckill/**', (route) => {
      seckillCalled = true
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'ok',
          data: { message: '秒杀成功', orderNo: 'SECKILL-ORD-001' },
        }),
      })
    })

    await page.goto('/product/list')
    await waitForSubAppMount(page)
    await page.waitForTimeout(2000)

    // 商品 2 在 mock 中是秒杀商品，进入详情后应展示「立即抢购」
    const container = page.locator('#subapp-container')
    await container.getByText(/查看详情|view detail/i).nth(1).click()
    await page.waitForTimeout(1000)

    await container.getByRole('button', { name: /立即抢购|flash buy/i }).click()

    await expect.poll(() => seckillCalled, { timeout: 10_000 }).toBe(true)
    // 秒杀链路不经过支付宝支付
    expect(page.url()).not.toContain('/order/pay-result')
  })
})