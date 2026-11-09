/**
 * 用户管理相关 API Mock Handlers
 */
import { type Page } from '@playwright/test'
import { mockUser, apiSuccess } from '../fixtures/test-data'

/**
 * 注册用户管理相关的 API mock 路由拦截。
 */
export async function setupUserMocks(page: Page): Promise<void> {
  const users = [
    mockUser({ id: 1, username: 'admin', name: '管理员', email: 'admin@example.com' }),
    mockUser({ id: 2, username: 'user1', name: '用户一', email: 'user1@example.com', phone: '13900139001', age: 28 }),
    mockUser({ id: 3, username: 'user2', name: '用户二', email: 'user2@example.com', phone: '13900139002', age: 32 }),
  ]

  // 获取用户列表
  await page.route('**/api/user/users', (route) => {
    if (route.request().method() === 'GET') {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiSuccess(users)),
      })
    } else if (route.request().method() === 'POST') {
      // 创建用户
      const body = route.request().postDataJSON()
      const newUser = mockUser({ id: users.length + 1, ...body })
      users.push(newUser)
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiSuccess(newUser)),
      })
    }
  })

  // 获取/更新/删除单个用户
  await page.route('**/api/user/users/:id', (route) => {
    const url = route.request().url()
    const idMatch = url.match(/\/users\/(\d+)/)
    const id = idMatch ? parseInt(idMatch[1]) : 0

    if (route.request().method() === 'GET') {
      const user = users.find((u) => u.id === id)
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiSuccess(user || null)),
      })
    } else if (route.request().method() === 'PUT') {
      const body = route.request().postDataJSON()
      const user = users.find((u) => u.id === id)
      if (user) {
        Object.assign(user, body)
      }
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiSuccess(user || null)),
      })
    } else if (route.request().method() === 'DELETE') {
      const index = users.findIndex((u) => u.id === id)
      if (index >= 0) users.splice(index, 1)
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiSuccess(null)),
      })
    }
  })

  // 短信发送记录
  await page.route('**/api/user/sms/records', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiSuccess([
        { phone: '13800138000', code: '123456', sentAt: new Date().toISOString(), status: 'SUCCESS' },
        { phone: '13900139001', code: '654321', sentAt: new Date(Date.now() - 60000).toISOString(), status: 'SUCCESS' },
      ])),
    })
  })
}
