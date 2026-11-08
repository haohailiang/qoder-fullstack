import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import { renderWithQiankun, qiankunWindow } from 'vite-plugin-qiankun/dist/helper'

// 判断是否在 qiankun 环境中运行
function render(props: any = {}) {
  const { container } = props
  const rootElement = container
    ? container.querySelector('#root')
    : document.getElementById('root')
  
  ReactDOM.createRoot(rootElement!).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  )
}

renderWithQiankun({
  bootstrap() {
    console.log('[product-app] bootstrap')
  },
  mount(props) {
    console.log('[product-app] mount', props)
    render(props)
  },
  unmount(props) {
    console.log('[product-app] unmount')
    const { container } = props
    const rootElement = container
      ? container.querySelector('#root')
      : document.getElementById('root')
    if (rootElement) {
      ReactDOM.createRoot(rootElement).unmount()
    }
  },
  update(props) {
    console.log('[product-app] update')
  },
})

// 独立运行时
if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  render()
}
