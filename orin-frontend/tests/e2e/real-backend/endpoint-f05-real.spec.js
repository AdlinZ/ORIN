import { randomUUID } from 'node:crypto'

import { expect, test } from '@playwright/test'

const BASE_URL = process.env.ORIN_REAL_BACKEND || ''
const ADMIN_USERNAME = process.env.ORIN_E2E_ADMIN_USERNAME || 'admin'
const ADMIN_PASSWORD = process.env.ORIN_E2E_ADMIN_PASSWORD || ''

async function responseJson(response, description) {
  if (!response.ok()) {
    throw new Error(`${description} failed: HTTP ${response.status()} ${await response.text()}`)
  }
  return response.json()
}

async function createFrozenAgent(request, token, stamp) {
  const headers = { Authorization: `Bearer ${token}` }
  const name = `F05 browser fixture ${stamp}`
  const created = await responseJson(await request.post(`${BASE_URL}/api/v1/agents`, {
    headers,
    data: { name, description: 'Ephemeral fixture for Workspace publish E2E' },
  }), 'Agent create')
  const agentId = created.agentId || created.id || created.data?.agentId
  if (!agentId) throw new Error('Agent create response omitted agentId')

  await responseJson(await request.put(`${BASE_URL}/api/v1/agents/${agentId}/draft`, {
    headers,
    data: {
      name,
      description: 'Ephemeral fixture for Workspace publish E2E',
      mode: 'agent',
      modelName: 'deterministic',
      providerType: 'ORIN_DETERMINISTIC',
      systemPrompt: 'Return the deterministic test result.',
      temperature: 0,
      topP: 1,
      maxTokens: 64,
      pendingSecretRefs: [],
    },
  }), 'Agent draft save')

  await responseJson(await request.post(`${BASE_URL}/api/v1/agents/${agentId}/versions`, {
    headers: { ...headers, 'Idempotency-Key': `f05-browser-${randomUUID()}` },
  }), 'Agent freeze')

  const versions = await responseJson(await request.get(`${BASE_URL}/api/v1/agents/${agentId}/versions`, {
    headers,
  }), 'Agent versions read')
  const frozen = (Array.isArray(versions) ? versions : versions.data || [])
    .find((version) => version.status === 'FROZEN')
  const versionId = frozen?.agent_version_id || frozen?.id
  if (!versionId) throw new Error('Fixture Agent has no FROZEN version')
  return { agentId, versionId, name }
}

async function login(page) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill(ADMIN_USERNAME)
  await page.getByPlaceholder('请输入密码').fill(ADMIN_PASSWORD)
  const [response] = await Promise.all([
    page.waitForResponse((candidate) =>
      candidate.url().includes('/api/v1/auth/login') && candidate.request().method() === 'POST'
    ),
    page.getByRole('button', { name: '登录' }).click(),
  ])
  if (!response.ok()) throw new Error(`Browser login failed: HTTP ${response.status()}`)
  await expect.poll(() => new URL(page.url()).pathname, { timeout: 10_000 }).not.toBe('/login')
}

test.describe('F05 Workspace publish real-browser E2E', () => {
  test.setTimeout(120_000)

  test.beforeAll(() => {
    if (!BASE_URL) throw new Error('F05 browser E2E requires ORIN_REAL_BACKEND.')
    if (!ADMIN_PASSWORD) throw new Error('F05 browser E2E requires ORIN_E2E_ADMIN_PASSWORD.')
  })

  test('Workspace publishes a FROZEN AgentVersion and shows the one-time handoff', async ({ page, request }) => {
    const stamp = `${Date.now()}-${process.pid}`
    const loginBody = await responseJson(await request.post(`${BASE_URL}/api/v1/auth/login`, {
      data: { username: ADMIN_USERNAME, password: ADMIN_PASSWORD },
    }), 'Fixture login')
    const token = loginBody.token || loginBody.accessToken || loginBody.data?.token
    if (!token) throw new Error('Fixture login omitted JWT')
    const fixture = await createFrozenAgent(request, token, stamp)
    const endpointName = `F05 browser endpoint ${stamp}`
    let endpointId = ''

    try {
      await login(page)
      await page.goto('/workspace/endpoints')
      await page.getByRole('button', { name: '发布端点' }).click()
      const publishDialog = page.getByRole('dialog', { name: '发布端点' })
      await expect(publishDialog).toBeVisible()
      await publishDialog.getByPlaceholder('如：客服 Agent v1').fill(endpointName)

      const selects = publishDialog.locator('.el-select')
      await expect(selects).toHaveCount(1)
      await selects.click()
      const agentOption = page.getByText(fixture.name, { exact: false })
      await expect(agentOption).toBeVisible()
      await agentOption.click()

      await expect(selects).toHaveCount(2)
      const versionSelect = selects.nth(1)
      const versionInput = publishDialog.getByRole('combobox', { name: '版本' })
      const versionDropdownId = await versionInput.getAttribute('aria-controls')
      if (!versionDropdownId) throw new Error('Version selector omitted aria-controls')
      await versionSelect.click()
      const versionOptions = page.locator(`#${versionDropdownId} [role="option"]:not(.is-disabled)`)
      await expect(versionOptions).toHaveCount(1)
      await versionOptions.click()
      expect(await versionInput.getAttribute('value')).not.toBe('')

      const [publishResponse] = await Promise.all([
        page.waitForResponse((candidate) =>
          candidate.url().includes('/api/v1/endpoints') && candidate.request().method() === 'POST'
        ),
        publishDialog.getByRole('button', { name: '发布' }).click(),
      ])
      if (!publishResponse.ok()) throw new Error(`Workspace publish failed: HTTP ${publishResponse.status()}`)
      const published = await publishResponse.json()
      endpointId = published.id || published.data?.id || ''
      if (!endpointId) throw new Error('Workspace publish response omitted endpoint id')

      const handoff = page.getByRole('dialog', { name: '端点已发布' })
      await expect(handoff).toBeVisible()
      await expect(handoff.getByText('此 API Key 仅显示一次', { exact: false })).toBeVisible()
      await expect(handoff.getByText('REST 调用地址', { exact: true })).toBeVisible()
      await expect(handoff.getByText('MCP 客户端配置', { exact: true })).toBeVisible()
      await handoff.getByRole('button', { name: '我已安全保存' }).click()
      await expect(page.getByText(endpointName, { exact: true })).toBeVisible()
    } finally {
      if (endpointId) {
        await request.post(`${BASE_URL}/api/v1/endpoints/${endpointId}/deactivate`, {
          headers: { Authorization: `Bearer ${token}` },
        })
      }
    }
  })
})
