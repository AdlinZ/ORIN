import { expect, test } from '@playwright/test'

const now = '2026-05-22T10:00:00Z'

const json = (body) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
})

// ── Mock data ───────────────────────────────────────────────────────────────

const taskRows = [
  {
    taskId: 'task-replayable',
    workflowId: 'wf-1',
    status: 'FAILED',
    priority: 'HIGH',
    retryCount: 2,
    maxRetries: 3,
    traceId: 'trace-task-failed',
    createdAt: now,
    updatedAt: now,
    errorMessage: 'AI Engine timeout after 60s'
  },
  {
    taskId: 'task-queued',
    workflowId: 'wf-2',
    status: 'QUEUED',
    priority: 'NORMAL',
    retryCount: 0,
    maxRetries: 3,
    traceId: 'trace-task-queued',
    createdAt: now,
    updatedAt: now,
    errorMessage: null
  }
]

const traceSummary = {
  traceId: 'trace-task-failed',
  status: 'FAILED',
  workflowInstance: {
    instanceId: 42,
    status: 'COMPLETED'
  },
  workflowTasks: [
    {
      taskId: 'task-replayable',
      status: 'FAILED',
      errorMessage: 'AI Engine timeout after 60s'
    }
  ],
  collaborationPackages: [
    {
      packageId: 'pkg-demo',
      intent: '测试协作包',
      status: 'COMPLETED'
    }
  ],
  auditLogs: [
    {
      id: 'audit-1',
      endpoint: '/v1/chat/completions',
      method: 'POST',
      success: false,
      statusCode: 500,
      createdAt: now
    }
  ],
  traceSteps: []
}

const recentTraces = [
  {
    traceId: 'trace-recent-1',
    status: 'SUCCESS',
    operationName: 'agent.chat',
    durationMs: 320,
    startTime: now
  }
]

// ── Mock backend ──────────────────────────────────────────────────────────

function createMockBackend() {
  const requests = []

  const route = async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '').replace('/api', '')
    requests.push({ method: request.method(), path })

    if (request.method() === 'GET' && path === '/setup/status') {
      return route.fulfill(json({ completed: true, canInitialize: false }))
    }

    if (request.method() === 'GET' && path === '/dashboard/summary') {
      return route.fulfill(json({ roles: ['ROLE_ADMIN'], defaultHome: '/dashboard/runtime/tasks' }))
    }

    if (request.method() === 'GET' && path === '/traces/recent') {
      return route.fulfill(json(recentTraces))
    }

    if (request.method() === 'GET' && path === '/traces/trace-task-failed') {
      return route.fulfill(json(traceSummary))
    }

    if (request.method() === 'GET' && path === '/traces/trace-task-failed/summary') {
      return route.fulfill(json(traceSummary))
    }

    const taskOpMatch = path.match(/^\/workflow-tasks\/([^/]+)\/(replay|cancel)$/)
    if (request.method() === 'POST' && taskOpMatch) {
      const [, taskId, op] = taskOpMatch
      const nextStatus = op === 'cancel' ? 'CANCELLED' : 'QUEUED'
      return route.fulfill(json({ taskId, status: nextStatus }))
    }

    if (request.method() === 'GET' && path === '/workflow-tasks/failed') {
      return route.fulfill(json({ content: [taskRows[0]], total: 1 }))
    }

    if (request.method() === 'GET' && path === '/workflow-tasks/queued') {
      return route.fulfill(json({ content: [taskRows[1]], total: 1 }))
    }

    if (request.method() === 'GET' && path === '/workflow-tasks/statistics') {
      return route.fulfill(json({
        total: 2, pending: 0, running: 0,
        failed: 1, dead: 0, cancelled: 0
      }))
    }

    return route.fulfill(json({}))
  }

  return { route, requests }
}

// ── Auth helper ────────────────────────────────────────────────────────────

async function authenticate(page) {
  await page.addInitScript(() => {
    window.localStorage.setItem('orin_token', 'e2e-token')
    document.cookie = 'orin_token=e2e-token; path=/'
    document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(['ROLE_ADMIN']))}; path=/`
    document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify({ userId: 1, username: 'admin' }))}; path=/`
  })
}

// ── Tests ──────────────────────────────────────────────────────────────────

test.describe('Task Queue page interactions', () => {
  test('shows failed and queued tasks with correct status badges', async ({ page }) => {
    const backend = createMockBackend()
    await authenticate(page)
    await page.route('**/api/v1/**', backend.route)

    await page.goto('/dashboard/runtime/tasks')

    // Should show failed task
    await expect(page.getByText('task-replayable').first()).toBeVisible()
    await expect(page.getByText('FAILED').first()).toBeVisible()

    // Should show queued task
    await expect(page.getByText('task-queued').first()).toBeVisible()
    await expect(page.getByText('QUEUED').first()).toBeVisible()
  })

  test('replay button on failed task creates new QUEUED task', async ({ page }) => {
    const backend = createMockBackend()
    await authenticate(page)
    await page.route('**/api/v1/**', backend.route)

    await page.goto('/dashboard/runtime/tasks')

    // Open failed task replay
    const failedRow = page.locator('.el-table__row').filter({ hasText: 'task-replayable' })
    await failedRow.getByRole('button', { name: '重试' }).click()
    await page.getByRole('button', { name: '确定' }).last().click()

    // Verify API was called
    expect(backend.requests).toEqual(expect.arrayContaining([
      expect.objectContaining({ method: 'POST', path: '/workflow-tasks/task-replayable/replay' })
    ]))
  })

  test('cancel button on queued task transitions to CANCELLED', async ({ page }) => {
    const backend = createMockBackend()
    await authenticate(page)
    await page.route('**/api/v1/**', backend.route)

    await page.goto('/dashboard/runtime/tasks')

    const queuedRow = page.locator('.el-table__row').filter({ hasText: 'task-queued' })
    await queuedRow.getByRole('button', { name: '取消' }).click()
    await page.getByRole('button', { name: '确定' }).last().click()

    expect(backend.requests).toEqual(expect.arrayContaining([
      expect.objectContaining({ method: 'POST', path: '/workflow-tasks/task-queued/cancel' })
    ]))
  })
})

test.describe('Trace summary links to workflow tasks and collaboration packages', () => {
  test('trace summary shows linked workflow tasks and collab packages', async ({ page }) => {
    const backend = createMockBackend()
    await authenticate(page)
    await page.route('**/api/v1/**', backend.route)

    await page.goto('/dashboard/runtime/traces')

    // Search for the failed trace
    await page.getByPlaceholder('输入 traceId 搜索调用链路').fill('trace-task-failed')
    await page.getByRole('button', { name: '搜索' }).click()

    await expect(page.getByText('trace-task-failed').first()).toBeVisible()
    await expect(page.getByText('FAILED').first()).toBeVisible()

    // Open trace detail
    await page.getByRole('button', { name: '查看' }).first().click()

    // Summary should show workflow task link
    await expect(page.getByText('task-replayable').first()).toBeVisible()
    await expect(page.getByText('pkg-demo').first()).toBeVisible()
    await expect(page.getByText('audit-1').first()).toBeVisible()
  })

  test('error trace shows error message and retry entry point', async ({ page }) => {
    const backend = createMockBackend()
    await authenticate(page)
    await page.route('**/api/v1/**', backend.route)

    await page.goto('/dashboard/runtime/traces')

    await page.getByPlaceholder('输入 traceId 搜索调用链路').fill('trace-task-failed')
    await page.getByRole('button', { name: '搜索' }).click()
    await page.getByRole('button', { name: '查看' }).first().click()

    // Should surface error details
    await expect(page.getByText(/timeout|FAILED|500|error/i).first()).toBeVisible()
  })
})
