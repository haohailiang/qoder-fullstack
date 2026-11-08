import { useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider, theme } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import ProductList from './pages/ProductList'
import ProductDetail from './pages/ProductDetail'

type ThemeMode = 'dark' | 'light'

function App() {
  const [themeMode, setThemeMode] = useState<ThemeMode>(
    () => (localStorage.getItem('demo_theme') as ThemeMode) || 'dark'
  )

  useEffect(() => {
    // 监听 qiankun 全局状态变化
    const handleStorage = () => {
      const stored = (localStorage.getItem('demo_theme') as ThemeMode) || 'dark'
      setThemeMode(stored)
    }
    window.addEventListener('storage', handleStorage)
    return () => window.removeEventListener('storage', handleStorage)
  }, [])

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

  return (
    <ConfigProvider
      locale={zhCN}
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
