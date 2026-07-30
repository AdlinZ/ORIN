import { expect, test } from '@playwright/test'

const json = (body) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
})

const now = '2026-05-21T10:00:00Z'

function createCollaborationBackend() {
  const packages = [
    {
      packageId: 'pkg-exec',
      intent: '浏览器验收协作任务',
      status: 'EXECUTING',
      priority: 'HIGH',
      collaborationMode: 'SEQUENTIAL',
      traceId: 'trace-e2e-001',
      createdAt: now,
      updatedAt: now
    },
    {
      packageId: 'pkg-paused',
      intent: '暂停中的协作任务',
      status: 'PAUSED',
      priority: 'NORMAL',
      collaborationMode: 'PARALLEL',
      traceId: 'trace-e2e-002',
      createdAt: now,
      updatedAt: now
    },
    {
      packageId: 'pkg-failed',
      intent: '失败的协作任务',
      status: 'FAILED',
      priority: 'NORMAL',
      collaborationMode: 'CONSENSUS',
      traceId: 'trace-e2e-003',
      createdAt: now,
      updatedAt: now
    }
  ]

  const subtasks = {
    'pkg-exec': [
      {
        subTaskId: 'sub-pending',
        description: '等待人工判断是否跳过',
        expectedRole: 'planner',
        status: 'PENDING',
        retryCount: 0
      },
      {
        subTaskId: 'sub-failed',
        description: '失败后需要重试',
        expectedRole: 'worker',
        status: 'FAILED',
        retryCount: 1
      },
      {
        subTaskId: 'sub-running',
        description: '人工补齐执行结果',
        expectedRole: 'reviewer',
        status: 'RUNNING',
        retryCount: 0
      }
    ]
  }

  const events = {
    'pkg-exec': [
      {
        eventId: 'evt-created',
        eventType: 'PACKAGE_CREATED',
        message: '协作包已创建',
        actor: 'system',
        status: 'success',
        timestamp: now
      }
    ]
  }

  const requests = []

  const addEvent = (packageId, eventType, message) => {
    events[packageId] = [
      ...(events[packageId] || []),
      {
        eventId: `${eventType}-${events[packageId]?.length || 0}`,
        eventType,
        message,
        actor: 'operator',
        status: 'success',
        timestamp: now
      }
    ]
  }

  const updatePackage = (packageId, patch) => {
    const target = packages.find((item) => item.packageId === packageId)
    if (target) Object.assign(target, patch, { updatedAt: now })
  }

  const updateSubtask = (packageId, subTaskId, patch) => {
    const target = subtasks[packageId]?.find((item) => item.subTaskId === subTaskId)
    if (target) Object.assign(target, patch)
  }

  const route = async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    requests.push({ method: request.method(), path })

    if (request.method() === 'GET' && path === '/collaboration/stats') {
      return route.fulfill(json({
        totalTasks: packages.length,
        completedTasks: packages.filter((item) => item.status === 'COMPLETED').length,
        executingTasks: packages.filter((item) => item.status === 'EXECUTING').length,
        failedTasks: packages.filter((item) => item.status === 'FAILED').length
      }))
    }

    if (request.method() === 'GET' && path === '/collaboration/packages') {
      return route.fulfill(json(packages))
    }

    const eventMatch = path.match(/^\/collaboration\/events\/([^/]+)$/)
    if (request.method() === 'GET' && eventMatch) {
      return route.fulfill(json(events[eventMatch[1]] || []))
    }

    const runtimeMatch = path.match(/^\/collaboration\/packages\/([^/]+)\/runtime$/)
    if (request.method() === 'GET' && runtimeMatch) {
      return route.fulfill(json({
        packageId: runtimeMatch[1],
        queue: 'collaboration-e2e',
        worker: 'mock-worker',
        status: packages.find((item) => item.packageId === runtimeMatch[1])?.status
      }))
    }

    const diagnosticsMatch = path.match(/^\/collaboration\/packages\/([^/]+)\/diagnostics$/)
    if (request.method() === 'GET' && diagnosticsMatch) {
      return route.fulfill(json({
        packageId: diagnosticsMatch[1],
        mq: 'healthy',
        traceId: packages.find((item) => item.packageId === diagnosticsMatch[1])?.traceId
      }))
    }

    const subtasksMatch = path.match(/^\/collaboration\/packages\/([^/]+)\/subtasks$/)
    if (request.method() === 'GET' && subtasksMatch) {
      return route.fulfill(json(subtasks[subtasksMatch[1]] || []))
    }

    const packageOpMatch = path.match(/^\/collaboration\/packages\/([^/]+)\/(pause|resume|cancel|manual-complete)$/)
    if (request.method() === 'POST' && packageOpMatch) {
      const [, packageId, operation] = packageOpMatch
      const nextStatus = {
        pause: 'PAUSED',
        resume: 'EXECUTING',
        cancel: 'CANCELLED',
        'manual-complete': 'COMPLETED'
      }[operation]
      updatePackage(packageId, { status: nextStatus })
      addEvent(packageId, `PACKAGE_${operation.toUpperCase().replace('-', '_')}`, `协作包${nextStatus}`)
      return route.fulfill(json({ success: true }))
    }

    const subtaskOpMatch = path.match(/^\/collaboration\/packages\/([^/]+)\/subtasks\/([^/]+)\/(retry|skip|manual-complete)$/)
    if (request.method() === 'POST' && subtaskOpMatch) {
      const [, packageId, subTaskId, operation] = subtaskOpMatch
      const nextStatus = {
        retry: 'RUNNING',
        skip: 'SKIPPED',
        'manual-complete': 'COMPLETED'
      }[operation]
      updateSubtask(packageId, subTaskId, {
        status: nextStatus,
        retryCount: operation === 'retry' ? 2 : undefined
      })
      addEvent(packageId, `SUBTASK_${operation.toUpperCase().replace('-', '_')}`, `${subTaskId} ${nextStatus}`)
      return route.fulfill(json({ success: true }))
    }

    return route.fulfill(json({}))
  }

  return { route, requests }
}

function createApiKeyBackend() {
  const keys = [
    {
      id: 'gsec_self',
      keyPrefix: 'sk-orin-self',
      name: '个人 MCP Key',
      description: '自助创建的 Key',
      enabled: true,
      status: 'ACTIVE',
      rateLimitPerMinute: 100,
      rateLimitPerDay: 10000,
      monthlyTokenQuota: 1000000,
      usedTokens: 1200,
      quotaPercentage: 0.12,
      userId: '1',
      createdAt: now,
      lastUsedAt: now
    }
  ]
  const requests = []

  const route = async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    requests.push({ method: request.method(), path })

    if (request.method() === 'GET' && path === '/api-keys') {
      return route.fulfill(json(keys))
    }

    if (request.method() === 'POST' && path === '/api-keys') {
      const payload = request.postDataJSON()
      const created = {
        id: 'gsec_created',
        keyPrefix: 'sk-orin-new',
        name: payload.name,
        description: payload.description,
        enabled: true,
        status: 'ACTIVE',
        rateLimitPerMinute: 100,
        rateLimitPerDay: 10000,
        monthlyTokenQuota: 1000000,
        usedTokens: 0,
        quotaPercentage: 0,
        userId: '1',
        createdAt: now
      }
      keys.unshift(created)
      return route.fulfill(json({
        apiKey: created,
        secretKey: 'sk-orin-created-secret',
        warning: 'only once'
      }))
    }

    const toggleMatch = path.match(/^\/api-keys\/([^/]+)\/(disable|enable)$/)
    if (request.method() === 'PATCH' && toggleMatch) {
      const [, keyId, operation] = toggleMatch
      const key = keys.find((item) => item.id === keyId)
      if (key) {
        key.enabled = operation === 'enable'
        key.status = key.enabled ? 'ACTIVE' : 'DISABLED'
      }
      return route.fulfill(json({ success: true }))
    }

    const rotateMatch = path.match(/^\/api-keys\/([^/]+)\/rotate$/)
    if (request.method() === 'POST' && rotateMatch) {
      const key = keys.find((item) => item.id === rotateMatch[1])
      return route.fulfill(json({
        apiKey: key,
        secretKey: 'sk-orin-rotated-secret',
        warning: 'only once'
      }))
    }

    const usageMatch = path.match(/^\/api-keys\/([^/]+)\/usage$/)
    if (request.method() === 'GET' && usageMatch) {
      return route.fulfill(json({
        keyId: usageMatch[1],
        totalCalls: 3,
        successCalls: 2,
        failedCalls: 1,
        failureRate: 33.3,
        averageLatencyMs: 88,
        tokensInWindow: 120,
        recentEvents: [
          {
            source: 'GATEWAY',
            method: 'POST',
            path: '/v1/mcp',
            statusCode: 200,
            success: true,
            latencyMs: 88,
            traceId: 'trace-api-key-self-service',
            createdAt: now
          }
        ]
      }))
    }

    const deleteMatch = path.match(/^\/api-keys\/([^/]+)$/)
    if (request.method() === 'DELETE' && deleteMatch) {
      const index = keys.findIndex((item) => item.id === deleteMatch[1])
      if (index >= 0) keys.splice(index, 1)
      return route.fulfill(json({ success: true }))
    }

    return route.fulfill(json({}))
  }

  return { route, requests }
}

async function authenticate(page, roles = ['ROLE_ADMIN']) {
  await page.addInitScript((value) => {
    window.localStorage.setItem('orin_token', 'e2e-token')
    window.localStorage.setItem('orin_menu_mode', 'topbar')
    document.cookie = 'orin_token=e2e-token; path=/'
    document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(value))}; path=/`
    document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify({ userId: 1, username: 'admin' }))}; path=/`
  }, roles)
}

async function confirmMessageBox(page) {
  await page.locator('.el-message-box .el-button--primary').last().click({ force: true })
}

async function fillMessageBoxPrompt(page, value) {
  const box = page.locator('.el-message-box')
  await box.locator('textarea, input').fill(value)
  await box.locator('.el-button--primary').last().click({ force: true })
}

test.describe('CollaborationDashboardV2 browser acceptance', () => {
  test('validates package and subtask interventions refresh visible state', async ({ page }) => {
    const backend = createCollaborationBackend()
    await authenticate(page)
    await page.route('**/api/v1/**', backend.route)

    await page.goto('/dashboard/applications/collaboration/dashboard')

    await expect(page.getByText('多智能体协作').first()).toBeVisible()
    await expect(page.getByText('3 个结果')).toBeVisible()
    await expect(page.getByText('pkg-exec').first()).toBeVisible()
    await expect(page.getByText('EXECUTING').first()).toBeVisible()
    await expect(page.getByText('PAUSED').first()).toBeVisible()
    await expect(page.getByText('FAILED').first()).toBeVisible()

    await page.getByRole('button', { name: '事件流' }).first().click()
    await expect(page.getByText('PACKAGE_CREATED').first()).toBeVisible()

    await page.getByRole('button', { name: '详情' }).first().click()
    await expect(page.getByText('协作任务包详情')).toBeVisible()
    const detailDrawer = page.locator('.el-drawer').filter({ hasText: '协作任务包详情' })
    const packageSummary = detailDrawer.locator('.el-descriptions').first()
    await expect(page.getByRole('button', { name: 'trace-e2e-001' })).toBeVisible()
    await expect(page.getByText('mock-worker')).toBeVisible()
    await expect(page.getByText('healthy')).toBeVisible()
    await expect(page.getByText('sub-pending')).toBeVisible()
    await expect(page.getByText('sub-failed')).toBeVisible()
    await expect(page.getByText('sub-running')).toBeVisible()

    await page.getByRole('button', { name: '暂停' }).click()
    await confirmMessageBox(page)
    await expect(page.getByText('PACKAGE_PAUSE').first()).toBeVisible()
    await expect(packageSummary).toContainText('PAUSED')

    await page.getByRole('button', { name: '恢复' }).click()
    await confirmMessageBox(page)
    await expect(page.getByText('PACKAGE_RESUME').first()).toBeVisible()
    await expect(packageSummary).toContainText('EXECUTING')

    await page.locator('.el-table__row').filter({ hasText: 'sub-pending' }).getByRole('button', { name: '跳过' }).first().click()
    await confirmMessageBox(page)
    await expect(page.getByText('SUBTASK_SKIP').first()).toBeVisible()
    await expect(page.locator('.el-table__row').filter({ hasText: 'sub-pending' }).first()).toContainText('SKIPPED')

    await page.locator('.el-table__row').filter({ hasText: 'sub-failed' }).getByRole('button', { name: '重试' }).first().click()
    await confirmMessageBox(page)
    await expect(page.getByText('SUBTASK_RETRY').first()).toBeVisible()
    await expect(page.locator('.el-table__row').filter({ hasText: 'sub-failed' }).first()).toContainText('RUNNING')

    await page.locator('.el-table__row').filter({ hasText: 'sub-running' }).getByRole('button', { name: '手动完成' }).first().click()
    await fillMessageBoxPrompt(page, 'review finished')
    await expect(page.getByText('SUBTASK_MANUAL_COMPLETE').first()).toBeVisible()
    await expect(page.locator('.el-table__row').filter({ hasText: 'sub-running' }).first()).toContainText('COMPLETED')

    await page.locator('.package-actions').getByRole('button', { name: '手动完成' }).click()
    await fillMessageBoxPrompt(page, 'package accepted')
    await expect(page.getByText('PACKAGE_MANUAL_COMPLETE').first()).toBeVisible()
    await expect(packageSummary).toContainText('COMPLETED')

    expect(backend.requests).toEqual(expect.arrayContaining([
      expect.objectContaining({ method: 'POST', path: '/collaboration/packages/pkg-exec/pause' }),
      expect.objectContaining({ method: 'POST', path: '/collaboration/packages/pkg-exec/resume' }),
      expect.objectContaining({ method: 'POST', path: '/collaboration/packages/pkg-exec/manual-complete' }),
      expect.objectContaining({ method: 'POST', path: '/collaboration/packages/pkg-exec/subtasks/sub-pending/skip' }),
      expect.objectContaining({ method: 'POST', path: '/collaboration/packages/pkg-exec/subtasks/sub-failed/retry' }),
      expect.objectContaining({ method: 'POST', path: '/collaboration/packages/pkg-exec/subtasks/sub-running/manual-complete' })
    ]))
  })

  test('filters dashboard navigation by role and redirects unauthorized direct access', async ({ page }) => {
    await page.route('**/api/v1/**', async (route) => route.fulfill(json([])))

    await authenticate(page, ['ROLE_OPERATOR'])
    await page.goto('/dashboard')

    await expect(page).toHaveURL(/\/dashboard\/applications\/agents/)
    const operatorNav = page.locator('.navbar-menu')
    await expect(operatorNav.getByText('智能体管理')).toBeVisible()
    await expect(operatorNav.getByText('工作流管理')).toBeVisible()
    await expect(operatorNav.getByText('知识库管理')).toBeVisible()
    await expect(operatorNav.getByText('运行监控')).toHaveCount(0)
    await expect(operatorNav.getByText('系统设置')).toHaveCount(0)

    await page.goto('/dashboard/control/users')
    await expect(page).toHaveURL(/\/dashboard\/applications\/agents/)
  })

  test('keeps regular users on the service portal instead of dashboard modules', async ({ page }) => {
    await page.route('**/api/v1/**', async (route) => route.fulfill(json([])))

    await authenticate(page, ['ROLE_USER'])
    await page.goto('/dashboard')

    await expect(page).toHaveURL(/\/portal/)
    await page.goto('/dashboard/applications/agents')
    await expect(page).toHaveURL(/\/portal/)
  })

  test('lets regular users manage only self-service API keys from the portal route', async ({ page }) => {
    const backend = createApiKeyBackend()
    await page.route('**/api/v1/**', backend.route)

    await authenticate(page, ['ROLE_USER'])
    await page.goto('/portal/api-keys')

    await expect(page.getByText('API Key 自助').first()).toBeVisible()
    await expect(page.getByText('个人 MCP Key')).toBeVisible()
    await expect(page.getByText('外部供应商密钥')).toHaveCount(0)
    await expect(page.getByRole('button', { name: '查看明文' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '重置配额' })).toHaveCount(0)

    await page.getByRole('button', { name: '创建平台密钥' }).click()
    const dialog = page.locator('.el-dialog').filter({ hasText: '创建API密钥' })
    await dialog.getByPlaceholder('为密钥取一个名称').fill('门户自助 Key')
    await dialog.getByPlaceholder('密钥用途描述').fill('portal self-service')
    await dialog.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('sk-orin-created-secret')).toBeVisible()
    await page.getByRole('button', { name: '我已处理' }).click()
    await expect(page.getByText('门户自助 Key')).toBeVisible()

    await page.locator('.el-table__row').filter({ hasText: '门户自助 Key' }).getByRole('button', { name: '禁用' }).click()
    await expect(page.locator('.el-table__row').filter({ hasText: '门户自助 Key' }).first()).toContainText('禁用')

    await page.locator('.el-table__row').filter({ hasText: '门户自助 Key' }).getByRole('button', { name: '轮换' }).click()
    await confirmMessageBox(page)
    await expect(page.getByText('sk-orin-rotated-secret')).toBeVisible()
    await page.getByRole('button', { name: '我已处理' }).click()

    await page.locator('.el-table__row').filter({ hasText: '门户自助 Key' }).getByRole('button', { name: '历史' }).click()
    await expect(page.getByText('trace-api-key-self-service')).toBeVisible()
    await page.getByRole('button', { name: '关闭' }).click()

    expect(backend.requests).toEqual(expect.arrayContaining([
      expect.objectContaining({ method: 'GET', path: '/api-keys' }),
      expect.objectContaining({ method: 'POST', path: '/api-keys' }),
      expect.objectContaining({ method: 'PATCH', path: '/api-keys/gsec_created/disable' }),
      expect.objectContaining({ method: 'POST', path: '/api-keys/gsec_created/rotate' }),
      expect.objectContaining({ method: 'GET', path: '/api-keys/gsec_created/usage' })
    ]))
    expect(backend.requests).not.toEqual(expect.arrayContaining([
      expect.objectContaining({ path: '/api-keys/external' })
    ]))
  })
})
