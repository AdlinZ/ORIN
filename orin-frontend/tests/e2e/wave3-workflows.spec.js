import { expect, test } from '@playwright/test'

const now = '2026-05-22T10:00:00Z'

const workflowDefinition = {
  workflow: {
    graph: {
      nodes: [
        { id: 'start_1', type: 'start', position: { x: 120, y: 180 }, data: { id: 'start_1', label: '开始' } },
        { id: 'end_1', type: 'end', position: { x: 520, y: 180 }, data: { id: 'end_1', label: '结束' } }
      ],
      edges: [
        { id: 'edge-start-end', source: 'start_1', target: 'end_1', type: 'smoothstep' }
      ]
    }
  }
}

const workflows = [
  {
    id: 'wf-1',
    workflowName: '订单审核工作流',
    description: 'Wave 3 浏览器验收工作流',
    status: 'ACTIVE',
    workflowType: 'DAG',
    workflowDefinition,
    updatedAt: now,
    createdAt: now
  }
]

const json = (body) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
})

async function authenticate(page) {
  await page.addInitScript(() => {
    const roles = ['ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_USER']
    window.localStorage.setItem('orin_token', 'wave3-token')
    window.sessionStorage.setItem('orin_setup_completed', 'true')
    document.cookie = 'orin_token=wave3-token; path=/'
    document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(roles))}; path=/`
    document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify({ userId: 1, username: 'admin' }))}; path=/`
  })
}

async function mockBackends(page) {
  const routeBackend = async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const method = request.method()

    if (path === '/api/v1/setup/status') {
      return route.fulfill(json({ completed: true, canInitialize: false }))
    }
    if (path === '/api/workflows' && method === 'GET') {
      return route.fulfill(json(workflows))
    }
    if (path === '/api/workflows' && method === 'POST') {
      return route.fulfill(json({ ...workflows[0], id: 'wf-created', status: 'DRAFT' }))
    }
    if (path === '/api/workflows/capabilities') {
      return route.fulfill(json({ supportedNodeTypes: ['start', 'end', 'agent', 'skill', 'llm', 'answer'] }))
    }
    if (path === '/api/workflows/wf-1' && method === 'GET') {
      return route.fulfill(json(workflows[0]))
    }
    if (path === '/api/workflows/wf-1' && method === 'PUT') {
      return route.fulfill(json(workflows[0]))
    }
    if (path === '/api/workflows/wf-1/publish') {
      return route.fulfill(json({ success: true }))
    }
    if (path === '/api/workflows/wf-1/instances') {
      return route.fulfill(json([
        {
          id: 'inst-1',
          workflowId: 'wf-1',
          status: 'COMPLETED',
          createdAt: now,
          finishedAt: now,
          traceId: 'trace-wave3'
        }
      ]))
    }
    if (path === '/api/workflows/instances/inst-1') {
      return route.fulfill(json({
        id: 'inst-1',
        status: 'COMPLETED',
        inputData: { query: 'hello' },
        outputData: { answer: 'ok' },
        traceId: 'trace-wave3'
      }))
    }
    if (path === '/api/v1/workflow-tasks/workflow/wf-1') {
      return route.fulfill(json({
        content: [{
          taskId: 'task-1',
          workflowInstanceId: 'inst-1',
          status: 'COMPLETED',
          retryCount: 0,
          maxRetries: 3,
          updatedAt: now
        }]
      }))
    }
    if (path === '/api/v1/agents') {
      return route.fulfill(json([{ id: 'agent-1', name: '客服智能体', agentName: '客服智能体' }]))
    }
    if (path === '/api/v1/models') {
      return route.fulfill(json([{ id: 1, name: 'DeepSeek Chat', modelId: 'deepseek-chat', provider: 'DeepSeek', type: 'CHAT', status: 'ENABLED' }]))
    }
    if (path === '/api/v1/knowledge/list') {
      return route.fulfill(json([]))
    }

    return route.fulfill(json({}))
  }

  await page.route('**/api/v1/**', routeBackend)
  await page.route('**/api/workflows**', routeBackend)
}

test.describe('Wave 3 workflow domain browser smoke', () => {
  test('opens workflow pages and legacy redirects without blank screens or runtime errors', async ({ page }) => {
    const runtimeErrors = []
    page.on('pageerror', (error) => runtimeErrors.push(`pageerror: ${error.message}`))
    page.on('console', (message) => {
      if (message.type() === 'error') runtimeErrors.push(`console error: ${message.text()}`)
    })

    await authenticate(page)
    await mockBackends(page)

    const paths = [
      '/dashboard/applications/workflows',
      '/dashboard/applications/workflows/execution?workflowId=wf-1',
      '/dashboard/applications/workflows/create',
      '/dashboard/applications/workflows/edit/wf-1',
      '/dashboard/applications/workflows/visual',
      '/dashboard/applications/workflows/visual/wf-1',
      '/dashboard/workflow/list',
      '/dashboard/applications/workflows-v2/canvas'
    ]

    for (const path of paths) {
      const startErrorCount = runtimeErrors.length
      await page.goto(path, { waitUntil: 'networkidle' })
      await expect(page.locator('body')).not.toHaveText(/^\\s*$/)
      await expect(page.locator('body')).not.toContainText('登录工作台')
      expect(runtimeErrors.slice(startErrorCount), path).toEqual([])
    }

    await expect(page).toHaveURL(/\/dashboard\/applications\/workflows\/visual/)
  })
})
