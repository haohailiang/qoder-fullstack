/**
 * base-app 全局测试 setup
 *
 * - mock Element Plus ElMessage（避免真实 DOM 渲染）
 * - mock localStorage
 * - mock window.matchMedia（Element Plus 依赖）
 */
import { beforeEach, vi } from 'vitest'

// ---------- Element Plus ElMessage mock ----------
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
    ElMessageBox: {
      confirm: vi.fn().mockResolvedValue('confirm'),
      alert: vi.fn().mockResolvedValue(undefined),
    },
  }
})

// ---------- window.matchMedia mock ----------
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

// ---------- IntersectionObserver mock（product-app 也会用到） ----------
class MockIntersectionObserver {
  observe = vi.fn()
  unobserve = vi.fn()
  disconnect = vi.fn()
}
Object.defineProperty(window, 'IntersectionObserver', {
  writable: true,
  value: MockIntersectionObserver,
})

// ---------- MutationObserver mock ----------
class MockMutationObserver {
  observe = vi.fn()
  disconnect = vi.fn()
  takeRecords = vi.fn().mockReturnValue([])
}
Object.defineProperty(window, 'MutationObserver', {
  writable: true,
  value: MockMutationObserver,
})

// ---------- localStorage 每个测试前清空 ----------
beforeEach(() => {
  if (typeof localStorage !== 'undefined') {
    localStorage.clear()
  }
})
