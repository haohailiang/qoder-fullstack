import { defineConfig, devices } from '@playwright/test'

/**
 * Playwright E2E 测试配置
 *
 * - 自动启动三个子应用的 Vite dev server
 * - 默认使用 Chromium 浏览器
 * - 失败时保留截图和视频
 */
export default defineConfig({
  testDir: '.',
  testMatch: '**/*.spec.ts',
  timeout: 60_000,
  retries: 1,
  fullyParallel: false, // E2E 测试按顺序执行，避免状态冲突
  forbidOnly: !!process.env.CI,
  workers: 1,
  reporter: [
    ['html', { open: 'never', outputFolder: 'test-results/report' }],
    ['list'],
  ],
  use: {
    baseURL: 'http://localhost:5173',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    trace: 'retain-on-failure',
    actionTimeout: 10_000,
    navigationTimeout: 15_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: [
    {
      command: 'node node_modules/vite/bin/vite.js --port 5173',
      cwd: '../base-app',
      port: 5173,
      reuseExistingServer: !process.env.CI,
      timeout: 30_000,
    },
    {
      command: 'node node_modules/vite/bin/vite.js --port 7100',
      cwd: '../product-app',
      port: 7100,
      reuseExistingServer: !process.env.CI,
      timeout: 30_000,
    },
    {
      command: 'node node_modules/vite/bin/vite.js --port 7200',
      cwd: '../order-app',
      port: 7200,
      reuseExistingServer: !process.env.CI,
      timeout: 30_000,
    },
  ],
})
