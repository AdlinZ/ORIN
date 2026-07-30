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
  },
  {
    id: 2,
    name: 'OpenAI Chat',
    modelId: 'gpt-test',
    provider: 'OpenAI',
    type: 'CHAT',
    status: 'ENABLED',
    createTime: '2026-05-22 09:10:00'
  },
  {
    id: 3,
    name: 'Local Embedding',
    modelId: 'local-embedding',
    provider: 'Ollama',
    type: 'EMBEDDING',
    status: 'DISABLED',
    createTime: '2026-05-22 09:20:00'
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

const mcpServices = [
  {
    id: 1,
    name: 'Git',
    type: 'STDIO',
    command: 'git-mcp /workspace',
    enabled: true,
    status: 'CONNECTED',
    lastConnected: '2026-05-22T10:00:00Z',
    healthScore: 100
  },
  {
    id: 2,
    name: 'Time',
    type: 'STDIO',
    command: 'time',
    enabled: true,
    status: 'ERROR',
    lastError: 'AI Engine MCP operation failed',
    healthScore: 0
  },
  {
    id: 3,
    name: 'Filesystem',
    type: 'STDIO',
    command: 'filesystem /workspace',
    enabled: false,
    status: 'DISCONNECTED',
    healthScore: 100
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
    roles: ['ROLE_ADMIN', 'ROLE_USER'],
    tokenValue: token
  })
}

async function mockWave2Backends(page) {
  // Keep this browser smoke independent from whichever local backend profile is running.
  // More specific mocks registered below take precedence over this fallback.
  await page.route('**/api/**', async (route) => route.fulfill(json([])))
  await page.route('**/api/v1/**', async (route) => route.fulfill(json([])))
  await page.route('**/api/v1/setup/status', async (route) => route.fulfill(json({
    completed: true,
    canInitialize: false
  })))
  await page.route('**/api/system/mcp/**', async (route) => route.fulfill(json([])))
  await page.route('**/api/system/mcp/services', async (route) => route.fulfill(json(mcpServices)))
  await page.route('**/api/system/mcp/tools', async (route) => route.fulfill(json([
    {
      id: 'filesystem',
      key: 'filesystem',
      name: 'Filesystem',
      description: '本地文件系统读写与检索',
      type: 'STDIO',
      command: 'filesystem /app',
      installed: true,
      serviceId: 3,
      enabled: false,
      status: 'DISCONNECTED'
    }
  ])))
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
  await page.route('**/api/v1/gateway/secrets/provider-credentials', async (route) => route.fulfill(json([
    { secretId: 'provider-deepseek', provider: 'DeepSeek', status: 'ACTIVE', enabled: true }
  ])))
  await page.route('**/api/v1/skills**', async (route) => route.fulfill(json(skills)))
  await page.route('**/api/v1/system/providers', async (route) => route.fulfill(json([
    { providerKey: 'DeepSeek', providerName: 'DeepSeek', icon: 'Cpu' }
  ])))
  await page.route('**/api/v1/collaboration/**', async (route) => route.fulfill(json([])))
  await page.route('**/api/v1/mcp/**', async (route) => route.fulfill(json([])))
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
      '/dashboard/applications/mcp-tools',
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

    await page.goto('/dashboard/applications/models', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: '模型', exact: true })).toBeVisible()
    const modelSummary = page.getByRole('region', { name: '模型可用性概览' })
    await expect(modelSummary.getByText('可用于 Agent', { exact: true })).toBeVisible()
    await expect(modelSummary.getByText('待配置凭据', { exact: true })).toBeVisible()
    await expect(modelSummary.getByText('已停用', { exact: true })).toBeVisible()
    await expect(page.getByRole('row').filter({ hasText: 'DeepSeek Chat' })).toContainText('可用于 Agent')
    await expect(page.getByRole('row').filter({ hasText: 'OpenAI Chat' })).toContainText('待配置凭据')
    await expect(page.getByRole('row').filter({ hasText: 'Local Embedding' })).toContainText('已停用')

    await page.getByRole('button', { name: '管理供应商凭据' }).click()
    await expect(page).toHaveURL(/workspace=access&credentialTab=provider/)
    await expect(page.getByRole('tab', { name: '外部供应商密钥 (Credentials)' })).toHaveAttribute('aria-selected', 'true')

    await page.goto('/dashboard/applications/mcp-tools', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: 'MCP 工具', exact: true })).toBeVisible()
    await expect(page.getByRole('row').filter({ hasText: 'Git' })).toContainText('可用于 Agent')
    await expect(page.getByRole('row').filter({ hasText: 'Time' })).toContainText('连接故障')
    await expect(page.getByRole('row').filter({ hasText: 'Filesystem' })).toContainText('已停用')
    await expect(page.getByText('git-mcp /workspace', { exact: true })).toHaveCount(0)
    await expect(page.getByText('filesystem /workspace', { exact: true })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '工具模板' })).toHaveCount(0)

    await page.getByRole('button', { name: '进入高级管理' }).click()
    await expect(page).toHaveURL(/tab=mcp/)
    await expect(page.getByRole('heading', { name: 'MCP服务', exact: true })).toBeVisible()
    await page.getByRole('tab', { name: '工具市场', exact: true }).click()
    await expect(page.getByText('MCP 工具市场', { exact: true })).toBeVisible()
  })
})
