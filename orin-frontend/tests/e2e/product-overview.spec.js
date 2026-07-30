import { expect, test } from '@playwright/test'

const json = (body) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body),
})

async function authenticate(page) {
  await page.addInitScript(() => {
    const roles = ['ROLE_ADMIN', 'ROLE_USER']
    window.localStorage.setItem('orin_token', 'overview-token')
    window.sessionStorage.setItem('orin_setup_completed', 'true')
    document.cookie = 'orin_token=overview-token; path=/'
    document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(roles))}; path=/`
    document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify({ userId: 1, username: 'admin' }))}; path=/`
  })
}

test('产品概览只给一个当前行动，同时保留最近 Run 和交付证据', async ({ page }) => {
  await authenticate(page)
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/setup/status') {
      return route.fulfill(json({ completed: true, canInitialize: false }))
    }
    if (path === '/api/v1/agents') {
      return route.fulfill(json([
        { agentId: 'agent-1', name: '任务 Agent', activeVersionStatus: 'FROZEN', activeVersionId: 'version-1', activeVersionNumber: 1 },
      ]))
    }
    if (path === '/api/v1/runners') {
      return route.fulfill(json({ content: [{ id: 'runner-1', name: '离线节点', status: 'OFFLINE' }] }))
    }
    if (path === '/api/v1/runs') {
      return route.fulfill(json({
        content: [{
          id: 'run-1',
          agentId: 'agent-1',
          status: 'COMPLETED',
          input: '整理本周风险',
          output: '三个风险与对应负责人',
          createdAt: '2026-07-28T10:00:00Z',
          completedAt: '2026-07-28T10:01:00Z',
        }],
      }))
    }
    if (path === '/api/v1/endpoints') {
      return route.fulfill(json({
        content: [{
          id: 'endpoint-1',
          agentId: 'agent-1',
          agentVersionId: 'version-1',
          name: '风险整理服务',
          description: '供项目管理应用调用',
          endpointType: 'REST_API',
          status: 'INACTIVE',
          externalUrl: '/v1/endpoints/endpoint-1/run',
          createdAt: '2026-07-28T09:00:00Z',
        }],
      }))
    }
    return route.fulfill(json({}))
  })

  await page.goto('/workspace/overview', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { name: '今天最应该做的一件事' })).toBeVisible()

  const decision = page.getByRole('region', { name: '当前唯一下一步' })
  await expect(decision).toContainText('接入在线 Runner')
  await expect(decision).toContainText('1 个 Agent 版本已可运行')
  const onboarding = page.getByRole('region', { name: '首次交付向导' })
  await expect(onboarding).toContainText('跟着 5 步发布第一个 Agent')
  await expect(onboarding).toContainText('3 / 5')
  await expect(onboarding.getByRole('button', { name: /接入执行节点/ })).toContainText('当前步骤')
  await expect(page.getByRole('region', { name: '交付就绪证据' })).toContainText('现有服务已下线')
  await expect(page.getByText('三个风险与对应负责人', { exact: true })).toBeVisible()
  await expect(page.getByText('风险整理服务', { exact: true })).toBeVisible()
  await expect(page.locator('.summary-grid')).toHaveCount(0)

  await page.getByRole('button', { name: '处理已下线服务' }).click()
  await expect(page).toHaveURL(/\/workspace\/endpoints\?guide=endpoint-1/)
  await expect(page.getByRole('dialog', { name: '使用服务' })).toBeVisible()
})

test('Agent 列表只保留交付状态和下一步动作', async ({ page }) => {
  await authenticate(page)
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/setup/status') {
      return route.fulfill(json({ completed: true, canInitialize: false }))
    }
    if (path === '/api/v1/agents') {
      return route.fulfill(json([
        {
          agentId: 'agent-1',
          name: '任务 Agent',
          description: '整理项目风险',
          modelName: 'gpt-4o-mini',
          providerType: 'OpenAI',
          activeVersionStatus: 'FROZEN',
          activeVersionNumber: 7,
        },
      ]))
    }
    return route.fulfill(json({}))
  })

  await page.goto('/workspace/agents', { waitUntil: 'domcontentloaded' })

  const agentRow = page.getByRole('row').filter({ hasText: '任务 Agent' })
  await expect(agentRow).toContainText('gpt-4o-mini')
  await expect(agentRow).toContainText('可运行')
  await expect(agentRow.getByRole('button', { name: '运行', exact: true })).toBeVisible()
  await expect(agentRow.getByRole('button', { name: '发布', exact: true })).toBeVisible()
  await expect(agentRow.getByRole('button', { name: '查看版本', exact: true })).toHaveCount(0)
  await expect(page.getByText('OpenAI', { exact: true })).toHaveCount(0)
  await expect(page.getByText('v7', { exact: true })).toHaveCount(0)

  await agentRow.getByRole('cell').first().click()
  await expect(page).toHaveURL(/\/workspace\/agents\/agent-1\/versions/)
})
