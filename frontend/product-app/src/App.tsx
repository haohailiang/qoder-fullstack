import { useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider, theme } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import enUS from 'antd/locale/en_US'
import { useTranslation } from 'react-i18next'
import './i18n'
import ProductList from './pages/ProductList'
import ProductDetail from './pages/ProductDetail'

type ThemeMode = 'dark' | 'light'

function App() {
  const { i18n } = useTranslation()
  const [themeMode, setThemeMode] = useState<ThemeMode>(
    () => (localStorage.getItem('demo_theme') as ThemeMode) || 'dark'
  )
  const [currentLang, setCurrentLang] = useState<string>(i18n.language || 'zh')

  useEffect(() => {
    // 监听 qiankun 全局状态变化（主题 + 语言）
    const handleStorage = () => {
      const stored = (localStorage.getItem('demo_theme') as ThemeMode) || 'dark'
      setThemeMode(stored)
      setCurrentLang(i18n.language || 'zh')
    }
    window.addEventListener('storage', handleStorage)
    return () => window.removeEventListener('storage', handleStorage)
  }, [i18n.language])

  // 监听来自 qiankun 的主题变化
  useEffect(() => {
    const observer = new MutationObserver(() => {
      const stored = (localStorage.getItem('demo_theme') as ThemeMode) || 'dark'
      if (stored !== themeMode) {
        setThemeMode(stored)
      }
    })
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
    return () => observer.disconnect()
  }, [themeMode])

  // 监听 i18n 语言变化
  useEffect(() => {
    const handler = () => {
      setCurrentLang(i18n.language || 'zh')
    }
    i18n.on('languageChanged', handler)
    return () => { i18n.off('languageChanged', handler) }
  }, [i18n])

  // Ant Design locale 根据当前语言动态切换
  const antdLocale = currentLang === 'en' ? enUS : zhCN

  return (
    <ConfigProvider
      locale={antdLocale}
      theme={{
        algorithm: themeMode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm,
      }}
    >
      <BrowserRouter basename="/product">
        <Routes>
          <Route path="/" element={<Navigate to="/list" replace />} />
          <Route path="/list" element={<ProductList />} />
          <Route path="/detail/:id" element={<ProductDetail />} />
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  )
}

export default App
