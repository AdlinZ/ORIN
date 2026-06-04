import { expect, test } from '@playwright/test'

const now = '2026-05-23T10:00:00Z'

const json = (body) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
})

const token = `header.${Buffer.from(JSON.stringify({
  sub: 'admin',
  exp: Math.floor(Date.now() / 1000) + 3600
})).toString('base64url')}.sig`

const agents = [{
  id: 'agent-1',
  agentId: 'agent-1',
  name: '客服智能体',
  agentName: '客服智能体',
  status: 'RUNNING',
  healthScore: 98,
  viewType: 'CHAT'
}]

async function authenticate(page) {
  await page.addInitScript(({ tokenValue }) => {
    const roles = ['ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_USER']
    window.localStorage.setItem('orin_token', tokenValue)
    window.sessionStorage.setItem('orin_setup_completed', 'true')
    document.cookie = `orin_token=${tokenValue}; path=/`
    document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(roles))}; path=/`
    document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify({
      userId: 1,
      username: 'admin',
      email: 'admin@orin.local',
      nickname: '管理员'
    }))}; path=/`
  }, { tokenValue: token })
}

async function mockWave7Backends(page) {
  await page.route('**/*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname

    if (path === '/api/v1/setup/status') {
      return route.fulfill(json({ completed: true, canInitialize: false }))
    }

    if (path === '/api/v1/users/profile/admin') {
      return route.fulfill(json({
        userId: 1,
        username: 'admin',
        nickname: '管理员',
        email: 'admin@orin.local',
        createTime: now
      }))
    }
    if (path === '/api/v1/users/dashboard/admin') {
      return route.fulfill(json({
        stats: [
          { label: '知识库', value: '3', icon: 'Collection' },
          { label: 'AI 智能体', value: '4', icon: 'DataAnalysis' },
          { label: 'Token 消耗', value: '12K', icon: 'ChatDotRound' },
          { label: '活跃天数', value: '18', icon: 'Calendar' }
        ],
        activityData: [{ label: '今天', value: 80, count: 8 }],
        activityLogs: [{ action: '登录系统', detail: '进入控制台', time: '刚刚', type: 'success' }]
      }))
    }

    if (path === '/api/v1/monitor/dashboard/summary') {
      return route.fulfill(json({
        total_agents: 4,
        daily_requests: 128,
        total_tokens: 12,
        avg_latency: 120
      }))
    }
    if (path === '/api/v1/monitor/agents/list') {
      return route.fulfill(json(agents))
    }
    if (path === '/api/v1/monitor/server-hardware') {
      return route.fulfill(json({ cpuUsage: 28, memoryUsage: 42, gpuUsage: 8, diskUsage: 36 }))
    }

    if (path === '/api/v1/agents') {
      return route.fulfill(json(agents))
    }
    if (path === '/api/v1/agents/chat/sessions') {
      return route.fulfill(json([]))
    }
    if (path === '/api/v1/knowledge/list') {
      return route.fulfill(json([{ id: 'kb-1', name: '产品知识库', documentCount: 5 }]))
    }
    if (path === '/api/v1/models') {
      return route.fulfill(json([{ id: 1, name: 'DeepSeek Chat', modelId: 'deepseek-chat', status: 'ENABLED' }]))
    }

    if (path === '/api/workflows/capabilities') {
      return route.fulfill(json({
        supportedNodeTypes: ['start', 'end', 'llm', 'agent', 'answer'],
        nodeTypes: ['start', 'end', 'llm', 'agent', 'answer']
      }))
    }
    if (path === '/api/workflows') {
      return route.fulfill(json([]))
    }
    if (path.startsWith('/api/workflows/')) {
      return route.fulfill(json({ id: 'wf-1', name: '演示工作流', nodes: [], edges: [] }))
    }

    if (path === '/api/playground/api/agents') {
      return route.fulfill(json(agents.map((agent) => ({ id: agent.id, name: agent.name, skill_ids: [] }))))
    }
    if (path === '/api/playground/api/workflows') {
      return route.fulfill(json([]))
    }
    if (path === '/api/playground/api/workflow-templates') {
      return route.fulfill(json([]))
    }
    if (path === '/api/playground/api/skills') {
      return route.fulfill(json([]))
    }
    if (path === '/api/playground/api/settings') {
      return route.fulfill(json({}))
    }

    if (path.startsWith('/api/')) {
      return route.fulfill(json([]))
    }

    return route.continue()
  })
}

test.describe('Wave 7 special pages browser smoke', () => {
  test('opens public special pages without blank screens or runtime errors', async ({ page }) => {
    const runtimeErrors = []
    page.on('pageerror', (error) => runtimeErrors.push(`pageerror: ${error.message}`))
    page.on('console', (message) => {
      if (message.type() === 'error') runtimeErrors.push(`console error: ${message.text()}`)
    })

    await mockWave7Backends(page)

    const paths = ['/login', '/portal', '/datawall', '/wave7-not-found']

    for (const path of paths) {
      const startErrorCount = runtimeErrors.length
      await page.goto(path, { waitUntil: 'domcontentloaded' })
      await expect(page.locator('body')).not.toHaveText(/^\s*$/)
      expect(runtimeErrors.slice(startErrorCount), path).toEqual([])
    }
  })

  test('opens protected special pages without blank screens or runtime errors', async ({ page }) => {
    const runtimeErrors = []
    page.on('pageerror', (error) => runtimeErrors.push(`pageerror: ${error.message}`))
    page.on('console', (message) => {
      if (message.type() === 'error') runtimeErrors.push(`console error: ${message.text()}`)
    })

    await authenticate(page)
    await mockWave7Backends(page)

    const paths = [
      '/dashboard/profile',
      '/dashboard/applications/playground/run',
      '/dashboard/applications/workflows/visual',
      '/dashboard/unknown-wave7'
    ]

    for (const path of paths) {
      const startErrorCount = runtimeErrors.length
      await page.goto(path, { waitUntil: 'domcontentloaded' })
      await expect(page.locator('body')).not.toHaveText(/^\s*$/)
      await expect(page.locator('body')).not.toContainText('登录工作台')
      expect(runtimeErrors.slice(startErrorCount), path).toEqual([])
    }
  })
})
