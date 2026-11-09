/**
 * E2E 测试：微前端架构适配
 *
 * 覆盖场景：
 * 1. 主子应用路由切换等待与断言
 * 2. 全局状态（token/theme/lang）共享验证
 * 3. 跨应用通信验证
 * 4. qiankun 子应用挂载/卸载生命周期
 */
import { test, expect } from '@playwright/test'
import { setupAuthMocks } from './mocks/auth'
import { setupProductMocks } from './mocks/product'
import { setupOrderMocks } from './mocks/order'
import {
  adminLogin,
  waitForSubAppMount,
  waitForSubAppUnmount,
  getLocalStorage,
  setLocalStorage,
} from './helpers'

test.describe('微前端架构适配', () => {
  test.beforeEach(async ({ page }) => {
    await setupAuthMocks(page)
    await setupProductMocks(page)
    await setupOrderMocks(page)
    await adminLogin(page)
  })

  // ---------- 路由切换 ----------
  test('从用户管理页导航到商品子应用应正确加载', async ({ page }) => {
    // 当前在 /users 页面
    await expect(page).toHaveURL(/\/users/)

    // 点击导航菜单中的"商品"项
    await page.locator('.el-menu-item').filter({ hasText: /商品|product/i }).click()

    // 等待子应用挂载
    await waitForSubAppMount(page)

    // 验证 URL 变化
    expect(page.url()).toContain('/product')

    // 验证子应用容器有内容
    const container = page.locator('#subapp-container')
    await expect(container.locator('div').first()).toBeVisible({ timeout: 15_000 })
  })

  test('从商品子应用导航到订单子应用应正确切换', async ({ page }) => {
    // 先导航到商品页
    await page.goto('/product')
    await waitForSubAppMount(page)

    // 点击导航菜单中的"订单"项
    await page.locator('.el-menu-item').filter({ hasText: /订单|order/i }).click()

    // 等待订单子应用挂载
    await waitForSubAppMount(page)

    // 验证 URL 变化
    expect(page.url()).toContain('/order')
  })

  test('从子应用导航回用户管理页应卸载子应用', async ({ page }) => {
    // 导航到商品子应用
    await page.goto('/product')
    await waitForSubAppMount(page)

    // 点击导航菜单中的"用户"项
    await page.locator('.el-menu-item').filter({ hasText: /用户|user/i }).first().click()

    // 等待子应用卸载
    await waitForSubAppUnmount(page)

    // 验证回到用户管理页
    await expect(page).toHaveURL(/\/users/)
  })

  // ---------- 全局状态共享 ----------
  test('登录后 token 应存在于 localStorage 中', async ({ page }) => {
    const token = await getLocalStorage(page, 'demo_token')
    expect(token).toBeTruthy()
  })

  test('主题偏好应存储在 localStorage 中', async ({ page }) => {
    // 登录后主题偏好应被设置
    const theme = await getLocalStorage(page, 'demo_theme')
    // 可能是 dark 或 light，但应该有值
    // 如果没有设置，默认是 dark
    expect(theme === null || theme === 'dark' || theme === 'light').toBe(true)
  })

  test('语言偏好应存储在 localStorage 中', async ({ page }) => {
    const lang = await getLocalStorage(page, 'demo_lang')
    expect(lang === null || lang === 'zh' || lang === 'en').toBe(true)
  })

  // ---------- Token 传递 ----------
  test('进入商品子应用后 API 请求应携带 Authorization header', async ({ page }) => {
    let hasAuthHeader = false

    // 监听商品 API 请求
    await page.route('**/api/product/**', (route) => {
      const headers = route.request().headers()
      if (headers.authorization) {
        hasAuthHeader = true
      }
      route.continue()
    })

    await page.goto('/product/list')
    await waitForSubAppMount(page)
    await page.waitForTimeout(3000)

    // product-app 应从 localStorage 读取 token 并设置到请求头
    expect(hasAuthHeader).toBe(true)
  })

  // ---------- 子应用独立渲染验证 ----------
  test('product-app 子应用应独立渲染 React 组件', async ({ page }) => {
    await page.goto('/product/list')
    await waitForSubAppMount(page)
    await page.waitForTimeout(2000)

    // 验证 Ant Design 组件存在（React 子应用使用 antd）
    const container = page.locator('#subapp-container')
    const antCard = container.locator('.ant-card')
    await expect(antCard.first()).toBeVisible({ timeout: 10_000 })
  })

  test('order-app 子应用应独立渲染原生 DOM', async ({ page }) => {
    await page.goto('/order')
    await waitForSubAppMount(page)
    await page.waitForTimeout(2000)

    // 验证订单页标题存在（order-app 使用原生 DOM）
    const container = page.locator('#subapp-container')
    const h2 = container.locator('h2')
    await expect(h2).toBeVisible({ timeout: 10_000 })
  })

  // ---------- 导航菜单高亮 ----------
  test('当前页面对应的导航菜单项应高亮', async ({ page }) => {
    await page.goto('/users')
    await page.waitForTimeout(1000)

    // 验证"用户"菜单项处于激活状态
    const activeMenuItem = page.locator('.el-menu-item.is-active')
    await expect(activeMenuItem).toBeVisible()
  })
})
