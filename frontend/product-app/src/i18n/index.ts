import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import zh from './zh'
import en from './en'

const LANG_KEY = 'qoder_lang'

function readLang(): string {
  const stored = localStorage.getItem(LANG_KEY)
  if (stored === 'zh' || stored === 'en') {
    return stored
  }
  return 'zh'
}

i18n.use(initReactI18next).init({
  resources: {
    zh: { translation: zh },
    en: { translation: en },
  },
  lng: readLang(),
  fallbackLng: 'zh',
  interpolation: {
    escapeValue: false,
  },
})

export default i18n
export { LANG_KEY }
