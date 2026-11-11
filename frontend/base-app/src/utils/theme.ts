import { ref } from 'vue'
import request from '@/api/request'

/** 主题类型：dark（暗黑模式）或 light（亮色模式） */
export type ThemeMode = 'dark' | 'light'

const THEME_KEY = 'qoder_theme'

/** 响应式主题状态，供全局组件实时读取 */
export const themeRef = ref<ThemeMode>(readTheme())

/** 读取本地缓存的主题，默认 dark */
function readTheme(): ThemeMode {
  const stored = localStorage.getItem(THEME_KEY)
  if (stored === 'dark' || stored === 'light') {
    return stored
  }
  return 'dark'
}

/** 获取当前主题 */
export function getTheme(): ThemeMode {
  return themeRef.value
}

/**
 * 切换主题并持久化。
 * - 立即更新 localStorage 和响应式状态
 * - 应用到 <html> 元素
 * - 如果用户已登录，异步调用后端 API 保存到数据库
 */
export async function setTheme(theme: ThemeMode) {
  themeRef.value = theme
  localStorage.setItem(THEME_KEY, theme)
  applyTheme(theme)

  // 异步持久化到数据库（不阻塞 UI）
  try {
    await request.put('/user/users/theme', { theme })
  } catch {
    // 静默失败：本地缓存已更新，下次登录时会同步
    console.warn('主题偏好保存到后端失败')
  }
}

/** 切换主题（在 dark / light 之间互换） */
export function toggleTheme() {
  const next = themeRef.value === 'dark' ? 'light' : 'dark'
  setTheme(next)
}

/**
 * 将主题应用到 <html> 元素。
 * Element Plus 暗黑模式通过在 <html> 上添加 class="dark" 实现。
 */
export function applyTheme(theme: ThemeMode) {
  const html = document.documentElement
  if (theme === 'dark') {
    html.classList.add('dark')
  } else {
    html.classList.remove('dark')
  }
}

/**
 * 初始化主题：从后端加载用户偏好（如果已登录），否则使用本地缓存。
 * 应在应用启动时调用。
 */
export async function initTheme() {
  // 先应用本地缓存的主题
  applyTheme(themeRef.value)

  // 如果已登录，尝试从后端加载最新偏好
  const token = localStorage.getItem('qoder_token')
  if (token) {
    try {
      const result = await request.get<{ theme: ThemeMode }>('/user/users/theme')
      if (result && result.theme && result.theme !== themeRef.value) {
        themeRef.value = result.theme
        localStorage.setItem(THEME_KEY, result.theme)
        applyTheme(result.theme)
      }
    } catch {
      // 静默失败：使用本地缓存的主题
    }
  }
}
