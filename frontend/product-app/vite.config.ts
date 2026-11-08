import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'
import qiankun from 'vite-plugin-qiankun'

/**
 * 移除 React Refresh preamble 脚本。
 * qiankun import-html-entry 使用 eval() 执行脚本，不支持 ES module import 语法。
 * preamble 标志 (__vite_plugin_react_preamble_installed__) 和 React Refresh 桩函数
 * 由 base-app 在 beforeLoad 钩子中设置到真实 window 上。
 * 副作用：qiankun 模式下 React 组件热更新（HMR）不可用。
 */
function removeReactRefreshPreamble(): Plugin {
  return {
    name: 'remove-react-refresh-preamble',
    enforce: 'post',
    transformIndexHtml(html) {
      return html.replace(
        /<script type="module">[\s\S]*?\/@react-refresh[\s\S]*?<\/script>/,
        '<!-- react-refresh preamble removed for qiankun compatibility -->'
      )
    },
  }
}

export default defineConfig({
  plugins: [
    react(),
    qiankun('product-app', { useDevMode: true }),
    removeReactRefreshPreamble(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 7100,
    cors: true,
    origin: 'http://localhost:7100',
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
