import { defineConfig, devices } from '@playwright/test'

// Real-backend E2E config for ORIN Phase 1D closure.
// ASSUMES backend + MySQL + Redis are already running on localhost:8080.
// Frontend is served via `npm run preview` on port 4173.
// Run with: npx playwright test --config playwright.real-backend.config.js

export default defineConfig({
  testDir: './tests/e2e/real-backend',
  timeout: 60_000,
  expect: {
    timeout: 10_000
  },
  fullyParallel: false,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://127.0.0.1:4173',
    trace: 'on-first-retry'
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ],
  webServer: {
    // Frontend preview only — backend is assumed pre-running
    command: 'npm run preview -- --host 127.0.0.1 --port 4173',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: true,
    timeout: 30_000
  }
})