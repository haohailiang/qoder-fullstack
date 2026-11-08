import { registerMicroApps, start, initGlobalState, MicroAppStateActions } from 'qiankun'
import { getToken, currentUserRef } from '@/utils/auth'

// 全局状态管理器
const initialState = {
  token: getToken(),
  user: currentUserRef.value,
}

export const globalActions: MicroAppStateActions = initGlobalState(initialState)

// 监听全局状态变化
globalActions.onGlobalStateChange((state, prev) => {
  console.log('[qiankun] 全局状态变化:', state, prev)
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
    },
  },
]

// 注册微应用
registerMicroApps(apps, {
  beforeLoad: [async (app) => {
    console.log('[qiankun] beforeLoad:', app.name)
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
