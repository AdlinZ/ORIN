/**
 * 工作台 / 管理台 / Chat 三产品面边界 E2E（Phase 7+）
 *
 * 覆盖：
 * - ROLE_USER 访问 /admin/* 被路由守卫拦截到 /chat
 * - ROLE_USER 访问 /workspace/agents 成功，仅看到 ORIN 工作台菜单
 * - ROLE_ADMIN 访问 /admin/runtime/overview 成功，仅看到 ORIN 管理台菜单
 * - ROLE_ADMIN 访问 /workspace/workflows 也成功（不出现「跨产品切换按钮」）
 * - ROLE_USER 在 /chat 头像菜单不出现「ORIN 工作台」跨产品入口
 *
 * 通过 page.route mock 后端；不需要真实 Java 服务。
 */

import { expect, test } from '@playwright/test'

const json = (body, init = {}) => ({
    status: init.status || 200,
    contentType: 'application/json',
    body: JSON.stringify(body),
    ...(init.headers ? { headers: init.headers } : {}),
})

const tokenFor = (username) => `header.${Buffer.from(JSON.stringify({
    sub: username,
    exp: Math.floor(Date.now() / 1000) + 3600,
})).toString('base64url')}.sig`

const authenticate = async (page, { username, roles, menuMode = 'topbar' }) => {
    await page.addInitScript(({ roles: r, tokenValue, user, mode }) => {
        window.localStorage.setItem('orin_token', tokenValue)
        window.localStorage.setItem('orin_menu_mode', mode)
        window.localStorage.setItem('orin_menu_collapse', 'false')
        window.sessionStorage.setItem('orin_setup_completed', 'true')
        document.cookie = `orin_token=${tokenValue}; path=/`
        document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(r))}; path=/`
        document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify(user))}; path=/`
    }, { roles, tokenValue: tokenFor(username), user: { userId: 1, username }, mode: menuMode })
}

const mockCommon = async (page) => {
    await page.route('https://fonts.googleapis.com/**', (r) => r.fulfill({ status: 200, contentType: 'text/css', body: '' }))
    await page.route('https://fonts.gstatic.com/**', (r) => r.fulfill({ status: 200, contentType: 'font/woff2', body: '' }))
    await page.route('**/api/**', (route) => {
        const path = new URL(route.request().url()).pathname
        if (path === '/api/v1/setup/status') {
            return route.fulfill(json({ completed: true, canInitialize: false }))
        }
        if (path.startsWith('/api/v1/notifications')) {
            return route.fulfill(json({ list: [], total: 0 }))
        }
        return route.fulfill(json([]))
    })
}

test.describe('product surface boundaries', () => {
    test('ROLE_USER is redirected away from /admin/users to /chat', async ({ page }) => {
        await authenticate(page, { username: 'alice', roles: ['ROLE_USER'] })
        await mockCommon(page)

        await page.goto('/admin/users')
        await expect(page).toHaveURL(/\/chat/, { timeout: 8000 })
    })

    test('ROLE_USER is redirected away from /admin/runtime to /chat', async ({ page }) => {
        await authenticate(page, { username: 'alice', roles: ['ROLE_USER'] })
        await mockCommon(page)

        await page.goto('/admin/runtime/overview')
        await expect(page).toHaveURL(/\/chat/, { timeout: 8000 })
    })

    test('ROLE_USER can enter /workspace/agents, sees ORIN 工作台 brand', async ({ page }) => {
        await authenticate(page, { username: 'alice', roles: ['ROLE_USER'] })
        await mockCommon(page)

        await page.goto('/workspace/agents')
        // 等待导航稳定
        await expect(page).toHaveURL(/\/workspace\/agents/, { timeout: 8000 })

        await expect(page.locator('.navbar-surface-label').first()).toContainText('ORIN 工作台')

        // 不应出现 .surface-switcher / .product-switcher 这两个产品切换器
        expect(await page.locator('.surface-switcher').count()).toBe(0)
        expect(await page.locator('.product-switcher').count()).toBe(0)
    })

    test('ROLE_ADMIN can enter /admin/runtime/overview, sees ORIN 管理台 brand', async ({ page }) => {
        await authenticate(page, { username: 'admin', roles: ['ROLE_ADMIN'] })
        await mockCommon(page)

        await page.goto('/admin/runtime/overview')
        await expect(page).toHaveURL(/\/admin\/runtime\/overview/, { timeout: 8000 })

        await expect(page.locator('.navbar-surface-label').first()).toContainText('ORIN 管理台')

        expect(await page.locator('.surface-switcher').count()).toBe(0)
        expect(await page.locator('.product-switcher').count()).toBe(0)
    })

    test('ROLE_ADMIN does not have cross-product switcher in WorkspaceLayout', async ({ page }) => {
        await authenticate(page, { username: 'admin', roles: ['ROLE_ADMIN'] })
        await mockCommon(page)

        await page.goto('/workspace/workflows')
        await expect(page).toHaveURL(/\/workspace\/workflows/, { timeout: 8000 })

        await expect(page.locator('.navbar-surface-label').first()).toContainText('ORIN 工作台')
        expect(await page.locator('.surface-switcher').count()).toBe(0)
        expect(await page.locator('.product-switcher').count()).toBe(0)

        await page.locator('.navbar-logo').click()
        await expect(page).toHaveURL((url) => url.pathname === '/workspace/developer')
    })

    test('admin logo stays inside the admin product', async ({ page }) => {
        await authenticate(page, { username: 'admin', roles: ['ROLE_ADMIN'] })
        await mockCommon(page)

        await page.goto('/admin/runtime/tasks')
        await page.locator('.navbar-logo').click()
        await expect(page).toHaveURL((url) => url.pathname === '/admin/admin-overview')
    })

    test('sidebar mode keeps a working collapse control in the workspace layout', async ({ page }) => {
        await authenticate(page, { username: 'alice', roles: ['ROLE_USER'], menuMode: 'sidebar' })
        await mockCommon(page)

        await page.goto('/workspace/agents')
        const sidebar = page.locator('.sidebar-container')
        await expect(sidebar).not.toHaveClass(/collapsed/)
        await page.getByRole('button', { name: '折叠侧边栏' }).click()
        await expect(sidebar).toHaveClass(/collapsed/)
        await expect(page.getByRole('button', { name: '展开侧边栏' })).toBeVisible()
    })

    test('workspace home sends regular users to self-service destinations', async ({ page }) => {
        await authenticate(page, { username: 'alice', roles: ['ROLE_USER'] })
        await mockCommon(page)
        await page.route('**/api/v1/developer/summary', (route) => route.fulfill(json({
            myAgents: { agents: [], total: 0 },
            myApiKeys: { keys: [], total: 0, activeKeys: 0 },
            recentTraces: [],
            quickLinks: [{ title: 'API 文档', path: '/dashboard/control/unified-gateway' }],
        })))

        await page.goto('/workspace/developer')
        await page.getByRole('button', { name: '管理', exact: true }).click()
        await expect(page).toHaveURL((url) => url.pathname === '/platform')

        await page.goto('/workspace/developer')
        await page.getByRole('button', { name: 'API 文档', exact: true }).click()
        await expect(page).toHaveURL((url) => url.pathname === '/platform/docs')
    })
})
