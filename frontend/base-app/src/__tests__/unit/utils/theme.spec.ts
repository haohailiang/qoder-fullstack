/**
 * theme 工具函数单元测试
 *
 * 覆盖：getTheme / setTheme / toggleTheme / applyTheme / themeRef
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { getTheme, setTheme, toggleTheme, applyTheme, themeRef } from '@/utils/theme'
import type { ThemeMode } from '@/utils/theme'

// mock @/api/request（theme.ts 导入了 request 用于持久化）
vi.mock('@/api/request', () => ({
  default: {
    put: vi.fn().mockResolvedValue({}),
    get: vi.fn().mockResolvedValue(null),
  },
}))

describe('utils/theme', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
    // 重置 themeRef 到默认值
    themeRef.value = 'dark'
  })

  // ---------- getTheme ----------
  describe('getTheme', () => {
    it('无缓存时应返回默认值 dark', () => {
      expect(getTheme()).toBe('dark')
    })

    it('localStorage 有值时应返回对应主题', () => {
      localStorage.setItem('qoder_theme', 'light')
      themeRef.value = 'light'
      expect(getTheme()).toBe('light')
    })
  })

  // ---------- applyTheme ----------
  describe('applyTheme', () => {
    it('dark 模式应在 html 元素上添加 dark 类', () => {
      applyTheme('dark')
      expect(document.documentElement.classList.contains('dark')).toBe(true)
    })

    it('light 模式应移除 html 元素上的 dark 类', () => {
      document.documentElement.classList.add('dark')
      applyTheme('light')
      expect(document.documentElement.classList.contains('dark')).toBe(false)
    })
  })

  // ---------- setTheme ----------
  describe('setTheme', () => {
    it('应更新 themeRef 响应式状态', async () => {
      await setTheme('light')
      expect(themeRef.value).toBe('light')
    })

    it('应写入 localStorage', async () => {
      await setTheme('light')
      expect(localStorage.getItem('qoder_theme')).toBe('light')
    })

    it('应将 dark 类应用到 html 元素', async () => {
      await setTheme('dark')
      expect(document.documentElement.classList.contains('dark')).toBe(true)
      await setTheme('light')
      expect(document.documentElement.classList.contains('dark')).toBe(false)
    })

    it('应异步调用后端 API 持久化主题偏好', async () => {
      const request = (await import('@/api/request')).default
      await setTheme('light')
      expect(request.put).toHaveBeenCalledWith('/user/users/theme', { theme: 'light' })
    })

    it('后端 API 失败时不应抛出异常（静默失败）', async () => {
      const request = (await import('@/api/request')).default
      vi.mocked(request.put).mockRejectedValueOnce(new Error('Network Error'))
      await expect(setTheme('dark')).resolves.toBeUndefined()
    })
  })

  // ---------- toggleTheme ----------
  describe('toggleTheme', () => {
    it('从 dark 切换到 light', async () => {
      themeRef.value = 'dark'
      toggleTheme()
      // setTheme 是 async，需要等待
      await vi.dynamicImportSettled()
      expect(themeRef.value).toBe('light')
    })

    it('从 light 切换到 dark', async () => {
      themeRef.value = 'light'
      toggleTheme()
      await vi.dynamicImportSettled()
      expect(themeRef.value).toBe('dark')
    })
  })
})
