/**
 * E2E 测试辅助函数
 *
 * 封装 qiankun 微前端场景下的常用操作：
 * - 等待子应用挂载
 * - 登录（admin 免验证码）
 * - 路由导航
 */
import { type Page, type Locator, expect } from '@playwright/test'

/**
 * 等待 qiankun 子应用挂载完成。
 *
 * qiankun 加载子应用是异步的，需要等待 #subapp-container 内出现子应用的 DOM 节点。
 *
 * @param page Playwright Page 对象
 * @param timeout 超时时间（毫秒），默认 15000
 */
export async function waitForSubAppMount(page: Page, timeout = 15_000): Promise<void> {
  await page.waitForFunction(
    () => {
      const container = document.querySelector('#subapp-container')
      return container && container.children.length > 0
    },
    { timeout },
  )
}

/**
 * 等待 qiankun 子应用卸载完成。
 */
export async function waitForSubAppUnmount(page: Page, timeout = 10_000): Promise<void> {
  await page.waitForFunction(
    () => {
      const container = document.querySelector('#subapp-container')
      return !container || container.children.length === 0
    },
    { timeout },
  )
}

/**
 * 使用 admin 账号快速登录（跳过验证码）。
 *
 * @param page Playwright Page 对象
 * @param username 默认 'admin'
 * @param password 默认 'admin123'
 */
export async function adminLogin(page: Page, username = 'admin', password = 'admin123'): Promise<void> {
  await page.goto('/login')

  // 填写用户名和密码
  await page.getByPlaceholder(/用户名|username/i).fill(username)
  await page.getByPlaceholder(/密码|password/i).fill(password)

  // 点击登录按钮
  await page.getByRole('button', { name: /登录|sign in|login/i }).click()

  // 等待跳转到用户管理页
  await page.waitForURL('**/users', { timeout: 10_000 })
}

/**
 * 在 base-app 中导航到指定路径。
 */
export async function navigateTo(page: Page, path: string): Promise<void> {
  await page.goto(path)
}

/**
 * 等待页面中的指定文本出现。
 */
export async function waitForText(page: Page, text: string, timeout = 10_000): Promise<void> {
  await page.waitForFunction(
    (t) => document.body.textContent?.includes(t) ?? false,
    text,
    { timeout },
  )
}

/**
 * 获取 localStorage 中的值（在浏览器上下文中执行）。
 */
export function getLocalStorage(page: Page, key: string): Promise<string | null> {
  return page.evaluate((k) => localStorage.getItem(k), key)
}

/**
 * 设置 localStorage 中的值。
 */
export function setLocalStorage(page: Page, key: string, value: string): Promise<void> {
  return page.evaluate(([k, v]) => localStorage.setItem(k, v), [key, value] as const)
}

/**
 * 清除所有 demo_ 前缀的 localStorage 项。
 */
export function clearDemoStorage(page: Page): Promise<void> {
  return page.evaluate(() => {
    const keys: string[] = []
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key?.startsWith('demo_')) keys.push(key)
    }
    keys.forEach((k) => localStorage.removeItem(k))
  })
}

/**
 * 拦截 API 请求并返回 mock 响应。
 * 用于 E2E 测试中不依赖后端服务的场景。
 */
export async function mockApiResponse(
  page: Page,
  urlPattern: string | RegExp,
  responseBody: unknown,
  status = 200,
): Promise<void> {
  await page.route(urlPattern, (route) => {
    route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(responseBody),
    })
  })
}
