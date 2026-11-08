import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'

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

// 独立运行时
if (!(window as any).__POWERED_BY_QIANKUN__) {
  render()
}

// qiankun 生命周期导出
export async function bootstrap() {
  console.log('[product-app] bootstrap')
}

export async function mount(props: any) {
  console.log('[product-app] mount', props)
  render(props)
}

export async function unmount(props: any) {
  console.log('[product-app] unmount')
  const { container } = props
  const rootElement = container
    ? container.querySelector('#root')
    : document.getElementById('root')
  if (rootElement) {
    ReactDOM.createRoot(rootElement).unmount()
  }
}
