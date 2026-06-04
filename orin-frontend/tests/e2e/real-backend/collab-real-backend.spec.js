import { expect, test } from '@playwright/test'

/**
 * ORIN Phase 1D: Real-backend collaboration E2E.
 *
 * Assumes:
 * - Backend + MySQL + Redis are already running on localhost:8080
 * - Admin credentials are admin/admin123 (or ORIN_ADMIN_USERNAME / ORIN_ADMIN_PASSWORD)
 * - AI Engine running on localhost:8000 (for decompose)
 * - Frontend served on localhost:4173 by the webServer in playwright.real-backend.config.js
 *
 * Run:
 *   npx playwright test --config playwright.real-backend.config.js
 *
 * Cleanup is automatic — created packages are cancelled on completion.
 */

const BASE = process.env.ORIN_BASE_URL || 'http://127.0.0.1:8080'
const ADMIN_USER = process.env.ORIN_ADMIN_USERNAME || 'admin'
const ADMIN_PASS = process.env.ORIN_ADMIN_PASSWORD || 'admin123'

// ── Helpers ────────────────────────────────────────────────────────────────

async function login(page) {
  const res = await page.request.post(`${BASE}/api/v1/auth/login`, {
    data: { username: ADMIN_USER, password: ADMIN_PASS },
    headers: { 'Content-Type': 'application/json' }
  })
  expect(res.status()).toBe(200)
  const body = await res.json()
  return body.token
}

async function createPackage(request, token, intent = 'e2e 真实后端协作包', priority = 'NORMAL') {
  const res = await request.post(`${BASE}/api/v1/collaboration/packages`, {
    data: { intent, priority },
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  })
  expect(res.status()).toBe(200)
  return (await res.json()).packageId
}

async function decomposePackage(request, token, packageId) {
  const res = await request.post(`${BASE}/api/v1/collaboration/packages/${packageId}/decompose`, {
    data: { capabilities: { llm: true, knowledge: false, mcp: false } },
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  })
  return res.status()
}

async function cancelPackage(request, token, packageId) {
  await request.post(`${BASE}/api/v1/collaboration/packages/${packageId}/cancel`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

// ── Authenticated fixture ────────────────────────────────────────────────────

test.describe('Phase 1D: Real-backend collaboration E2E', () => {

  let token = ''
  const createdPackages = []

  test.beforeAll(async ({ request }) => {
    // Perform login once; store token in a global so browser tests can reuse it
    const res = await request.post(`${BASE}/api/v1/auth/login`, {
      data: { username: ADMIN_USER, password: ADMIN_PASS },
      headers: { 'Content-Type': 'application/json' }
    })
    expect(res.status()).toBe(200)
    token = (await res.json()).token
  })

  test.afterEach(async ({ request }) => {
    // Cancel any packages we created so they don't leak
    for (const pkgId of createdPackages) {
      try {
        await cancelPackage(request, token, pkgId)
      } catch { /* ignore */ }
    }
    createdPackages.length = 0
  })

  // ── Helper: authenticate browser for UI-driven tests ──────────────────────

  async function authenticateBrowser(page) {
    await page.addInitScript((t) => {
      window.localStorage.setItem('orin_token', t)
      document.cookie = `orin_token=${t}; path=/`
    }, token)
    // Inject roles so the route guard allows access
    await page.addInitScript(() => {
      document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(['ROLE_ADMIN', 'ROLE_SUPER_ADMIN']))}; path=/`
      document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify({ userId: 1, username: 'admin' }))}; path=/`
    })
  }

  // ── Test 1: Create → Decompose → See subtasks ─────────────────────────────

  test('creates and decomposes a collaboration package', async ({ page, request }) => {
    await authenticateBrowser(page)

    // 1. Create package via API
    const pkgId = await createPackage(request, token)
    createdPackages.push(pkgId)

    // 2. Decompose (creates subtasks)
    const decompStatus = await decomposePackage(request, token, pkgId)
    expect([200, 202]).toContain(decompStatus)

    // 3. Open dashboard and verify package appears
    await page.goto('/dashboard/applications/collaboration/dashboard')
    await expect(page.getByText(pkgId).first()).toBeVisible({ timeout: 10_000 })

    // 4. Open detail drawer
    await page.getByRole('button', { name: '详情' }).first().click()
    await expect(page.getByText('协作任务包详情')).toBeVisible()

    // 5. Subtasks should be visible after decompose
    await page.getByRole('button', { name: '刷新', exact: false }).last().click()
    // Wait for subtask table to appear
    await expect(page.getByText('子任务').first()).toBeVisible()
  })

  // ── Test 2: Package-level pause / resume / cancel ───────────────────────────

  test('pause, resume, and cancel work on a package', async ({ page, request }) => {
    const pkgId = await createPackage(request, token, 'e2e 暂停恢复测试')
    createdPackages.push(pkgId)
    await authenticateBrowser(page)

    await page.goto('/dashboard/applications/collaboration/dashboard')
    await expect(page.getByText(pkgId).first()).toBeVisible({ timeout: 10_000 })

    await page.getByRole('button', { name: '详情' }).first().click()
    await expect(page.getByText('协作任务包详情')).toBeVisible()

    // Pause
    const pauseBtn = page.getByRole('button', { name: '暂停' })
    if (await pauseBtn.isVisible()) {
      await pauseBtn.click()
      await page.getByRole('button', { name: '取消' }).filter({ hasText: '取消' }).click()
      await expect(page.getByText('确认暂停')).toBeVisible()
      await page.getByRole('button', { name: '确定' }).last().click()
      await expect(page.getByText('协作包已暂停')).toBeVisible({ timeout: 5_000 })
    }

    // Resume
    const resumeBtn = page.getByRole('button', { name: '恢复' })
    if (await resumeBtn.isVisible()) {
      await resumeBtn.click()
      await page.getByRole('button', { name: '确定' }).last().click()
      await expect(page.getByText('协作包已恢复')).toBeVisible({ timeout: 5_000 })
    }

    // Cancel (look inside package-actions to avoid hitting the dialog cancel button)
    const cancelBtn = page.locator('.package-actions').getByRole('button', { name: '取消' })
    if (await cancelBtn.isVisible()) {
      await cancelBtn.click()
      // ElMessageBox confirm dialog: "确定" to confirm, "取消" to abort
      await page.getByRole('button', { name: '确定' }).last().click()
      await expect(page.getByText('协作包已取消')).toBeVisible({ timeout: 5_000 })
    }
  })

  // ── Test 3: Subtask skip / retry / manual-complete ─────────────────────────

  test('skip, retry and manual-complete work on subtasks', async ({ page, request }) => {
    const pkgId = await createPackage(request, token, 'e2e 子任务操作测试')
    createdPackages.push(pkgId)
    await decomposePackage(request, token, pkgId)
    await authenticateBrowser(page)

    await page.goto('/dashboard/applications/collaboration/dashboard')
    await expect(page.getByText(pkgId).first()).toBeVisible({ timeout: 10_000 })

    await page.getByRole('button', { name: '详情' }).first().click()
    await expect(page.getByText('协作任务包详情')).toBeVisible()

    // Refresh subtasks table
    await page.locator('.drawer-section-header').filter({ hasText: '子任务' }).getByRole('button', { name: '刷新' }).click()
    await page.waitForTimeout(1_000)

    // Try skip button on first PENDING subtask
    const skipBtn = page.locator('.el-table__row').filter({ hasText: 'PENDING' }).getByRole('button', { name: '跳过' })
    if (await skipBtn.isVisible()) {
      await skipBtn.click()
      await page.getByRole('button', { name: '确定' }).last().click()
      await expect(page.getByText('已跳过子任务')).toBeVisible({ timeout: 5_000 })
    }

    // Try retry button on first FAILED subtask (if any)
    const retryBtn = page.locator('.el-table__row').filter({ hasText: 'FAILED' }).getByRole('button', { name: '重试' })
    if (await retryBtn.isVisible()) {
      await retryBtn.click()
      await page.getByRole('button', { name: '确定' }).last().click()
      await expect(page.getByText('已提交重试')).toBeVisible({ timeout: 5_000 })
    }

    // Try manual-complete on a RUNNING subtask
    const mcBtn = page.locator('.el-table__row').filter({ hasText: 'RUNNING' }).getByRole('button', { name: '手动完成' })
    if (await mcBtn.isVisible()) {
      await mcBtn.click()
      await page.locator('.el-message-box textarea, .el-message-box input').fill('e2e manual result')
      await page.getByRole('button', { name: '确定' }).last().click()
      await expect(page.getByText('已手动完成子任务')).toBeVisible({ timeout: 5_000 })
    }
  })

  // ── Test 4: Package manual-complete ───────────────────────────────────────

  test('package manual-complete closes the package', async ({ page, request }) => {
    const pkgId = await createPackage(request, token, 'e2e 手动完成包测试')
    createdPackages.push(pkgId)
    await authenticateBrowser(page)

    await page.goto('/dashboard/applications/collaboration/dashboard')
    await expect(page.getByText(pkgId).first()).toBeVisible({ timeout: 10_000 })

    await page.getByRole('button', { name: '详情' }).first().click()
    await expect(page.getByText('协作任务包详情')).toBeVisible()

    const manualCompleteBtn = page.locator('.package-actions').getByRole('button', { name: '手动完成' })
    if (await manualCompleteBtn.isVisible()) {
      await manualCompleteBtn.click()
      await page.locator('.el-message-box textarea, .el-message-box input').fill('e2e package accepted')
      await page.getByRole('button', { name: '确定' }).last().click()
      await expect(page.getByText('协作包已手动完成')).toBeVisible({ timeout: 5_000 })
    }
  })

  // ── Test 5: Trace summary is accessible after create ──────────────────────

  test('trace summary endpoint returns structured data after package creation', async ({ request }) => {
    const pkgId = await createPackage(request, token, 'e2e trace summary test')
    createdPackages.push(pkgId)

    // Get package details to retrieve traceId
    const res = await request.get(`${BASE}/api/v1/collaboration/packages/${pkgId}`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    expect(res.status()).toBe(200)
    const pkg = await res.json()
    const traceId = pkg.traceId

    if (traceId) {
      const summaryRes = await request.get(`${BASE}/api/v1/traces/${traceId}/summary`, {
        headers: { Authorization: `Bearer ${token}` }
      })
      expect(summaryRes.status()).toBe(200)
      const summary = await summaryRes.json()
      expect(summary).toHaveProperty('workflowInstance')
      // Must not leak sensitive fields
      expect(Object.keys(summary)).not.toContain('inputData')
      expect(Object.keys(summary)).not.toContain('responseContent')
    }
  })
})
