/**
 * product-app 全局测试 setup
 */
import '@testing-library/jest-dom'
import { beforeEach, vi } from 'vitest'

// ---------- IntersectionObserver mock ----------
class MockIntersectionObserver {
  observe = vi.fn()
  unobserve = vi.fn()
  disconnect = vi.fn()
}
Object.defineProperty(window, 'IntersectionObserver', {
  writable: true,
  value: MockIntersectionObserver,
})

// ---------- localStorage 每个测试前清空 ----------
beforeEach(() => {
  if (typeof localStorage !== 'undefined') {
    localStorage.clear()
  }
})
