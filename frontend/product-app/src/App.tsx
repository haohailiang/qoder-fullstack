import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import ProductList from './pages/ProductList'
import ProductDetail from './pages/ProductDetail'

function App() {
  return (
    <ConfigProvider locale={zhCN}>
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
