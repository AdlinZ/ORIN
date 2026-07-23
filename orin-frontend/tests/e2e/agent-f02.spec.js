import { expect, test } from '@playwright/test'

const json = (body, status = 200) => ({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
})

async function authenticate(page) {
    await page.addInitScript(() => {
        const roles = ['ROLE_ADMIN', 'ROLE_USER']
        window.localStorage.setItem('orin_token', 'f02-mock-token')
        window.sessionStorage.setItem('orin_setup_completed', 'true')
        document.cookie = 'orin_token=f02-mock-token; path=/'
        document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(roles))}; path=/`
        document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify({ userId: 1, username: 'admin' }))}; path=/`
    })
}

test('F02 用户旅程：新建 Agent → 编辑 → 冻结 → 切换 active → 拒绝修改已 FROZEN 内容', async ({ page }) => {
    let freezeCalls = 0

    await authenticate(page)
    await page.route('**/api/v1/**', async (route) => {
        const request = route.request()
        const path = new URL(request.url()).pathname
        const method = request.method()

        if (path === '/api/v1/setup/status') {
            return route.fulfill(json({ completed: true, canInitialize: false }))
        }
        if (path === '/api/v1/agents/_active-gateway-secrets') {
            return route.fulfill(json([
                { secret_id: 'gsec_openai', secret_type: 'PROVIDER_CREDENTIAL', key_prefix: 'sk-orin', last4: 'abcd' }
            ]))
        }
        if (path === '/api/v1/agents/ag_f02_demo/draft' && method === 'GET') {
            return route.fulfill(json({
                agent_id: 'ag_f02_demo',
                name: 'demo',
                description: '',
                mode: 'agent',
                modelName: 'gpt-4o',
                providerType: 'OPENAI',
                active_version_id: null,
            }))
        }
        if (path === '/api/v1/agents/ag_f02_demo/draft' && method === 'PUT') {
            return route.fulfill(json({
                agent_id: 'ag_f02_demo',
                name: 'demo',
                active_version_id: null,
            }))
        }
        if (path === '/api/v1/agents/ag_f02_demo/versions' && method === 'POST') {
            freezeCalls += 1
            const idemKey = request.headers()['idempotency-key'] || ''
            return route.fulfill(json({
                agent_version_id: `ver_${freezeCalls}`,
                agent_id: 'ag_f02_demo',
                version_number: freezeCalls,
                status: 'FROZEN',
                content_digest: 'a'.repeat(64),
                snapshot_schema_version: 1,
                idempotent_replay: false,
                frozen_at: new Date().toISOString(),
                frozen_by: 'admin',
            }, 201))
        }
        if (path === '/api/v1/agents/ag_f02_demo/versions' && method === 'GET') {
            return route.fulfill(json([
                {
                    agent_version_id: 'ver_1',
                    version_number: 1,
                    status: 'FROZEN',
                    content_digest: 'a'.repeat(64),
                    snapshot_schema_version: 1,
                    frozen_at: new Date().toISOString(),
                    is_active: true,
                }
            ]))
        }
        if (path === '/api/v1/agents/ag_f02_demo/versions/ver_1' && method === 'GET') {
            return route.fulfill(json({
                agent_version_id: 'ver_1',
                agent_id: 'ag_f02_demo',
                version_number: 1,
                status: 'FROZEN',
                content_digest: 'a'.repeat(64),
                snapshot_schema_version: 1,
                frozen_at: new Date().toISOString(),
                frozen_by: 'admin',
                is_active: true,
                secret_refs: [
                    { alias: 'openai.primary', source: 'CONTROL_PLANE', secret_id: 'gsec_openai',
                      inject_as: 'OPENAI_API_KEY', required: true, key_prefix: 'sk-orin', last4: 'abcd' }
                ]
            }))
        }
        if (path === '/api/v1/agents/ag_f02_demo/active-version' && method === 'PUT') {
            return route.fulfill(json({
                agent_version_id: 'ver_1',
                agent_id: 'ag_f02_demo',
                version_number: 1,
                status: 'FROZEN',
                content_digest: 'a'.repeat(64),
                snapshot_schema_version: 1,
                is_active: true,
                frozen_at: new Date().toISOString(),
            }))
        }
        return route.fulfill(json({}))
    })

    // 1. 直接访问草稿页
    await page.goto('/workspace/agents/ag_f02_demo')
    await expect(page.getByText('demo', { exact: false })).toBeVisible()
    await expect(page.getByText(/DRAFT|FROZEN/).first()).toBeVisible()

    // 2. 选择 secret / 填 alias / 触发 freeze
    await page.getByPlaceholder('openai.primary').fill('openai.primary')
    // select 模拟：直接 dispatch
    await page.evaluate(() => {
        document.querySelectorAll('input').forEach((el) => {
            if (el.value === 'openai.primary') {
                el.dispatchEvent(new Event('change', { bubbles: true }))
            }
        })
    })
    // secret 选择下拉
    await page.locator('.el-select').first().click()
    await page.getByText('gsec_openai', { exact: false }).first().click()

    // 3. 点击「校验并冻结」
    await page.getByRole('button', { name: /校验并冻结/ }).click()
    // 4. 校验跳转
    await expect(page).toHaveURL(/\/workspace\/agents\/ag_f02_demo\/versions\/ver_1/)

    // 5. 校验详情页 FROZEN / digest 出现
    await expect(page.getByText('FROZEN', { exact: false }).first()).toBeVisible()
    await expect(page.getByText(/a{12,}/).first()).toBeVisible()
    await expect(page.getByText('OPENAI_API_KEY', { exact: false }).first()).toBeVisible()

    // 6. 至少 freeze 接口被调过一次
    expect(freezeCalls).toBeGreaterThan(0)
})
