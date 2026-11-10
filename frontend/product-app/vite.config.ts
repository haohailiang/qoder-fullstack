import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'
import { resolve, dirname } from 'node:path'
import { createRequire } from 'node:module'
import qiankun from 'vite-plugin-qiankun'

const require = createRequire(import.meta.url)

/**
 * 自定义 Cesium 插件（替代 vite-plugin-cesium，兼容 qiankun 微前端模式）。
 *
 * qiankun 模式下 product-app 作为子应用被 base-app（port 5173）加载，
 * 浏览器页面 origin 为 base-app，而 Cesium Workers/Assets 由 product-app（port 7100）提供。
 * 因此需要：
 *   1. 在 product-app dev server 上以 /cesium/ 路径提供静态资源（含 CORS 头）
 *   2. 将 CESIUM_BASE_URL 全局变量设为完整 URL http://localhost:7100/cesium/
 *      使 Cesium 跨 origin 加载 Workers，而非从当前页面 origin 请求。
 */
function cesiumQiankunPlugin(): Plugin {
  const cesiumBaseUrl = 'http://localhost:7100/cesium/'
  return {
    name: 'cesium-qiankun',
    config() {
      return {
        define: {
          CESIUM_BASE_URL: JSON.stringify(cesiumBaseUrl),
        },
      }
    },
    configureServer(server) {
      const cesiumDir = resolve(dirname(fileURLToPath(import.meta.url)), 'node_modules/cesium/Build/CesiumUnminified')
      const serveStatic = require('serve-static')
      server.middlewares.use('/cesium', serveStatic(cesiumDir, {
        setHeaders: (res: any) => {
          res.setHeader('Access-Control-Allow-Origin', '*')
        },
      }))
    },
  }
}

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
    cesiumQiankunPlugin(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  optimizeDeps: {
    // Fix: @cesium/engine imports @zip.js/zip.js/lib/zip-no-worker.js
    // but the path is not declared in @zip.js/zip.js package exports
    esbuildOptions: {
      plugins: [
        {
          name: 'cesium-zip-fix',
          setup(build) {
            const __dirname = fileURLToPath(new URL('.', import.meta.url))
            build.onResolve(
              { filter: /@zip\.js\/zip\.js\/lib\/zip-no-worker\.js$/ },
              () => ({
                path: resolve(__dirname, 'node_modules/@zip.js/zip.js/dist/zip.js'),
              }),
            )
          },
        },
      ],
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
