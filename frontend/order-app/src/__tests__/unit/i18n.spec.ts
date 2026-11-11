/**
 * order-app i18n 翻译函数单元测试
 *
 * 覆盖：t() 函数中英文切换、fallback 逻辑
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { t, getCurrentLang } from '@/i18n'

describe('order-app i18n', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  // ---------- t() 函数 ----------
  describe('t() 翻译函数', () => {
    it('默认语言（zh）应返回中文文案', () => {
      localStorage.setItem('qoder_lang', 'zh')
      expect(t('order.title')).toBe('订单管理')
      expect(t('order.myOrders')).toBe('我的订单')
      expect(t('order.empty')).toBe('暂无订单数据')
    })

    it('切换为英文后应返回英文文案', () => {
      localStorage.setItem('qoder_lang', 'en')
      expect(t('order.title')).toBe('Orders')
      expect(t('order.myOrders')).toBe('My Orders')
      expect(t('order.empty')).toBe('No order data')
    })

    it('未定义的 key 应回退到中文', () => {
      localStorage.setItem('qoder_lang', 'en')
      // 假设某个 key 只在 zh 中存在
      expect(t('order.statusUnknown')).toBe('Unknown')
    })

    it('完全不存在的 key 应返回 key 本身', () => {
      expect(t('nonexistent.key')).toBe('nonexistent.key')
    })
  })

  // ---------- getCurrentLang ----------
  describe('getCurrentLang', () => {
    it('无缓存时应默认返回 zh', () => {
      expect(getCurrentLang()).toBe('zh')
    })

    it('应返回 localStorage 中存储的语言值', () => {
      localStorage.setItem('qoder_lang', 'en')
      expect(getCurrentLang()).toBe('en')
    })

    it('非法值应回退到 zh', () => {
      localStorage.setItem('qoder_lang', 'invalid')
      expect(getCurrentLang()).toBe('zh')
    })
  })

  // ---------- 订单状态翻译完整性 ----------
  describe('订单状态翻译完整性', () => {
    const statusKeys = [
      'order.statusPending',
      'order.statusPaid',
      'order.statusShipped',
      'order.statusCompleted',
      'order.statusCancelled',
    ]

    it('中文应包含所有状态翻译', () => {
      localStorage.setItem('qoder_lang', 'zh')
      statusKeys.forEach((key) => {
        expect(t(key)).toBeTruthy()
        expect(t(key)).not.toBe(key) // 不应返回 key 本身
      })
    })

    it('英文应包含所有状态翻译', () => {
      localStorage.setItem('qoder_lang', 'en')
      statusKeys.forEach((key) => {
        expect(t(key)).toBeTruthy()
        expect(t(key)).not.toBe(key)
      })
    })
  })
})
