import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  tsconfig: './tsconfig.e2e.json',
  retries: 0,
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
