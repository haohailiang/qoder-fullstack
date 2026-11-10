import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import { renderWithQiankun, qiankunWindow } from 'vite-plugin-qiankun/dist/helper'
import i18n from './i18n'

// 保存 React root 引用，确保 qiankun unmount 时能正确卸载 React 树
// 避免 "removeChild: The node to be removed is not a child of this node" 错误
let reactRoot: ReactDOM.Root | null = null

// 判断是否在 qiankun 环境中运行
function render(props: any = {}) {
  const { container } = props
  const rootElement = container
    ? container.querySelector('#root')
    : document.getElementById('root')
  
  reactRoot = ReactDOM.createRoot(rootElement!)
  reactRoot.render(
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
    // 使用保存的 root 引用正确卸载 React 树
    if (reactRoot) {
      reactRoot.unmount()
      reactRoot = null
    }
  },
  update(props) {
    console.log('[product-app] update', props)
    // 接收基座应用的主题状态
    if (props?.theme) {
      localStorage.setItem('demo_theme', props.theme)
      // 触发 storage 事件让 App 组件重新读取
      window.dispatchEvent(new Event('storage'))
    }
    // 接收基座应用的语言状态
    if (props?.lang) {
      localStorage.setItem('demo_lang', props.lang)
      if (i18n.language !== props.lang) {
        i18n.changeLanguage(props.lang)
      }
    }
  },
})

// 独立运行时
if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  render()
}
