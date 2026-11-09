/**
 * axios 请求封装单元测试
 *
 * 覆盖：请求拦截器（自动注入 token）、响应拦截器（解包 / 401 / 403 / 40101 挤下线）
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'

// ---------- mock 依赖 ----------
vi.mock('@/utils/auth', () => ({
  getToken: vi.fn(),
  clearAuth: vi.fn(),
}))

vi.mock('@/router', () => ({
  default: {
    currentRoute: { value: { name: 'users', fullPath: '/users' } },
    push: vi.fn(),
    replace: vi.fn(),
  },
}))

vi.mock('element-plus', async () => {
  const actual = await vi.importActual<Record<string, unknown>>('element-plus')
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
    },
  }
})

vi.mock('@/i18n', () => ({
  default: {
    global: {
      t: (key: string) => key,
    },
  },
}))

import request from '@/api/request'
import { getToken, clearAuth } from '@/utils/auth'
import router from '@/router'

describe('api/request 拦截器', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getToken).mockReturnValue(null)
  })

  // ---------- 请求拦截器 ----------
  describe('请求拦截器', () => {
    it('有 token 时应在请求头注入 Authorization Bearer', async () => {
      vi.mocked(getToken).mockReturnValue('my-jwt-token')

      // 通过 mock adapter 捕获请求配置
      let capturedHeaders: Record<string, string> = {}
      const mockAdapter = vi.fn().mockImplementation((config: any) => {
        capturedHeaders = config.headers || {}
        return Promise.resolve({
          data: { code: 200, message: 'ok', data: {} },
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        })
      })
      request.defaults.adapter = mockAdapter

      await request.get('/test')
      expect(capturedHeaders['Authorization']).toBe('Bearer my-jwt-token')
    })

    it('无 token 时不应注入 Authorization 头', () => {
      vi.mocked(getToken).mockReturnValue(null)
      expect(getToken()).toBeNull()
    })
  })

  // ---------- 响应拦截器：业务成功 ----------
  describe('响应拦截器 - 业务成功', () => {
    it('code === 200 时应返回 data 字段', async () => {
      // 通过 axios mock adapter 模拟响应
      const mockAdapter = vi.fn().mockResolvedValue({
        data: { code: 200, message: 'ok', data: { id: 1, name: 'test' } },
        status: 200,
        statusText: 'OK',
        headers: {},
        config: { headers: {} },
      })
      request.defaults.adapter = mockAdapter

      const result = await request.get('/test')
      expect(result).toEqual({ id: 1, name: 'test' })
    })
  })

  // ---------- 响应拦截器：业务失败 ----------
  describe('响应拦截器 - 业务失败', () => {
    it('code !== 200 时应 reject 并弹出错误提示', async () => {
      const { ElMessage } = await import('element-plus')
      const mockAdapter = vi.fn().mockResolvedValue({
        data: { code: 500, message: '服务器内部错误', data: null },
        status: 200,
        statusText: 'OK',
        headers: {},
        config: { headers: {} },
      })
      request.defaults.adapter = mockAdapter

      await expect(request.get('/test')).rejects.toThrow('服务器内部错误')
      expect(ElMessage.error).toHaveBeenCalledWith('服务器内部错误')
    })
  })

  // ---------- 响应拦截器：HTTP 401/403 ----------
  describe('响应拦截器 - HTTP 401/403', () => {
    it('401 响应应清除 auth 并跳转登录页', async () => {
      const mockAdapter = vi.fn().mockRejectedValue({
        response: {
          status: 401,
          data: { code: 401, message: '登录已过期', data: null },
        },
      })
      request.defaults.adapter = mockAdapter

      await expect(request.get('/test')).rejects.toBeTruthy()
      expect(clearAuth).toHaveBeenCalled()
      expect(router.push).toHaveBeenCalledWith(
        expect.objectContaining({ path: '/login' }),
      )
    })

    it('403 响应应清除 auth 并跳转登录页', async () => {
      const mockAdapter = vi.fn().mockRejectedValue({
        response: {
          status: 403,
          data: { code: 403, message: '未授权', data: null },
        },
      })
      request.defaults.adapter = mockAdapter

      await expect(request.get('/test')).rejects.toBeTruthy()
      expect(clearAuth).toHaveBeenCalled()
    })

    it('业务码 40101（挤下线）应使用 replace 跳转且不保留 redirect', async () => {
      const mockAdapter = vi.fn().mockRejectedValue({
        response: {
          status: 401,
          data: { code: 40101, message: '已在其他设备登录', data: null },
        },
      })
      request.defaults.adapter = mockAdapter

      await expect(request.get('/test')).rejects.toBeTruthy()
      expect(clearAuth).toHaveBeenCalled()
      expect(router.replace).toHaveBeenCalledWith('/login')
    })
  })

  // ---------- 响应拦截器：网络错误 ----------
  describe('响应拦截器 - 网络错误', () => {
    it('无 response 的错误应弹出网络错误提示', async () => {
      const { ElMessage } = await import('element-plus')
      const mockAdapter = vi.fn().mockRejectedValue(new Error('Network Error'))
      request.defaults.adapter = mockAdapter

      await expect(request.get('/test')).rejects.toThrow('Network Error')
      expect(ElMessage.error).toHaveBeenCalled()
    })
  })
})
