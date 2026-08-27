import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:15173',
    trace: 'on-first-retry',
    timezoneId: 'Asia/Shanghai',
    locale: 'zh-CN',
  },
  webServer: [
    {
      command: 'powershell -NoProfile -ExecutionPolicy Bypass -File ./e2e/start-e2e-backend.ps1',
      url: 'http://127.0.0.1:18080/api/jobs',
      timeout: 120_000,
      reuseExistingServer: false,
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 15173',
      url: 'http://127.0.0.1:15173',
      timeout: 120_000,
      reuseExistingServer: false,
      env: {
        JOBHUB_API_TARGET: 'http://127.0.0.1:18080',
      },
    },
  ],
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
