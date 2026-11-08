import { defineConfig } from 'vite'
import { fileURLToPath, URL } from 'node:url'
import qiankun from 'vite-plugin-qiankun'

export default defineConfig({
  plugins: [
    qiankun('order-app', { useDevMode: true }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 7200,
    cors: true,
    origin: 'http://localhost:7200',
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
