import { fileURLToPath, URL } from 'node:url'
import http from 'node:http'

import vue from '@vitejs/plugin-vue'
import { defineConfig, type Plugin } from 'vite'

/**
 * 自定义代理插件：解决 Vite 内置代理与 Spring Cloud Gateway (Netty) 的兼容性问题。
 * Gateway 返回 `Connection: close` 后继续发送数据，Node.js 严格 HTTP 解析器会抛出
 * "Parse Error: Data after Connection: close"，导致所有 /api 请求 500。
 * 此插件使用 Node http 模块手动转发，绕过内置代理的严格解析。
 */
function apiProxyPlugin(): Plugin {
  const TARGET = 'http://localhost:8080'
  return {
    name: 'api-proxy',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (!req.url?.startsWith('/api')) return next()

        const proxyReq = http.request(
          TARGET + req.url,
          {
            method: req.method,
            headers: { ...req.headers, host: 'localhost:8080' },
          },
          (proxyRes) => {
            // 移除 Connection: close 避免 Node.js HTTP 解析器报错
            delete proxyRes.headers['connection']
            res.writeHead(proxyRes.statusCode || 500, proxyRes.headers)
            proxyRes.pipe(res)
          },
        )

        proxyReq.on('error', () => {
          if (!res.headersSent) res.writeHead(502)
          res.end('Bad Gateway')
        })

        req.pipe(proxyReq)
      })
    },
  }
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue(), apiProxyPlugin()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
  },
})
