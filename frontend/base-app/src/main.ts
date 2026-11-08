import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'

import App from './App.vue'
import router from './router'

// 基础样式
import './styles/index.css'

// 国际化
import i18n from './i18n'
import { initLang } from './utils/lang'

// 主题管理
import { initTheme } from './utils/theme'

// 微前端
import { startQiankun } from './micro'

const app = createApp(App)

// Element Plus 完整引入（开发演示用，简单可靠）
// 语言配置通过 App.vue 中的 el-config-provider 动态切换
app.use(ElementPlus)

// 国际化
app.use(i18n)

// 全局注册 Element Plus 图标组件
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(router)
app.mount('#app')

// 初始化主题（从后端加载用户偏好或应用本地缓存）
initTheme()

// 初始化语言（从后端加载用户偏好或应用本地缓存）
initLang()

// 启动 qiankun 微前端
startQiankun()
