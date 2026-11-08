import { ref } from 'vue'
import request from '@/api/request'
import i18n, { LANG_KEY } from '@/i18n'

/** 语言类型 */
export type Lang = 'zh' | 'en'

/** 响应式语言状态，供全局组件实时读取 */
export const langRef = ref<Lang>(readLang())

/** 读取本地缓存的语言 */
function readLang(): Lang {
  const stored = localStorage.getItem(LANG_KEY)
  if (stored === 'zh' || stored === 'en') {
    return stored
  }
  return 'zh'
}

/** 获取当前语言 */
export function getLang(): Lang {
  return langRef.value
}

/**
 * 切换语言并持久化。
 * - 立即更新 localStorage、vue-i18n locale 和响应式状态
 * - 如果用户已登录，异步调用后端 API 保存到数据库
 */
export async function setLang(lang: Lang) {
  langRef.value = lang
  localStorage.setItem(LANG_KEY, lang)
  // 更新 vue-i18n locale
  i18n.global.locale.value = lang

  // 异步持久化到数据库（不阻塞 UI）
  try {
    await request.put('/user/users/language', { language: lang })
  } catch {
    // 静默失败：本地缓存已更新，下次登录时会同步
    console.warn('语言偏好保存到后端失败')
  }
}

/**
 * 初始化语言：从后端加载用户偏好（如果已登录），否则使用本地缓存。
 * 应在应用启动时调用。
 */
export async function initLang() {
  // 先应用本地缓存的语言
  i18n.global.locale.value = langRef.value

  // 如果已登录，尝试从后端加载最新偏好
  const token = localStorage.getItem('demo_token')
  if (token) {
    try {
      const result = await request.get<{ language: Lang }>('/user/users/language')
      if (result && result.language && (result.language === 'zh' || result.language === 'en') && result.language !== langRef.value) {
        langRef.value = result.language
        localStorage.setItem(LANG_KEY, result.language)
        i18n.global.locale.value = result.language
      }
    } catch {
      // 静默失败：使用本地缓存的语言
    }
  }
}
