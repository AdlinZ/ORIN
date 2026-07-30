import { expect, test } from '@playwright/test'

const json = (body) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
})

const token = `header.${Buffer.from(JSON.stringify({
  sub: 'admin',
  exp: Math.floor(Date.now() / 1000) + 3600
})).toString('base64url')}.sig`

const agents = [
  {
    id: 'agent-1',
    agentId: 'agent-1',
    name: '客服智能体',
    agentName: '客服智能体',
    status: 'RUNNING',
    providerType: 'OpenAI',
    modelName: 'gpt-4o-mini',
    viewType: 'CHAT'
  }
]

const skills = [
  {
    id: 1,
    skillName: 'SearchDocs',
    skillType: 'KNOWLEDGE',
    status: 'ACTIVE',
    version: '1.0.0',
    description: '检索知识库',
    createdAt: '2026-05-22 10:00:00'
  }
]

const models = [
  {
    id: 1,
    name: 'DeepSeek Chat',
    modelId: 'deepseek-chat',
    provider: 'DeepSeek',
    type: 'CHAT',
    status: 'ENABLED',
    createTime: '2026-05-22 09:00:00'
  }
]

const workflows = [
  {
    id: 'wf-1',
    name: '默认协作方案',
    type: 'router_specialists',
    specialist_agent_ids: ['agent-1'],
    finalizer_enabled: true
  }
]

async function authenticate(page) {
  await page.addInitScript(({ roles, tokenValue }) => {
    window.localStorage.setItem('orin_token', tokenValue)
    window.sessionStorage.setItem('orin_setup_completed', 'true')
    document.cookie = `orin_token=${tokenValue}; path=/`
    document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(roles))}; path=/`
    document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify({ userId: 1, username: 'admin' }))}; path=/`
  }, {
    roles: ['ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_USER'],
    tokenValue: token
  })
}

async function mockWave2Backends(page) {
  await page.route('**/api/v1/setup/status', async (route) => route.fulfill(json({
    completed: true,
    canInitialize: false
  })))
  await page.route('**/api/system/mcp/**', async (route) => route.fulfill(json([])))
  await page.route('**/api/playground/api/agents', async (route) => route.fulfill(json(agents.map((agent) => ({
    id: agent.id,
    name: agent.name,
    skill_ids: []
  })))))
  await page.route('**/api/playground/api/workflows', async (route) => route.fulfill(json(workflows)))
  await page.route('**/api/playground/api/workflow-templates', async (route) => route.fulfill(json([
    { id: 'tpl-1', name: '路由专家', type: 'router_specialists' }
  ])))
  await page.route('**/api/playground/api/skills', async (route) => route.fulfill(json(skills)))
  await page.route('**/api/playground/api/settings', async (route) => route.fulfill(json({})))
  await page.route('**/api/playground/api/workflows/**/graph', async (route) => route.fulfill(json({
    nodes: [],
    edges: []
  })))
  await page.route('**/api/playground/api/**', async (route) => route.fulfill(json([])))
  await page.route('**/api/v1/agents', async (route) => route.fulfill(json(agents)))
  await page.route('**/api/v1/agents/**', async (route) => route.fulfill(json({})))
  await page.route('**/api/v1/conversation-logs/grouped**', async (route) => route.fulfill(json({
    content: [{
      conversationId: 'conv-1',
      agentId: 'agent-1',
      model: 'deepseek-chat',
      query: '帮我总结',
      cumulativeTokens: 128,
      responseTime: 360,
      createdAt: '2026-05-22 10:30:00',
      success: true
    }],
    totalElements: 1
  })))
  await page.route('**/api/v1/conversation-logs/**', async (route) => route.fulfill(json([])))
  await page.route('**/api/v1/models**', async (route) => route.fulfill(json(models)))
  await page.route('**/api/v1/skills**', async (route) => route.fulfill(json(skills)))
  await page.route('**/api/v1/system/providers', async (route) => route.fulfill(json([
    { providerKey: 'DeepSeek', providerName: 'DeepSeek', icon: 'Cpu' }
  ])))
  await page.route('**/api/v1/collaboration/**', async (route) => route.fulfill(json([])))
  await page.route('**/api/v1/mcp/**', async (route) => route.fulfill(json([])))
  await page.route('**/api/v1/**', async (route) => route.fulfill(json([])))
}

test.describe('Wave 2 application domain browser smoke', () => {
  test('opens application pages without blank screens or runtime errors', async ({ page }) => {
    const runtimeErrors = []
    page.on('pageerror', (error) => runtimeErrors.push(`pageerror: ${error.message}`))
    page.on('console', (message) => {
      if (message.type() === 'error') runtimeErrors.push(`console error: ${message.text()}`)
    })

    await authenticate(page)
    await mockWave2Backends(page)

    const paths = [
      '/dashboard/applications/agents',
      '/dashboard/applications/conversations',
      '/dashboard/applications/models',
      '/dashboard/applications/skills',
      '/dashboard/applications/extensions',
      '/dashboard/applications/collaboration/dashboard',
      '/dashboard/applications/playground',
      '/dashboard/applications/playground/overview',
      '/dashboard/applications/workspace'
    ]

    for (const path of paths) {
      const startErrorCount = runtimeErrors.length
      await page.goto(path, { waitUntil: 'networkidle' })
      await expect(page.locator('body')).not.toHaveText(/^\\s*$/)
      await expect(page.locator('body')).not.toContainText('登录工作台')
      expect(runtimeErrors.slice(startErrorCount), path).toEqual([])
    }
  })
})
