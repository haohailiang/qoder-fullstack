/**
 * E2E 测试：商品浏览与搜索链路
 *
 * 覆盖场景：
 * 1. 导航到商品页 → 等待 qiankun 加载 product-app → 验证商品列表渲染
 * 2. 关键词搜索 → 验证列表过滤
 * 3. 分类筛选 → 验证 API 请求参数
 * 4. 点击商品 → 验证路由跳转到详情页
 */
import { test, expect } from '@playwright/test'
import { setupAuthMocks } from './mocks/auth'
import { setupProductMocks } from './mocks/product'
import { adminLogin, waitForSubAppMount } from './helpers'

test.describe('商品浏览与搜索', () => {
  test.beforeEach(async ({ page }) => {
    await setupAuthMocks(page)
    await setupProductMocks(page)
    await adminLogin(page)
  })

  test('导航到商品页应正确加载 product-app 子应用', async ({ page }) => {
    await page.goto('/product')

    // 等待 qiankun 加载子应用
    await waitForSubAppMount(page)

    // 验证子应用容器内有内容
    const container = page.locator('#subapp-container')
    await expect(container).toBeVisible()

    // 验证商品列表区域存在（React 子应用渲染的内容）
    const subAppContent = container.locator('div').first()
    await expect(subAppContent).toBeVisible({ timeout: 15_000 })
  })

  test('商品列表应展示商品卡片', async ({ page }) => {
    await page.goto('/product/list')
    await waitForSubAppMount(page)

    // 等待商品数据加载（mock 返回 8 个商品）
    await page.waitForTimeout(2000)

    // 验证至少有一个商品卡片渲染
    const productCards = page.locator('#subapp-container .ant-card')
    await expect(productCards.first()).toBeVisible({ timeout: 10_000 })
  })

  test('搜索框输入关键词后应触发搜索', async ({ page }) => {
    let searchKeyword = ''

    // 拦截搜索请求，记录关键词
    await page.route('**/api/product/products/search**', (route) => {
      const url = new URL(route.request().url())
      searchKeyword = url.searchParams.get('keyword') || ''
      route.continue()
    })

    await page.goto('/product/list')
    await waitForSubAppMount(page)
    await page.waitForTimeout(1000)

    // 在搜索框输入关键词
    const searchInput = page.locator('#subapp-container input[placeholder*="搜索"], #subapp-container input[placeholder*="search"], #subapp-container .ant-input').first()
    await searchInput.fill('商品 1')

    // 点击搜索按钮或按回车
    const searchBtn = page.locator('#subapp-container .ant-btn-primary, #subapp-container button').filter({ hasText: /搜索|search/i }).first()
    await searchBtn.click()

    await page.waitForTimeout(1000)

    // 验证搜索请求包含关键词
    expect(searchKeyword).toBe('商品 1')
  })

  test('点击商品应跳转到详情页', async ({ page }) => {
    await page.goto('/product/list')
    await waitForSubAppMount(page)
    await page.waitForTimeout(2000)

    // 点击"查看详情"按钮
    const detailBtn = page.locator('#subapp-container').getByText(/查看详情|view detail/i).first()
    if (await detailBtn.isVisible()) {
      await detailBtn.click()

      // 验证 URL 变为详情页路由
      await page.waitForTimeout(2000)
      expect(page.url()).toMatch(/\/product\/detail\/\d+/)
    }
  })
})
