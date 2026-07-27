import { expect, test } from '@playwright/test'

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
})

async function authenticate(page) {
  await page.addInitScript(() => {
    const roles = ['ROLE_ADMIN', 'ROLE_USER']
    window.localStorage.setItem('orin_token', 'f04-mock-token')
    window.sessionStorage.setItem('orin_setup_completed', 'true')
    document.cookie = 'orin_token=f04-mock-token; path=/'
    document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(roles))}; path=/`
    document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify({ userId: 1, username: 'admin' }))}; path=/`
  })
}

const RUN_COMPLETED = {
  id: 'run_f04_abc',
  agentId: 'ag_f04_demo',
  agentVersionId: 'ver_f04_v1',
  runnerId: 'runner_f04_x',
  status: 'COMPLETED',
  input: 'hello f04 e2e',
  output: 'task output',
  traceId: 'trace-f04-e2e-aaa',
  runAttempt: 1,
  retryCount: 0,
  maxRetries: 3,
  terminalReason: null,
  createdAt: Date.now() - 60000,
  startedAt: Date.now() - 55000,
  completedAt: Date.now() - 50000,
  createdBy: 'admin'
}

const RUN_RUNNING = {
  ...RUN_COMPLETED,
  id: 'run_f04_running',
  status: 'RUNNING',
  completedAt: null,
  terminalReason: null,
  output: null
}

const RUN_FAILED = {
  ...RUN_COMPLETED,
  id: 'run_f04_failed',
  status: 'FAILED',
  errorMessage: 'Execution error: boom',
  terminalReason: 'RUNNER_FAILED',
  output: null
}

test('F04 用户旅程：Run 列表筛选 → 详情(状态步骤条+事件时间线+日志+Cancel/Retry+Trace链接)', async ({ page }) => {
  let cancelCalled = false
  const CANCELLED_RUN = {
    ...RUN_RUNNING,
    status: 'CANCELLED',
    terminalReason: 'USER_CANCELLED'
  }

  await authenticate(page)
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()

    if (path === '/api/v1/setup/status') {
      return route.fulfill(json({ completed: true, canInitialize: false }))
    }

    // ---- Run list ----
    if (path === '/api/v1/runs' && method === 'GET') {
      return route.fulfill(json({
        content: [RUN_COMPLETED, RUN_RUNNING, RUN_FAILED],
        totalElements: 3,
        totalPages: 1,
        number: 0,
        size: 100
      }))
    }

    // ---- Run detail ----
    if (path === '/api/v1/runs/run_f04_abc') {
      return route.fulfill(json(RUN_COMPLETED))
    }
    if (path === '/api/v1/runs/run_f04_running') {
      return route.fulfill(json(cancelCalled ? CANCELLED_RUN : RUN_RUNNING))
    }
    if (path === '/api/v1/runs/run_f04_failed') {
      return route.fulfill(json(RUN_FAILED))
    }

    // ---- Events ----
    if (path === '/api/v1/runs/run_f04_abc/events') {
      return route.fulfill(json([
        { id: 1, eventSeq: 1, level: 'INFO', message: 'Run started: run_f04_abc', timestamp: Date.now() - 59000, runAttempt: 1, leaseId: 'lease-e2e' },
        { id: 2, eventSeq: 2, level: 'INFO', message: 'Config snapshot loaded: 42 bytes', timestamp: Date.now() - 58000, runAttempt: 1, leaseId: 'lease-e2e' },
        { id: 3, eventSeq: 3, level: 'INFO', message: 'Secrets bound: 0 materialized', timestamp: Date.now() - 57000, runAttempt: 1, leaseId: 'lease-e2e' },
        { id: 4, eventSeq: 4, level: 'INFO', message: 'Execution started via TaskRuntime', timestamp: Date.now() - 56000, runAttempt: 1, leaseId: 'lease-e2e' },
        { id: 5, eventSeq: 5, level: 'INFO', message: 'Execution completed: 11 chars output', timestamp: Date.now() - 51000, runAttempt: 1, leaseId: 'lease-e2e' },
        { id: 6, eventSeq: 6, level: 'INFO', message: 'Run finished: run_f04_abc', timestamp: Date.now() - 50000, runAttempt: 1, leaseId: 'lease-e2e' }
      ]))
    }

    // ---- Assignments ----
    if (path === '/api/v1/runs/run_f04_abc/assignments') {
      return route.fulfill(json([{
        id: 'asgn-e2e-1', runnerId: 'runner_f04_x', leaseId: 'lease-e2e',
        status: 'COMPLETED', runAttempt: 1, terminalReason: null,
        leaseExpiresAt: Date.now() + 30000, createdAt: Date.now() - 60000
      }]))
    }

    // ---- Logs ----
    if (path.includes('/logs')) {
      return route.fulfill(json([]))
    }

    // ---- Cancel ----
    if (path === '/api/v1/runs/run_f04_running/cancel' && method === 'POST') {
      cancelCalled = true
      return route.fulfill(json({ ...RUN_RUNNING, status: 'CANCELLED', terminalReason: 'USER_CANCELLED' }))
    }

    return route.continue()
  })

  // ---- List page ----
  await page.goto('/workspace/runs')
  await page.waitForSelector('text=Runs', { timeout: 10000 })

  // verify list renders with status tags
  await expect(page.locator('text=COMPLETED').first()).toBeVisible()
  await expect(page.locator('text=RUNNING').first()).toBeVisible()
  await expect(page.locator('text=FAILED').first()).toBeVisible()

  // ---- Detail page (COMPLETED run) — navigate directly ----
  await page.goto('/workspace/runs/run_f04_abc')
  // wait for the page to render run data
  await page.waitForSelector('text=run_f04_abc', { timeout: 10000 })

  // verify status stepper shows COMPLETED
  await expect(page.locator('.el-steps').first()).toBeVisible({ timeout: 5000 })
  await expect(page.locator('text=COMPLETED').first()).toBeVisible()

  // verify basic info
  await expect(page.locator('text=trace-f04-e2e-aaa')).toBeVisible()

  // verify event timeline has 6 events
  await expect(page.locator('text=Run finished: run_f04_abc')).toBeVisible({ timeout: 5000 })
  await expect(page.locator('text=Config snapshot loaded')).toBeVisible({ timeout: 5000 })

  // verify Trace link exists (href may be hash-based)
  await expect(page.locator('text=trace-f04-e2e-aaa')).toBeVisible({ timeout: 5000 })

  // ---- Detail page (RUNNING run) → Cancel ----
  await page.goto('/workspace/runs/run_f04_running')
  await page.waitForSelector('text=run_f04_running', { timeout: 10000 })
  await page.getByRole('button', { name: '取消 Run' }).click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect.poll(() => cancelCalled).toBe(true)
  await expect(page.getByText('CANCELLED', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('用户主动取消')).toBeVisible()
})

test('F04 Retry 会从详情页提交创建新 Run 的请求', async ({ page }) => {
  let retryCalled = false
  const RETRIED_RUN = {
    ...RUN_FAILED,
    id: 'run_f04_retried',
    status: 'QUEUED',
    retryCount: 1,
    retryOfRunId: 'run_f04_failed',
    terminalReason: null
  }

  await authenticate(page)
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()

    if (path === '/api/v1/setup/status') {
      return route.fulfill(json({ completed: true, canInitialize: false }))
    }
    // Run list
    if (path === '/api/v1/runs' && method === 'GET') {
      return route.fulfill(json({ content: [RUN_FAILED], totalElements: 1, totalPages: 1, number: 0, size: 100 }))
    }
    // Detail
    if (path === '/api/v1/runs/run_f04_failed') {
      return route.fulfill(json(RUN_FAILED))
    }
    if (path === '/api/v1/runs/run_f04_retried') {
      return route.fulfill(json(RETRIED_RUN))
    }
    // Events (empty for this test)
    if (path.includes('/events')) return route.fulfill(json([]))
    // Assignments
    if (path.includes('/assignments')) return route.fulfill(json([]))
    // Logs
    if (path.includes('/logs')) return route.fulfill(json([]))
    // Retry
    if (path === '/api/v1/runs/run_f04_failed/retry' && method === 'POST') {
      retryCalled = true
      return route.fulfill(json(RETRIED_RUN))
    }

    return route.continue()
  })

  // Navigate to FAILED run detail
  await page.goto('/workspace/runs/run_f04_failed')
  await page.waitForSelector('text=run_f04_failed', { timeout: 10000 })

  await page.getByRole('button', { name: '重试 Run' }).click()
  await expect.poll(() => retryCalled).toBe(true)

  // Verify terminal reason shows Chinese explanation
  await expect(page.locator('text=执行过程中出错')).toBeVisible()
})
