/**
 * lang 工具函数单元测试
 *
 * 覆盖：getLang / setLang / initLang / langRef
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { getLang, setLang, langRef } from '@/utils/lang'
import type { Lang } from '@/utils/lang'

// mock @/api/request
vi.mock('@/api/request', () => ({
  default: {
    put: vi.fn().mockResolvedValue({}),
    get: vi.fn().mockResolvedValue(null),
  },
}))

// mock @/i18n
vi.mock('@/i18n', () => {
  const localeRef = { value: 'zh' }
  return {
    default: {
      global: {
        locale: localeRef,
      },
    },
    LANG_KEY: 'demo_lang',
  }
})

describe('utils/lang', () => {
  beforeEach(() => {
    localStorage.clear()
    langRef.value = 'zh'
  })

  // ---------- getLang ----------
  describe('getLang', () => {
    it('无缓存时应返回默认值 zh', () => {
      expect(getLang()).toBe('zh')
    })

    it('langRef 更新后应返回最新值', () => {
      langRef.value = 'en'
      expect(getLang()).toBe('en')
    })
  })

  // ---------- setLang ----------
  describe('setLang', () => {
    it('应更新 langRef 响应式状态', async () => {
      await setLang('en')
      expect(langRef.value).toBe('en')
    })

    it('应写入 localStorage', async () => {
      await setLang('en')
      expect(localStorage.getItem('demo_lang')).toBe('en')
    })

    it('应同步更新 vue-i18n 的 locale', async () => {
      const i18n = (await import('@/i18n')).default
      await setLang('en')
      expect(i18n.global.locale.value).toBe('en')
    })

    it('应异步调用后端 API 持久化语言偏好', async () => {
      const request = (await import('@/api/request')).default
      await setLang('en')
      expect(request.put).toHaveBeenCalledWith('/user/users/language', { language: 'en' })
    })

    it('后端 API 失败时不应抛出异常', async () => {
      const request = (await import('@/api/request')).default
      vi.mocked(request.put).mockRejectedValueOnce(new Error('Network Error'))
      await expect(setLang('zh')).resolves.toBeUndefined()
    })
  })

  // ---------- initLang ----------
  describe('initLang', () => {
    it('无 token 时应使用本地缓存的语言', async () => {
      localStorage.setItem('demo_lang', 'en')
      langRef.value = 'en'
      const i18n = (await import('@/i18n')).default
      // initLang 会设置 locale
      await (await import('@/utils/lang')).initLang()
      expect(i18n.global.locale.value).toBe('en')
    })

    it('有 token 且后端返回语言时应更新', async () => {
      localStorage.setItem('demo_token', 'test-token')
      const request = (await import('@/api/request')).default
      vi.mocked(request.get).mockResolvedValueOnce({ language: 'en' })

      const { initLang } = await import('@/utils/lang')
      await initLang()

      expect(langRef.value).toBe('en')
      expect(localStorage.getItem('demo_lang')).toBe('en')
    })

    it('后端请求失败时应静默使用本地缓存', async () => {
      localStorage.setItem('demo_token', 'test-token')
      localStorage.setItem('demo_lang', 'zh')
      langRef.value = 'zh'

      const request = (await import('@/api/request')).default
      vi.mocked(request.get).mockRejectedValueOnce(new Error('fail'))

      const { initLang } = await import('@/utils/lang')
      await initLang()

      expect(langRef.value).toBe('zh')
    })
  })
})
