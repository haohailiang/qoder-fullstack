/**
 * localStorage polyfill（在 setupFiles 中第一个加载）
 *
 * Node.js 22+ 引入了实验性全局 localStorage，但在 jsdom 环境中可能冲突。
 * 此脚本确保 localStorage 在所有模块导入前可用。
 */
import { vi } from 'vitest'

// 检测 localStorage 是否可用
let localStorageAvailable = false
try {
  localStorage.getItem('test')
  localStorageAvailable = true
} catch {
  localStorageAvailable = false
}

if (!localStorageAvailable) {
  // 注入内存 polyfill
  const store = new Map<string, string>()
  const mockStorage: Storage = {
    getItem(key: string): string | null {
      return store.get(key) ?? null
    },
    setItem(key: string, value: string): void {
      store.set(key, String(value))
    },
    removeItem(key: string): void {
      store.delete(key)
    },
    clear(): void {
      store.clear()
    },
    get length(): number {
      return store.size
    },
    key(index: number): string | null {
      const keys = Array.from(store.keys())
      return keys[index] ?? null
    },
  }
  vi.stubGlobal('localStorage', mockStorage)
}
