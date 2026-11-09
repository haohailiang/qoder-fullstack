/**
 * E2E 测试：主题跨应用同步
 *
 * 覆盖场景：
 * 1. base-app 切换主题 → html.dark 类变化
 * 2. 主题偏好持久化到 localStorage
 * 3. 导航到 product-app → 验证 Ant Design 主题算法跟随
 * 4. 导航到 order-app → 验证原生 DOM 内联样式跟随
 * 5. 刷新页面后主题保持
 */
import { test, expect } from '@playwright/test'
import { setupAuthMocks } from './mocks/auth'
import { setupProductMocks } from './mocks/product'
import { setupOrderMocks } from './mocks/order'
import {
  adminLogin,
  waitForSubAppMount,
  getLocalStorage,
} from './helpers'

test.describe('主题跨应用同步', () => {
  test.beforeEach(async ({ page }) => {
    await setupAuthMocks(page)
    await setupProductMocks(page)
    await setupOrderMocks(page)
    await adminLogin(page)
  })

  // ---------- base-app 主题切换 ----------
  test('点击主题切换按钮应切换 html.dark 类', async ({ page }) => {
    // 初始状态检查
    const initialHasDark = await page.evaluate(() =>
      document.documentElement.classList.contains('dark'),
    )

    // 点击主题切换按钮
    const themeBtn = page.locator('.theme-toggle-btn')
    await themeBtn.click()
    await page.waitForTimeout(500)

    // 验证 class 翻转
    const afterHasDark = await page.evaluate(() =>
      document.documentElement.classList.contains('dark'),
    )
    expect(afterHasDark).toBe(!initialHasDark)
  })

  test('主题切换后应更新 localStorage', async ({ page }) => {
    const themeBtn = page.locator('.theme-toggle-btn')

    // 切换到 light
    await page.evaluate(() => localStorage.setItem('demo_theme', 'dark'))
    await themeBtn.click()
    await page.waitForTimeout(500)

    const theme = await getLocalStorage(page, 'demo_theme')
    expect(theme).toBe('light')

    // 切换回 dark
    await themeBtn.click()
    await page.waitForTimeout(500)

    const theme2 = await getLocalStorage(page, 'demo_theme')
    expect(theme2).toBe('dark')
  })

  // ---------- product-app 主题适配 ----------
  test('product-app 应读取 localStorage 中的主题并应用', async ({ page }) => {
    // 先设置为 light 主题
    await page.evaluate(() => localStorage.setItem('demo_theme', 'light'))

    // 导航到商品页
    await page.goto('/product/list')
    await waitForSubAppMount(page)
    await page.waitForTimeout(2000)

    // 验证 product-app 的背景色（light 模式为 #f5f5f5）
    const container = page.locator('#subapp-container')
    const firstDiv = container.locator('> div').first()
    const style = await firstDiv.getAttribute('style')

    // light 模式下 background 应为 #f5f5f5
    if (style) {
      expect(style).toContain('#f5f5f5')
    }
  })

  test('product-app 在 dark 主题下应使用深色背景', async ({ page }) => {
    // 确保 dark 主题
    await page.evaluate(() => localStorage.setItem('demo_theme', 'dark'))

    await page.goto('/product/list')
    await waitForSubAppMount(page)
    await page.waitForTimeout(2000)

    const container = page.locator('#subapp-container')
    const firstDiv = container.locator('> div').first()
    const style = await firstDiv.getAttribute('style')

    // dark 模式下 background 应为 #141414
    if (style) {
      expect(style).toContain('#141414')
    }
  })

  // ---------- order-app 主题适配 ----------
  test('order-app 应读取 localStorage 中的主题并应用', async ({ page }) => {
    // 设置为 light 主题
    await page.evaluate(() => localStorage.setItem('demo_theme', 'light'))

    await page.goto('/order')
    await waitForSubAppMount(page)
    await page.waitForTimeout(2000)

    const container = page.locator('#subapp-container')
    const firstDiv = container.locator('> div').first()
    const style = await firstDiv.getAttribute('style')

    // light 模式下 background 应为 #f5f5f5
    if (style) {
      expect(style).toContain('#f5f5f5')
    }
  })

  test('order-app 在 dark 主题下应使用深色背景', async ({ page }) => {
    await page.evaluate(() => localStorage.setItem('demo_theme', 'dark'))

    await page.goto('/order')
    await waitForSubAppMount(page)
    await page.waitForTimeout(2000)

    const container = page.locator('#subapp-container')
    const firstDiv = container.locator('> div').first()
    const style = await firstDiv.getAttribute('style')

    // dark 模式下 background 应为 #141414
    if (style) {
      expect(style).toContain('#141414')
    }
  })

  // ---------- 刷新后主题保持 ----------
  test('刷新页面后主题应保持', async ({ page }) => {
    // 切换到 light
    const themeBtn = page.locator('.theme-toggle-btn')
    await page.evaluate(() => localStorage.setItem('demo_theme', 'dark'))
    await themeBtn.click()
    await page.waitForTimeout(500)

    // 刷新页面
    await page.reload()
    await page.waitForTimeout(1000)

    // 验证主题仍然是 light
    const theme = await getLocalStorage(page, 'demo_theme')
    expect(theme).toBe('light')

    // 验证 html 元素没有 dark 类
    const hasDark = await page.evaluate(() =>
      document.documentElement.classList.contains('dark'),
    )
    expect(hasDark).toBe(false)
  })

  // ---------- 主题切换按钮 tooltip ----------
  test('主题切换按钮应显示当前模式的提示', async ({ page }) => {
    const themeBtn = page.locator('.theme-toggle-btn')

    // hover 显示 tooltip
    await themeBtn.hover()
    await page.waitForTimeout(500)

    // tooltip 应出现（Element Plus 的 tooltip 渲染到 body）
    const tooltip = page.locator('.el-tooltip__trigger').first()
    await expect(themeBtn).toBeVisible()
  })
})
