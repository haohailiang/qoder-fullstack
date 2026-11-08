import { registerMicroApps, start, initGlobalState, MicroAppStateActions } from 'qiankun'
import { getToken, currentUserRef } from '@/utils/auth'
import { getTheme, themeRef } from '@/utils/theme'
import { watch } from 'vue'

// 全局状态管理器
const initialState = {
  token: getToken(),
  user: currentUserRef.value,
  theme: getTheme(),
}

export const globalActions: MicroAppStateActions = initGlobalState(initialState)

// 监听全局状态变化
globalActions.onGlobalStateChange((state, prev) => {
  console.log('[qiankun] 全局状态变化:', state, prev)
})

// 监听主题变化，同步到子应用
watch(themeRef, (newTheme) => {
  globalActions.setGlobalState({ theme: newTheme })
})

// 子应用配置
const apps = [
  {
    name: 'product-app', // React 商品子应用
    entry: '//localhost:7100',
    container: '#subapp-container',
    activeRule: '/product',
    props: {
      getToken,
      globalActions,
      getTheme,
    },
  },
  {
    name: 'order-app', // Angular 订单子应用
    entry: '//localhost:7200',
    container: '#subapp-container',
    activeRule: '/order',
    props: {
      getToken,
      globalActions,
      getTheme,
    },
  },
]

// 注册微应用
registerMicroApps(apps, {
  beforeLoad: [async (app) => {
    console.log('[qiankun] beforeLoad:', app.name)
    // 在真实 window 上设置 React Refresh 兼容标志和桩函数
    // qiankun 沙箱中 eval() 设置的 window 是代理对象，而子应用组件
    // 通过 import() 加载后运行在模块作用域，访问的是真实 window
    if (app.name === 'product-app') {
      (window as any).__vite_plugin_react_preamble_installed__ = true
      ;(window as any).$RefreshReg$ = () => {}
      ;(window as any).$RefreshSig$ = () => (type: any) => type
    }
  }],
  beforeMount: [async (app) => {
    console.log('[qiankun] beforeMount:', app.name)
  }],
  afterUnmount: [async (app) => {
    console.log('[qiankun] afterUnmount:', app.name)
  }],
})

// 启动 qiankun
export function startQiankun() {
  start({
    sandbox: {
      experimentalStyleIsolation: true, // 开启样式隔离
    },
  })
}
