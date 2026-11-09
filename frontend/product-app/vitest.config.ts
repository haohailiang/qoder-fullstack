import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/__tests__/**/*.spec.{ts,tsx}'],
    setupFiles: [
      'src/__tests__/unit/global-init.ts',
      'src/__tests__/unit/setup.ts',
    ],
  },
})
