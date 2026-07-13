/**
 * 旧 /dashboard/* 路径重定向到 canonical /workspace/* 或 /admin/* 验证
 */

import { expect, test } from '@playwright/test'

const json = (body, init = {}) => ({
    status: init.status || 200,
    contentType: 'application/json',
    body: JSON.stringify(body),
})

const tokenFor = (username) => `header.${Buffer.from(JSON.stringify({
    sub: username,
    exp: Math.floor(Date.now() / 1000) + 3600,
})).toString('base64url')}.sig`

const authenticate = async (page, { username, roles }) => {
    await page.addInitScript(({ roles: r, tokenValue, user }) => {
        window.localStorage.setItem('orin_token', tokenValue)
        window.localStorage.setItem('orin_menu_mode', 'topbar')
        window.sessionStorage.setItem('orin_setup_completed', 'true')
        document.cookie = `orin_token=${tokenValue}; path=/`
        document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(r))}; path=/`
        document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify(user))}; path=/`
    }, { roles, tokenValue: tokenFor(username), user: { userId: 1, username } })
}

const mockCommon = async (page) => {
    await page.route('https://fonts.googleapis.com/**', (r) => r.fulfill({ status: 200, contentType: 'text/css', body: '' }))
    await page.route('https://fonts.gstatic.com/**', (r) => r.fulfill({ status: 200, contentType: 'font/woff2', body: '' }))
    await page.route('**/api/**', (route) => {
        const path = new URL(route.request().url()).pathname
        if (path === '/api/v1/setup/status') {
            return route.fulfill(json({ completed: true, canInitialize: false }))
        }
        return route.fulfill(json([]))
    })
}

const REDIRECTS = [
    // [oldUrl, expectedPathname, role]
    ['/dashboard/applications/agents', '/workspace/agents', ['ROLE_USER']],
    ['/dashboard/applications/workspace', '/workspace/workspace', ['ROLE_USER']],
    ['/dashboard/applications/conversations', '/workspace/conversations', ['ROLE_USER']],
    ['/dashboard/applications/workflows', '/workspace/workflows', ['ROLE_USER']],
    ['/dashboard/applications/extensions', '/workspace/extensions', ['ROLE_USER']],
    ['/dashboard/applications/collaboration/workflows', '/workspace/collaboration/workflows', ['ROLE_USER']],
    ['/dashboard/resources/center', '/workspace/knowledge/center', ['ROLE_USER']],
    ['/dashboard/resources/assets', '/workspace/knowledge/assets', ['ROLE_USER']],
    ['/dashboard/resources/retrieval', '/workspace/knowledge/retrieval', ['ROLE_USER']],
    ['/dashboard/control/users', '/admin/users', ['ROLE_ADMIN']],
    ['/dashboard/control/admin-overview', '/admin/admin-overview', ['ROLE_ADMIN']],
    ['/dashboard/control/gateway', '/admin/gateway', ['ROLE_ADMIN']],
    ['/dashboard/control/audit-logs', '/admin/audit-logs', ['ROLE_ADMIN']],
    ['/dashboard/runtime/overview', '/admin/runtime/overview', ['ROLE_ADMIN']],
    ['/dashboard/runtime/server', '/admin/runtime/server', ['ROLE_ADMIN']],
    ['/dashboard/runtime/tasks', '/admin/runtime/tasks', ['ROLE_ADMIN']],
    ['/dashboard/runtime/alerts', '/admin/runtime/alerts', ['ROLE_ADMIN']],
    ['/dashboard/runtime/traces', '/admin/runtime/traces', ['ROLE_ADMIN']],
    ['/dashboard/agents/list', '/workspace/agents', ['ROLE_USER']],
    ['/dashboard/system/api-keys', '/admin/gateway', ['ROLE_ADMIN']],
]

test.describe('legacy /dashboard redirects land on canonical product surfaces', () => {
    for (const [oldUrl, expectedPathname, roles] of REDIRECTS) {
        const username = roles.includes('ROLE_ADMIN') ? 'admin' : 'alice'
        test(`${oldUrl} → ${expectedPathname}`, async ({ page }) => {
            await authenticate(page, { username, roles })
            await mockCommon(page)

            await page.goto(oldUrl)
            await expect(page).toHaveURL((url) => url.pathname === expectedPathname, { timeout: 8000 })
        })
    }
})

test.describe('ROLE_USER legacy /dashboard/control and /dashboard/runtime are blocked', () => {
    test('ROLE_USER on /dashboard/control/users is redirected to /chat', async ({ page }) => {
        await authenticate(page, { username: 'alice', roles: ['ROLE_USER'] })
        await mockCommon(page)

        await page.goto('/dashboard/control/users')
        await expect(page).toHaveURL(/\/chat/, { timeout: 8000 })
    })

    test('ROLE_USER on /dashboard/runtime/overview is redirected to /chat', async ({ page }) => {
        await authenticate(page, { username: 'alice', roles: ['ROLE_USER'] })
        await mockCommon(page)

        await page.goto('/dashboard/runtime/overview')
        await expect(page).toHaveURL(/\/chat/, { timeout: 8000 })
    })
})
