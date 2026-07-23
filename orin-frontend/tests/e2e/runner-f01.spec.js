import { expect, test } from '@playwright/test'

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
})

async function authenticate(page) {
  await page.addInitScript(() => {
    const roles = ['ROLE_ADMIN', 'ROLE_USER']
    window.localStorage.setItem('orin_token', 'runner-f01-token')
    window.sessionStorage.setItem('orin_setup_completed', 'true')
    document.cookie = 'orin_token=runner-f01-token; path=/'
    document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(roles))}; path=/`
    document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify({ userId: 1, username: 'admin' }))}; path=/`
  })
}

test('F01 creates an enrollment command and controls a monitored Runner', async ({ page }) => {
  let status = 'ONLINE'
  let drainRequested = false
  const runner = () => ({
    id: 'run_f01',
    name: 'prod-web-1',
    status,
    drainRequested,
    hostname: 'prod-web-1.internal',
    os: 'Linux',
    arch: 'x86_64',
    version: '0.1.0',
    cpuCores: 8,
    memoryTotal: 17179869184,
    diskTotal: 512000000000,
    activeRuns: 0,
    queuedRuns: 0,
    maxConcurrency: 2,
    lastHeartbeatAgeSec: 3,
    lastDependencyHealth: 'HEALTHY',
    latestSnapshot: {
      cpuUsage: 21.5,
      memoryUsed: 4294967296,
      memoryTotal: 17179869184,
      diskUsed: 128000000000,
      diskTotal: 512000000000,
      reportedAt: Date.now()
    },
    recentSnapshots: [],
    credential: { credentialId: 'rcred_f01', keyPrefix: 'sk-runner-rcred', last4: 'abcd' }
  })

  await authenticate(page)
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path === '/api/v1/setup/status') {
      return route.fulfill(json({ completed: true, canInitialize: false }))
    }
    if (path === '/api/v1/runners' && request.method() === 'GET') {
      return route.fulfill(json({ content: [runner()], totalElements: 1 }))
    }
    if (path === '/api/v1/runner-enrollment-tokens' && request.method() === 'POST') {
      return route.fulfill(json({
        id: 'etk_f01',
        name: 'edge-runner-1',
        token: 'sk-enroll-etk_f01.one-time-secret',
        enrollmentEndpoint: 'https://control.orin.example/api/system/runners/enroll',
        expiresAt: Date.now() + 900000,
        ttlSec: 900
      }, 201))
    }
    if (path === '/api/v1/runners/run_f01' && request.method() === 'GET') {
      return route.fulfill(json(runner()))
    }
    if (path === '/api/v1/runners/run_f01/drain') {
      status = 'DRAINING'
      drainRequested = true
      return route.fulfill(json(runner()))
    }
    if (path === '/api/v1/runners/run_f01/restore') {
      status = 'ONLINE'
      drainRequested = false
      return route.fulfill(json(runner()))
    }
    if (path === '/api/v1/runners/run_f01/revoke') {
      status = 'REVOKED'
      drainRequested = false
      return route.fulfill(json(runner()))
    }
    return route.fulfill(json({}))
  })

  await page.goto('/dashboard/runtime/server')
  await expect(page.getByText('prod-web-1', { exact: true })).toBeVisible()
  await expect(page.getByText('ONLINE', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: '接入服务器' }).click()
  await page.getByPlaceholder('如 prod-web-1').fill('edge-runner-1')
  await page.getByRole('button', { name: '生成接入命令' }).click()
  const command = page.locator('.command-text')
  await expect(command).toContainText('ORIN_ENROLLMENT_TOKEN')
  await expect(command).toContainText('https://control.orin.example')
  await expect(command).toContainText('orin-runner enroll')
  await page.getByRole('button', { name: '关闭' }).click()

  await page.getByRole('button', { name: 'Drain' }).click()
  await page.getByRole('dialog', { name: '确认 Drain' })
    .getByRole('button', { name: 'OK' }).click()
  await expect(page.getByText('DRAINING', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '恢复' }).click()
  await expect(page.getByText('ONLINE', { exact: true })).toBeVisible()

  await page.getByText('prod-web-1', { exact: true }).click()
  await expect(page).toHaveURL(/\/dashboard\/runtime\/server\/run_f01/)
  await expect(page.getByText('HEALTHY', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Revoke' }).click()
  await page.getByRole('button', { name: '确认撤销' }).click()
  await expect(page.getByText('REVOKED', { exact: true })).toBeVisible()
})
