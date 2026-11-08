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

// 独立运行时
if (!(window as any).__POWERED_BY_QIANKUN__) {
  render()
}

// qiankun 生命周期
export async function bootstrap() {
  console.log('[order-app] bootstrap')
}

export async function mount(props: any) {
  console.log('[order-app] mount', props)
  render(props)
}

export async function unmount(props: any) {
  console.log('[order-app] unmount')
  if (appInstance) {
    appInstance.destroy()
    appInstance = null
  }
}
