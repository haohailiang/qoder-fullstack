/**
 * 埋点工具函数单元测试
 *
 * 覆盖：trackEvent / trackPageView / trackItemExpose / trackClick
 * - payload 结构验证
 * - localStorage userId 采集
 * - 100ms 批量发送逻辑
 * - 异常静默捕获
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  trackEvent,
  trackPageView,
  trackItemExpose,
  trackClick,
} from '@/utils/tracker'

describe('utils/tracker', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  // ---------- trackEvent ----------
  describe('trackEvent', () => {
    it('应生成包含 eventName、properties、context 的 payload', () => {
      const consoleSpy = vi.spyOn(console, 'debug').mockImplementation(() => {})

      trackEvent('TestEvent', { key: 'value' }, 'TestPage')

      // 推进 100ms 触发批量发送
      vi.advanceTimersByTime(150)

      expect(consoleSpy).toHaveBeenCalledWith(
        '[tracker] batch send',
        expect.arrayContaining([
          expect.objectContaining({
            eventName: 'TestEvent',
            properties: expect.objectContaining({ key: 'value' }),
            context: expect.objectContaining({
              pageName: 'TestPage',
              platform: 'product-app',
            }),
          }),
        ]),
      )

      consoleSpy.mockRestore()
    })

    it('context.timestamp 应为当前时间戳', () => {
      const consoleSpy = vi.spyOn(console, 'debug').mockImplementation(() => {})
      const now = Date.now()

      trackEvent('Timestamp', {}, 'Page')
      vi.advanceTimersByTime(150)

      const call = consoleSpy.mock.calls[0]
      if (call && call[1]) {
        const batch = call[1] as Array<{ context: { timestamp: number } }>
        expect(batch[0].context.timestamp).toBeGreaterThanOrEqual(now)
        expect(batch[0].context.timestamp).toBeLessThanOrEqual(now + 200)
      }

      consoleSpy.mockRestore()
    })

    it('应从 localStorage 读取 qoder_token 作为 userId', () => {
      localStorage.setItem('qoder_token', 'jwt-abc-123')
      const consoleSpy = vi.spyOn(console, 'debug').mockImplementation(() => {})

      trackEvent('UserId', {}, 'Page')
      vi.advanceTimersByTime(150)

      const call = consoleSpy.mock.calls[0]
      if (call && call[1]) {
        const batch = call[1] as Array<{ context: { userId: string } }>
        expect(batch[0].context.userId).toBe('jwt-abc-123')
      }

      consoleSpy.mockRestore()
    })

    it('localStorage 无 token 时 userId 应为 null', () => {
      const consoleSpy = vi.spyOn(console, 'debug').mockImplementation(() => {})

      trackEvent('NoUser', {}, 'Page')
      vi.advanceTimersByTime(150)

      const call = consoleSpy.mock.calls[0]
      if (call && call[1]) {
        const batch = call[1] as Array<{ context: { userId: string | null } }>
        expect(batch[0].context.userId).toBeNull()
      }

      consoleSpy.mockRestore()
    })

    it('100ms 内的多条事件应合并为一次发送', () => {
      const consoleSpy = vi.spyOn(console, 'debug').mockImplementation(() => {})

      trackEvent('Event1', {}, 'Page')
      trackEvent('Event2', {}, 'Page')
      trackEvent('Event3', {}, 'Page')
      vi.advanceTimersByTime(150)

      // 应只调用一次 console.debug
      expect(consoleSpy).toHaveBeenCalledTimes(1)
      const call = consoleSpy.mock.calls[0]
      if (call && call[1]) {
        const batch = call[1] as unknown[]
        expect(batch).toHaveLength(3)
      }

      consoleSpy.mockRestore()
    })

    it('异常应静默捕获不影响业务', () => {
      // 模拟 localStorage 不可用
      const originalGetItem = Storage.prototype.getItem
      Storage.prototype.getItem = () => { throw new Error('SecurityError') }

      expect(() => trackEvent('Error', {}, 'Page')).not.toThrow()

      Storage.prototype.getItem = originalGetItem
    })
  })

  // ---------- 便捷方法 ----------
  describe('便捷方法', () => {
    it('trackPageView 应发送 PageView 事件', () => {
      const consoleSpy = vi.spyOn(console, 'debug').mockImplementation(() => {})

      trackPageView('HomePage', { section: 'top' })
      vi.advanceTimersByTime(150)

      const call = consoleSpy.mock.calls[0]
      if (call && call[1]) {
        const batch = call[1] as Array<{ eventName: string; properties: Record<string, unknown> }>
        expect(batch[0].eventName).toBe('PageView')
        expect(batch[0].properties).toEqual({ section: 'top' })
      }

      consoleSpy.mockRestore()
    })

    it('trackItemExpose 应发送 ItemExpose 事件并包含商品信息', () => {
      const consoleSpy = vi.spyOn(console, 'debug').mockImplementation(() => {})

      trackItemExpose(42, 'iPhone 15', true)
      vi.advanceTimersByTime(150)

      const call = consoleSpy.mock.calls[0]
      if (call && call[1]) {
        const batch = call[1] as Array<{ eventName: string; properties: Record<string, unknown> }>
        expect(batch[0].eventName).toBe('ItemExpose')
        expect(batch[0].properties).toEqual(
          expect.objectContaining({
            productId: 42,
            productName: 'iPhone 15',
            isSeckill: true,
          }),
        )
      }

      consoleSpy.mockRestore()
    })

    it('trackClick 应发送 Click 事件并包含操作类型', () => {
      const consoleSpy = vi.spyOn(console, 'debug').mockImplementation(() => {})

      trackClick(7, 'addToCart', { quantity: 2 })
      vi.advanceTimersByTime(150)

      const call = consoleSpy.mock.calls[0]
      if (call && call[1]) {
        const batch = call[1] as Array<{ eventName: string; properties: Record<string, unknown> }>
        expect(batch[0].eventName).toBe('Click')
        expect(batch[0].properties).toEqual(
          expect.objectContaining({
            productId: 7,
            actionType: 'addToCart',
            quantity: 2,
          }),
        )
      }

      consoleSpy.mockRestore()
    })
  })
})
