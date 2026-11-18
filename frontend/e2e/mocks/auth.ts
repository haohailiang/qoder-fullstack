/**
 * 认证相关 API Mock Handlers
 *
 * 用于 Playwright page.route() 拦截，模拟后端认证接口。
 */
import { type Page } from '@playwright/test'
import { mockLoginResult, mockAdminUser, mockCaptcha, mockUser, apiSuccess } from '../fixtures/test-data'

/**
 * 注册认证相关的 API mock 路由拦截。
 */
export async function setupAuthMocks(page: Page): Promise<void> {
  // 获取图片验证码
  await page.route('**/api/user/auth/captcha', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiSuccess(mockCaptcha())),
    })
  })

  // 发送短信验证码
  await page.route('**/api/user/auth/send-sms-code', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiSuccess(null)),
    })
  })

  // 登录
  await page.route('**/api/user/auth/login', (route) => {
    const body = route.request().postDataJSON()
    // admin 用户登录
    if (body.username === 'admin') {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiSuccess(mockLoginResult(mockAdminUser()))),
      })
    } else {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiSuccess(mockLoginResult())),
      })
    }
  })

  // 注册
  await page.route('**/api/user/auth/register', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiSuccess(mockUser())),
    })
  })

  // 获取当前用户
  await page.route('**/api/user/auth/me', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiSuccess(mockUser())),
    })
  })

  // 退出登录
  await page.route('**/api/user/auth/logout', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiSuccess(null)),
    })
  })

  // 主题偏好
  await page.route('**/api/user/users/theme', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiSuccess({ theme: 'dark' })),
    })
  })

  // 语言偏好
  await page.route('**/api/user/users/language', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiSuccess({ language: 'zh' })),
    })
  })
}
