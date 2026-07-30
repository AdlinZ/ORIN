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

test('F04 用户旅程：运行列表 → 结果优先详情 → 按需展开诊断 → 取消运行', async ({ page }) => {
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
    if (path.includes('/events') || path.includes('/assignments')) {
      return route.fulfill(json([]))
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

    return route.fulfill(json({}))
  })

  // ---- List page ----
  await page.goto('/workspace/runs', { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('text=运行中心', { timeout: 10000 })

  // The entry page groups raw runtime states by the user's next action.
  await expect(page.getByRole('button', { name: /当前记录/ })).toHaveCount(0)
  await expect(page.getByRole('button', { name: /进行中/ })).toContainText('1')
  await expect(page.getByRole('button', { name: /已产出结果/ })).toContainText('1')
  await expect(page.getByRole('button', { name: /需要处理/ })).toContainText('1')
  await expect(page.getByText('已完成', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('运行中', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('失败', { exact: true }).first()).toBeVisible()
  await expect(page.getByRole('columnheader', { name: '执行节点' })).toHaveCount(0)
  await expect(page.getByRole('columnheader', { name: 'Agent / 版本' })).toHaveCount(0)
  await expect(page.getByRole('columnheader', { name: 'Agent', exact: true })).toBeVisible()
  await expect(page.getByText('run_f04_abc', { exact: true })).toHaveCount(0)

  await page.getByRole('button', { name: /需要处理/ }).click()
  await expect(page.getByRole('row').filter({ hasText: 'hello f04 e2e' })).toHaveCount(1)
  await expect(page.getByRole('row').filter({ hasText: 'Execution error: boom' })).toContainText('处理失败')

  // ---- Detail page (COMPLETED run) — navigate directly ----
  await page.goto('/workspace/runs/run_f04_abc')
  // wait for the page to render run data
  await page.waitForSelector('text=运行结果', { timeout: 10000 })

  // result is primary; diagnostics stay collapsed until requested
  await expect(page.getByText('task output', { exact: true })).toBeVisible()
  await expect(page.getByText('Run finished: run_f04_abc')).toBeHidden()

  await page.getByRole('button', { name: /执行过程/ }).click()
  await expect(page.locator('.el-steps').first()).toBeVisible({ timeout: 5000 })
  await expect(page.locator('text=Run finished: run_f04_abc')).toBeVisible({ timeout: 5000 })
  await expect(page.locator('text=Config snapshot loaded')).toBeVisible({ timeout: 5000 })

  await page.getByRole('button', { name: /技术信息/ }).click()
  await expect(page.locator('text=trace-f04-e2e-aaa')).toBeVisible({ timeout: 5000 })

  // ---- Detail page (RUNNING run) → Cancel ----
  await page.goto('/workspace/runs/run_f04_running')
  await page.waitForSelector('text=运行结果', { timeout: 10000 })
  await page.getByRole('button', { name: '取消运行' }).click()
  await page.getByRole('dialog', { name: '确认取消' }).getByRole('button', { name: '取消运行' }).click()
  await expect.poll(() => cancelCalled).toBe(true)
  await expect(page.getByText('已取消', { exact: true }).first()).toBeVisible()
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

    return route.fulfill(json({}))
  })

  // Navigate to FAILED run detail
  await page.goto('/workspace/runs/run_f04_failed')
  await page.waitForSelector('text=运行结果', { timeout: 10000 })

  await expect(page.getByText('执行过程中出错')).toBeVisible()
  await page.getByRole('button', { name: '重新运行' }).click()
  await expect.poll(() => retryCalled).toBe(true)
  await page.waitForURL('**/workspace/runs/run_f04_retried')
  await expect(page.getByText('排队中', { exact: true }).first()).toBeVisible()
})

test('从版本历史开始运行时保留指定版本、默认在线 Runner，并进入结果页', async ({ page }) => {
  let createPayload = null

  await authenticate(page)
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()

    if (path === '/api/v1/setup/status') {
      return route.fulfill(json({ completed: true, canInitialize: false }))
    }
    if (path === '/api/v1/agents' && method === 'GET') {
      return route.fulfill(json([{
        agentId: 'agent-task',
        name: '任务助手',
        description: '把输入整理成可执行结论',
        activeVersionStatus: 'FROZEN',
        activeVersionId: 'version-2',
        activeVersionNumber: 2,
      }]))
    }
    if (path === '/api/v1/agents/agent-task/versions' && method === 'GET') {
      return route.fulfill(json([
        { id: 'version-1', versionNumber: 1, status: 'FROZEN', changeDescription: '初始版本' },
        { id: 'version-2', versionNumber: 2, status: 'FROZEN', changeDescription: '当前稳定版本' },
      ]))
    }
    if (path === '/api/v1/runners' && method === 'GET') {
      return route.fulfill(json({
        content: [{
          id: 'runner-online',
          name: '本地执行节点',
          hostname: 'runner.local',
          status: 'ONLINE',
        }],
      }))
    }
    if (path === '/api/v1/runs' && method === 'GET') {
      return route.fulfill(json({ content: [], totalElements: 0 }))
    }
    if (path === '/api/v1/runs' && method === 'POST') {
      createPayload = request.postDataJSON()
      return route.fulfill(json({
        id: 'run-created',
        status: 'QUEUED',
        ...createPayload,
      }))
    }
    if (path === '/api/v1/runs/run-created') {
      return route.fulfill(json({
        id: 'run-created',
        status: 'QUEUED',
        input: createPayload?.input,
        retryCount: 0,
        maxRetries: 3,
        ...createPayload,
      }))
    }
    if (path.includes('/events') || path.includes('/logs') || path.includes('/assignments')) {
      return route.fulfill(json([]))
    }
    return route.fulfill(json({}))
  })

  await page.goto(
    '/workspace/runs?create=1&agentId=agent-task&versionId=version-1',
    { waitUntil: 'domcontentloaded' }
  )

  const dialog = page.getByRole('dialog', { name: '开始运行' })
  await expect(dialog).toBeVisible()
  await expect(dialog.locator('.selection-summary strong')).toHaveText('任务助手')
  await expect(dialog.getByText('运行环境已就绪', { exact: true })).toBeVisible()
  await expect(dialog.getByText('系统已自动选择，按需调整', { exact: true })).toBeVisible()
  await expect(dialog.getByRole('button', { name: '开始运行' })).toBeDisabled()
  await dialog.getByPlaceholder('例如：总结这段材料，并列出三个需要继续确认的问题')
    .fill('整理本周风险，并给出下一步')
  await dialog.getByRole('button', { name: '开始运行' }).click()

  await expect.poll(() => createPayload).toMatchObject({
    agentId: 'agent-task',
    agentVersionId: 'version-1',
    runnerId: 'runner-online',
    input: '整理本周风险，并给出下一步',
  })
  await page.waitForURL('**/workspace/runs/run-created')
  await expect(page.getByText('整理本周风险，并给出下一步', { exact: true })).toBeVisible()
})
