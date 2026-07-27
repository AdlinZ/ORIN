import { execFileSync } from 'node:child_process'

import { expect, test } from '@playwright/test'

const HAS_REAL_BACKEND = Boolean(process.env.ORIN_REAL_BACKEND)
const ADMIN_USERNAME = process.env.ORIN_E2E_ADMIN_USERNAME || 'admin'
const ADMIN_PASSWORD = process.env.ORIN_E2E_ADMIN_PASSWORD || ''
const RUNNER_IMAGE = process.env.ORIN_E2E_RUNNER_IMAGE || 'orin-runner:f01'
const RUNNER_BASE_URL = process.env.ORIN_E2E_RUNNER_BASE_URL || 'http://host.lima.internal:5173'
const ONLINE_TIMEOUT_MS = Number(process.env.ORIN_E2E_RUNNER_ONLINE_TIMEOUT_MS || 120_000)
const OFFLINE_TIMEOUT_MS = Number(process.env.ORIN_E2E_RUNNER_OFFLINE_TIMEOUT_MS || 150_000)

function docker(...args) {
  return execFileSync('docker', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()
}

async function waitForRunnerStatus(page, runnerName, expectedStatus, timeoutMs) {
  await expect.poll(async () => {
    await page.goto('/workspace/runners')
    const row = page.getByRole('row').filter({ hasText: runnerName }).first()
    // `page.goto()` only waits for the SPA shell.  The Runner table is loaded
    // afterwards, so a zero-count check here races the list request and turns
    // a real ONLINE Runner into a false MISSING result.
    const rowVisible = await row.waitFor({ state: 'visible', timeout: 5_000 })
      .then(() => true)
      .catch(() => false)
    if (!rowVisible) return 'MISSING'
    return (await row.innerText()).includes(expectedStatus) ? expectedStatus : 'OTHER'
  }, { timeout: timeoutMs, intervals: [1_000, 2_000, 3_000] }).toBe(expectedStatus)
}

async function completeFirstRunSetupIfNeeded(page) {
  await page.goto('/login')
  const setupTitle = page.getByText('ORIN First-run Setup', { exact: true })
  const setupVisible = await setupTitle.waitFor({ state: 'visible', timeout: 5_000 })
    .then(() => true)
    .catch(() => false)
  if (!setupVisible) return

  await page.getByRole('button', { name: '继续' }).click()
  const setupInputs = page.locator('.setup-form-panel input')
  await setupInputs.nth(0).fill(ADMIN_USERNAME)
  await setupInputs.nth(1).fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: '继续' }).click()

  // Provider credentials and a client API key are independent of F01.  Keep
  // this E2E fixture secret-free while still completing the product setup.
  await page.locator('.el-switch').click()
  await page.getByRole('button', { name: '继续' }).click()
  await page.locator('.el-switch').click()
  const [initializeResponse] = await Promise.all([
    page.waitForResponse((response) =>
      response.url().includes('/api/v1/setup/initialize') && response.request().method() === 'POST'
    ),
    page.getByRole('button', { name: '完成初始化' }).click(),
  ])
  if (!initializeResponse.ok()) {
    const payload = await initializeResponse.json().catch(() => ({}))
    throw new Error(`First-run setup failed: HTTP ${initializeResponse.status()} code=${payload.code || 'unknown'}`)
  }
  await expect(page.getByText('系统初始化完成')).toBeVisible()
  await page.getByRole('button', { name: '去登录' }).click()
  await page.waitForURL(/\/login$/)
}

test.describe('F01 real browser + Docker Runner E2E', () => {
  test.setTimeout(6 * 60_000)

  test.beforeAll(() => {
    if (!HAS_REAL_BACKEND) {
      throw new Error('F01 real-browser E2E requires ORIN_REAL_BACKEND; it must never silently skip.')
    }
    if (!ADMIN_PASSWORD) {
      throw new Error('F01 real-browser E2E requires ORIN_E2E_ADMIN_PASSWORD.')
    }
  })

  test('Workspace enrollment drives ONLINE → Drain → Restore → OFFLINE → Resume → Revoke', async ({ page }) => {
    const stamp = `${Date.now()}-${process.pid}`
    const runnerName = `f01-browser-${stamp}`
    const containerName = `orin-f01-browser-${stamp}`
    const volumeName = `orin-f01-browser-credential-${stamp}`
    const pageErrors = []
    const consoleErrors = []
    const runnerRequests = []
    page.on('pageerror', (error) => pageErrors.push(error.message))
    page.on('console', (message) => {
      if (message.type() === 'error') consoleErrors.push(message.text())
    })
    page.on('request', (request) => {
      if (request.url().includes('/api/v1/runners/')) {
        runnerRequests.push(`${request.method()} ${request.url()}`)
      }
    })

    try {
      await completeFirstRunSetupIfNeeded(page)
      await page.getByPlaceholder('请输入用户名').fill(ADMIN_USERNAME)
      await page.getByPlaceholder('请输入密码').fill(ADMIN_PASSWORD)
      const [loginResponse] = await Promise.all([
        page.waitForResponse((response) =>
          response.url().includes('/api/v1/auth/login') && response.request().method() === 'POST'
        ),
        page.getByRole('button', { name: '登录' }).click(),
      ])
      if (!loginResponse.ok()) {
        throw new Error(`Login failed: HTTP ${loginResponse.status()}`)
      }
      await page.waitForTimeout(1_000)
      if (new URL(page.url()).pathname === '/login') {
        throw new Error(`Login did not navigate away from /login: ${pageErrors.join(' | ') || 'no page error'}`)
      }

      await page.goto('/workspace/runners')
      await page.getByRole('button', { name: '接入服务器' }).click()
      await page.getByPlaceholder('如 prod-web-1').fill(runnerName)
      await page.getByRole('button', { name: '生成接入命令' }).click()
      const command = await page.locator('.command-text').innerText()
      expect(command).toContain('orin-runner enroll')
      expect(command).toContain(RUNNER_BASE_URL)

      const tokenMatch = command.match(/ORIN_ENROLLMENT_TOKEN='([^']+)'/)
      if (!tokenMatch) {
        throw new Error('Enrollment command did not contain a shell-quoted one-time token.')
      }
      const enrollmentToken = tokenMatch[1]

      docker('volume', 'create', volumeName)
      docker(
        'run', '--detach', '--name', containerName,
        '--volume', `${volumeName}:/root/.orin`,
        '--env', `ORIN_ENROLLMENT_TOKEN=${enrollmentToken}`,
        RUNNER_IMAGE,
        'enroll', '--name', runnerName, '--url', RUNNER_BASE_URL,
      )

      await page.getByRole('button', { name: '完成，刷新列表' }).click()
      await waitForRunnerStatus(page, runnerName, 'ONLINE', ONLINE_TIMEOUT_MS)

      const runnerRow = page.getByRole('row').filter({ hasText: runnerName }).first()
      await runnerRow.click()
      await page.waitForURL(/\/workspace\/runners\/run_/)
      const runnerDetail = page.locator('.runner-detail-page')
      await expect(runnerDetail.getByText('ONLINE', { exact: true })).toBeVisible()
      await runnerDetail.getByRole('button', { name: 'Drain' }).click()
      let drainResponse
      try {
        const drainDialog = page.getByRole('dialog', { name: '确认 Drain' })
        await expect(drainDialog).toBeVisible()
        ;[drainResponse] = await Promise.all([
          page.waitForResponse((response) =>
            response.url().includes('/drain') && response.request().method() === 'POST',
          { timeout: 15_000 }),
          drainDialog.getByRole('button', { name: '确认 Drain' }).click(),
        ])
      } catch {
        throw new Error(
          `Drain did not reach the control plane. Observed: ${runnerRequests.join(' | ') || 'none'}. `
          + `Browser errors: ${consoleErrors.join(' | ') || 'none'}`
        )
      }
      if (!drainResponse.ok()) {
        const payload = await drainResponse.json().catch(() => ({}))
        throw new Error(`Drain failed: HTTP ${drainResponse.status()} code=${payload.code || 'unknown'}`)
      }
      await expect(runnerDetail.getByText('DRAINING', { exact: true })).toBeVisible()

      const [restoreResponse] = await Promise.all([
        page.waitForResponse((response) =>
          response.url().includes('/restore') && response.request().method() === 'POST'
        ),
        runnerDetail.getByRole('button', { name: '恢复' }).click(),
      ])
      if (!restoreResponse.ok()) {
        const payload = await restoreResponse.json().catch(() => ({}))
        throw new Error(`Restore failed: HTTP ${restoreResponse.status()} code=${payload.code || 'unknown'}`)
      }
      await expect(runnerDetail.getByText('ONLINE', { exact: true })).toBeVisible()

      docker('rm', '-f', containerName)
      await waitForRunnerStatus(page, runnerName, 'OFFLINE', OFFLINE_TIMEOUT_MS)

      docker('run', '--detach', '--name', containerName, '--volume', `${volumeName}:/root/.orin`, RUNNER_IMAGE)
      await waitForRunnerStatus(page, runnerName, 'ONLINE', ONLINE_TIMEOUT_MS)

      const resumedRow = page.getByRole('row').filter({ hasText: runnerName }).first()
      await resumedRow.click()
      await page.waitForURL(/\/workspace\/runners\/run_/)
      const resumedDetail = page.locator('.runner-detail-page')
      await resumedDetail.getByRole('button', { name: 'Revoke' }).click()
      const [revokeResponse] = await Promise.all([
        page.waitForResponse((response) =>
          response.url().includes('/revoke') && response.request().method() === 'POST'
        ),
        page.getByRole('button', { name: '确认撤销' }).click(),
      ])
      if (!revokeResponse.ok()) {
        const payload = await revokeResponse.json().catch(() => ({}))
        throw new Error(`Revoke failed: HTTP ${revokeResponse.status()} code=${payload.code || 'unknown'}`)
      }
      await expect(page.getByText('REVOKED', { exact: true })).toBeVisible()

      await expect.poll(() => {
        try {
          return docker('inspect', '-f', '{{.State.Running}}', containerName)
        } catch {
          return 'false'
        }
      }, { timeout: 90_000, intervals: [1_000, 2_000] }).toBe('false')
    } finally {
      try { docker('rm', '-f', containerName) } catch { /* resource may already be gone */ }
      try { docker('volume', 'rm', volumeName) } catch { /* resource may already be gone */ }
    }
  })
})
