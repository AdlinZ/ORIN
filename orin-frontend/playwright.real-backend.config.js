import { defineConfig, devices } from '@playwright/test'

// Real-backend E2E config for ORIN Phase 1D closure.
// ASSUMES backend + MySQL + Redis are already running on localhost:8080.
// Frontend is served by Vite on the standard development port 5173 so its development proxy forwards
// browser API calls to the already-running backend on :8080.
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
    baseURL: 'http://127.0.0.1:5173',
    trace: 'on-first-retry'
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ],
  webServer: {
    // Backend is assumed pre-running.  `vite preview` has no API proxy, so
    // real-browser tests must use the Vite server rather than static preview.
    command: 'npm run dev -- --host 127.0.0.1 --port 5173',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: true,
    timeout: 30_000
  }
})
