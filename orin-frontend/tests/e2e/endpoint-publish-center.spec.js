import { expect, test } from '@playwright/test'

const json = (body) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body),
})

async function authenticate(page) {
  await page.addInitScript(() => {
    const roles = ['ROLE_ADMIN', 'ROLE_USER']
    window.localStorage.setItem('orin_token', 'publish-center-token')
    window.sessionStorage.setItem('orin_setup_completed', 'true')
    document.cookie = 'orin_token=publish-center-token; path=/'
    document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(roles))}; path=/`
    document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify({ userId: 1, username: 'admin' }))}; path=/`
  })
}

test('发布中心只暴露交付主线，并可重新查看协议对应的调用方式', async ({ page }) => {
  await authenticate(page)
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname

    if (path === '/api/v1/setup/status') {
      return route.fulfill(json({ completed: true, canInitialize: false }))
    }
    if (path === '/api/v1/endpoints') {
      return route.fulfill(json({
        content: [
          {
            id: 'ep-rest',
            name: '客服问答服务',
            description: '供官网客服应用调用',
            endpointType: 'REST_API',
            status: 'ACTIVE',
            agentId: 'agent-1',
            agentVersionId: 'version-1',
            externalUrl: '/v1/endpoints/ep-rest/run',
            createdAt: '2026-07-28T10:00:00Z',
          },
          {
            id: 'ep-mcp',
            name: '资料检索工具',
            description: '供 AI 客户端调用',
            endpointType: 'MCP_SERVER',
            status: 'INACTIVE',
            agentId: 'agent-2',
            agentVersionId: 'version-2',
            externalUrl: '/v1/endpoints/ep-mcp/run',
            createdAt: '2026-07-28T09:00:00Z',
          },
        ],
        totalElements: 2,
      }))
    }
    if (path === '/api/v1/agents') {
      return route.fulfill(json([
        { agentId: 'agent-1', name: '客服 Agent', activeVersionStatus: 'FROZEN', activeVersionId: 'version-1', activeVersionNumber: 1 },
        { agentId: 'agent-2', name: '检索 Agent', activeVersionStatus: 'FROZEN', activeVersionId: 'version-2', activeVersionNumber: 2 },
      ]))
    }
    return route.fulfill(json({}))
  })

  await page.goto('/workspace/endpoints', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { name: '发布中心' })).toBeVisible()
  await expect(page.getByRole('menuitem', { name: '访问密钥与网关' })).toHaveCount(0)
  await expect(page.getByRole('region', { name: '发布状态' }).getByRole('button', { name: /可调用/ })).toContainText('1')

  const restRow = page.getByRole('row').filter({ hasText: '客服问答服务' })
  await expect(restRow).toContainText('供应用和脚本通过 HTTP 调用')
  await restRow.getByRole('button', { name: '查看调用方式' }).click()

  let guide = page.getByRole('dialog', { name: '使用服务' })
  await expect(guide.getByText('curl 示例', { exact: true })).toBeVisible()
  await expect(guide.getByText(/YOUR_API_KEY/)).toBeVisible()
  await expect(guide.getByText('MCP 客户端配置', { exact: true })).toHaveCount(0)
  await guide.getByRole('button', { name: '完成' }).click()

  const mcpRow = page.getByRole('row').filter({ hasText: '资料检索工具' })
  await expect(mcpRow).toContainText('外部调用已停止')
  await mcpRow.getByRole('button', { name: '查看调用方式' }).click()

  guide = page.getByRole('dialog', { name: '使用服务' })
  await expect(guide.getByText('服务当前已下线', { exact: false })).toBeVisible()
  await expect(guide.getByText('MCP 客户端配置', { exact: true })).toBeVisible()
  await expect(guide.getByText('curl 示例', { exact: true })).toHaveCount(0)
})
