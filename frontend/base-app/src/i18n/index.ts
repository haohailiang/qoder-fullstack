import { createI18n } from 'vue-i18n'
import zh from './zh'
import en from './en'

const LANG_KEY = 'qoder_lang'

/** 读取本地缓存的语言，默认 zh */
function readLang(): 'zh' | 'en' {
  const stored = localStorage.getItem(LANG_KEY)
  if (stored === 'zh' || stored === 'en') {
    return stored
  }
  return 'zh'
}

const i18n = createI18n({
  legacy: false,
  locale: readLang(),
  fallbackLocale: 'zh',
  messages: { zh, en },
})

export default i18n
export { LANG_KEY }
