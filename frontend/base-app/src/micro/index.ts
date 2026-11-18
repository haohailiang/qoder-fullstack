import { registerMicroApps, start, initGlobalState, MicroAppStateActions } from 'qiankun'
import { getToken, currentUserRef } from '@/utils/auth'
import { getTheme, themeRef } from '@/utils/theme'
import { getLang, langRef } from '@/utils/lang'
import { computed, reactive, watch } from 'vue'

// 全局状态管理器
const initialState = {
  token: getToken(),
  user: currentUserRef.value,
  theme: getTheme(),
  lang: getLang(),
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

// 监听语言变化，同步到子应用
watch(langRef, (newLang) => {
  globalActions.setGlobalState({ lang: newLang })
})

/**
 * 子应用加载状态：记录「已开始加载但尚未挂载」的应用名。
 * 使用 Set 而非布尔值，避免切换子应用时 afterUnmount(旧应用) 晚于 beforeLoad(新应用)
 * 导致加载占位被提前隐藏。
 */
export const subAppState = reactive({
  pending: new Set<string>(),
})

/** 是否有子应用正在加载/挂载（立即态，UI 侧需再叠加防闪烁延时） */
export const subAppLoading = computed(() => subAppState.pending.size > 0)

/**
 * 清理残留的加载标记。
 * 子应用挂载过程中被路由切换打断时，qiankun 不会再回调 afterMount/afterUnmount，
 * 需要由基座在离开子应用路由时兜底清理，避免占位层永久停留。
 */
export function resetSubAppLoading() {
  if (subAppState.pending.size > 0) {
    subAppState.pending.clear()
  }
}

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
      getLang,
    },
  },
  {
    name: 'order-app', // 原生 TypeScript 订单子应用
    entry: '//localhost:7200',
    container: '#subapp-container',
    activeRule: '/order',
    props: {
      getToken,
      globalActions,
      getTheme,
      getLang,
    },
  },
]

// 注册微应用
registerMicroApps(apps, {
  beforeLoad: [async (app) => {
    console.log('[qiankun] beforeLoad:', app.name)
    subAppState.pending.add(app.name)
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
    subAppState.pending.add(app.name)
  }],
  afterMount: [async (app) => {
    console.log('[qiankun] afterMount:', app.name)
    // 挂载完成：占位层可以在数据请求/骨架屏接管后立即撤下
    subAppState.pending.delete(app.name)
  }],
  afterUnmount: [async (app) => {
    console.log('[qiankun] afterUnmount:', app.name)
    subAppState.pending.delete(app.name)
  }],
})

/**
 * 保护 window.history.state 不被子应用覆盖。
 *
 * 子应用（如 product-app）的 BrowserRouter 调用 history.replaceState/pushState 时
 * 传入 React Router 内部 state 格式 {usr, key}，覆盖 vue-router 的 {back, current,
 * forward, position, scroll} 格式。后续 base-app router.push 读取 history.state 时
 * 发现 currentState.current === undefined，拼接出非法 URL。
 *
 * 此 patch 在调用原生方法前将 vue-router 关键字段合并进 state 参数，
 * 保证 history.state 始终保留 base-app 所需的状态结构。
 */
function protectHistoryState() {
  const originalReplaceState = window.history.replaceState.bind(window.history)
  const originalPushState = window.history.pushState.bind(window.history)

  window.history.replaceState = function (state: any, title: string, url?: string | URL | null) {
    // 如果传入的 state 缺少 vue-router 的 current 字段，从已有 state 继承
    if (state && typeof state === 'object' && state.current === undefined) {
      const existing = window.history.state
      if (existing && existing.current !== undefined) {
        state = { ...state, current: existing.current, back: existing.back, forward: existing.forward, position: existing.position }
      }
    }
    originalReplaceState(state, title, url as any)
  }

  window.history.pushState = function (state: any, title: string, url?: string | URL | null) {
    if (state && typeof state === 'object' && state.current === undefined) {
      const existing = window.history.state
      if (existing && existing.current !== undefined) {
        state = { ...state, current: existing.current, back: existing.back, forward: existing.forward, position: existing.position }
      }
    }
    originalPushState(state, title, url as any)
  }
}

// 启动 qiankun
export function startQiankun() {
  protectHistoryState()
  start({
    sandbox: {
      experimentalStyleIsolation: true, // 开启样式隔离
    },
  })
}
