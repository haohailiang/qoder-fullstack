import { renderWithQiankun, qiankunWindow } from 'vite-plugin-qiankun/dist/helper'
import { OrderApp } from './app'

let appInstance: OrderApp | null = null

function render(props: any = {}) {
  const { container } = props
  const rootElement = container
    ? container.querySelector('#root')
    : document.getElementById('root')
  
  appInstance = new OrderApp(rootElement as HTMLElement, props)
  appInstance.render()
}

renderWithQiankun({
  bootstrap() {
    console.log('[order-app] bootstrap')
  },
  mount(props) {
    console.log('[order-app] mount', props)
    render(props)
  },
  unmount(props) {
    console.log('[order-app] unmount')
    if (appInstance) {
      appInstance.destroy()
      appInstance = null
    }
  },
  update(props) {
    console.log('[order-app] update', props)
    // 接收基座应用的主题状态
    if (props?.theme) {
      localStorage.setItem('qoder_theme', props.theme)
      // 重新渲染以应用新主题
      if (appInstance) {
        appInstance.render()
      }
    }
    // 接收基座应用的语言状态
    if (props?.lang) {
      localStorage.setItem('qoder_lang', props.lang)
      // 重新渲染以应用新语言
      if (appInstance) {
        appInstance.render()
      }
    }
  },
})

// 独立运行时
if (!qiankunWindow.__POWERED_BY_QIANKUN__) {
  render()
}
