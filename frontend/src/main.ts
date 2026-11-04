import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'

// 基础样式
import './styles/index.css'

const app = createApp(App)

// Element Plus 完整引入（开发演示用，简单可靠）
// 生产环境可按需引入：使用 unplugin-auto-import 与 unplugin-vue-components 插件
app.use(ElementPlus, { locale: zhCn })

// 全局注册 Element Plus 图标组件
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(router)
app.mount('#app')
