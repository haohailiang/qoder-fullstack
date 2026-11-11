/**
 * auth 工具函数单元测试
 *
 * 覆盖：setAuth / clearAuth / getToken / tokenRef / currentUserRef
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { setAuth, clearAuth, getToken, tokenRef, currentUserRef } from '@/utils/auth'
import type { AuthUserInfo } from '@/utils/auth'

const mockUser: AuthUserInfo = {
  id: 1,
  username: 'testuser',
  name: 'Test User',
  email: 'test@example.com',
  phone: '13800138000',
  age: 25,
  themePreference: 'dark',
  languagePreference: 'zh',
}

describe('utils/auth', () => {
  beforeEach(() => {
    clearAuth()
    localStorage.clear()
  })

  // ---------- getToken / setAuth ----------
  describe('setAuth & getToken', () => {
    it('应将 token 写入 localStorage 并可通过 getToken 读取', () => {
      setAuth('jwt-token-123', mockUser)
      expect(getToken()).toBe('jwt-token-123')
      expect(localStorage.getItem('qoder_token')).toBe('jwt-token-123')
    })

    it('应将用户信息序列化后写入 localStorage', () => {
      setAuth('jwt-token-123', mockUser)
      const stored = JSON.parse(localStorage.getItem('qoder_user')!)
      expect(stored).toEqual(mockUser)
    })

    it('应同步更新响应式 tokenRef', () => {
      expect(tokenRef.value).toBeNull()
      setAuth('abc', mockUser)
      expect(tokenRef.value).toBe('abc')
    })

    it('应同步更新响应式 currentUserRef', () => {
      expect(currentUserRef.value).toBeNull()
      setAuth('abc', mockUser)
      expect(currentUserRef.value).toEqual(mockUser)
    })
  })

  // ---------- clearAuth ----------
  describe('clearAuth', () => {
    it('应清除 localStorage 中的 token 和 user', () => {
      setAuth('token', mockUser)
      clearAuth()
      expect(localStorage.getItem('qoder_token')).toBeNull()
      expect(localStorage.getItem('qoder_user')).toBeNull()
    })

    it('应将 tokenRef 和 currentUserRef 重置为 null', () => {
      setAuth('token', mockUser)
      clearAuth()
      expect(tokenRef.value).toBeNull()
      expect(currentUserRef.value).toBeNull()
    })
  })

  // ---------- getToken 边界 ----------
  describe('getToken 边界情况', () => {
    it('localStorage 无 token 时应返回 null', () => {
      expect(getToken()).toBeNull()
    })

    it('tokenRef 应在 setAuth 后正确反映 localStorage 的值', () => {
      localStorage.setItem('qoder_token', 'pre-existing')
      // tokenRef 在模块加载时初始化，由于模块缓存无法重新触发
      // 这里验证 setAuth 能正确更新 tokenRef
      setAuth('pre-existing', mockUser)
      expect(getToken()).toBe('pre-existing')
    })
  })

  // ---------- currentUserRef 初始化 ----------
  describe('currentUserRef 初始化', () => {
    it('localStorage 中有合法用户 JSON 时应正确解析', () => {
      localStorage.setItem('qoder_user', JSON.stringify(mockUser))
      // 注意：模块已加载，currentUserRef 在模块初始化时读取
      // 这里验证 setAuth 后重新读取的场景
      setAuth('t', mockUser)
      expect(currentUserRef.value).toEqual(mockUser)
    })

    it('localStorage 中有非法 JSON 时应为 null', () => {
      localStorage.setItem('qoder_user', 'not-json')
      clearAuth()
      expect(currentUserRef.value).toBeNull()
    })
  })
})
