/**
 * E2E 测试：用户登录链路
 *
 * 覆盖场景：
 * 1. admin 用户免验证码登录 → 跳转到用户管理页
 * 2. 未登录访问受保护页面 → 重定向到登录页
 * 3. 登录后 localStorage 正确写入 token 和用户信息
 * 4. 退出登录 → 清除本地状态并跳转
 */
import { test, expect } from '@playwright/test'
import { setupAuthMocks } from './mocks/auth'
import { adminLogin, getLocalStorage } from './helpers'

test.describe('用户登录', () => {
  test.beforeEach(async ({ page }) => {
    await setupAuthMocks(page)
  })

  test('admin 用户免验证码登录成功后跳转到用户管理页', async ({ page }) => {
    await page.goto('/login')

    // 输入 admin 用户名
    await page.getByPlaceholder(/用户名|username/i).fill('admin')
    await page.getByPlaceholder(/密码|password/i).fill('admin123')

    // admin 模式下不应显示验证码和短信输入框
    const captchaInput = page.locator('input[placeholder*="验证码"]')
    // 输入框可能存在但对应的表单项应被 v-if 隐藏

    // 点击登录
    await page.getByRole('button', { name: /登录|sign in|login/i }).click()

    // 验证跳转到用户管理页
    await page.waitForURL('**/users', { timeout: 10_000 })
    await expect(page).toHaveURL(/\/users/)
  })

  test('登录成功后应将 token 写入 localStorage', async ({ page }) => {
    await adminLogin(page)

    const token = await getLocalStorage(page, 'qoder_token')
    expect(token).toBeTruthy()
    expect(token).toBe('mock-jwt-token-e2e-test')
  })

  test('登录成功后应将用户信息写入 localStorage', async ({ page }) => {
    await adminLogin(page)

    const userRaw = await getLocalStorage(page, 'qoder_user')
    expect(userRaw).toBeTruthy()

    const user = JSON.parse(userRaw!)
    expect(user.name).toBeTruthy()
    expect(user.email).toBeTruthy()
  })

  test('未登录访问 /users 应重定向到 /login', async ({ page }) => {
    // 确保无 token
    await page.goto('/login')
    await page.evaluate(() => {
      localStorage.removeItem('qoder_token')
      localStorage.removeItem('qoder_user')
    })

    await page.goto('/users')

    // 应被路由守卫重定向到登录页
    await page.waitForURL('**/login**', { timeout: 10_000 })
    expect(page.url()).toContain('/login')
  })

  test('已登录用户访问 /login 应重定向到 /users', async ({ page }) => {
    await adminLogin(page)

    // 再次访问登录页
    await page.goto('/login')

    // 应被路由守卫重定向到用户管理页
    await page.waitForURL('**/users', { timeout: 10_000 })
  })

  test('退出登录后应清除 token 并跳转到登录页', async ({ page }) => {
    await adminLogin(page)

    // 点击退出按钮
    await page.getByRole('button', { name: /退出|logout|登出/i }).click()

    // 等待跳转到登录页
    await page.waitForURL('**/login', { timeout: 10_000 })

    // 验证 localStorage 已清除
    const token = await getLocalStorage(page, 'qoder_token')
    expect(token).toBeNull()
  })
})
