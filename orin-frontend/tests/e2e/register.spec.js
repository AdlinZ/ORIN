import { expect, test } from '@playwright/test'

const json = (body, init = {}) => ({
  status: init.status || 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
})

async function mockRegisterBackend(page) {
  const requests = []

  await page.route('**/*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    requests.push({ method: request.method(), path })

    if (request.method() === 'GET' && path === '/api/v1/setup/status') {
      return route.fulfill(json({ completed: true, canInitialize: false }))
    }

    if (request.method() === 'GET' && path === '/api/v1/auth/registration-status') {
      return route.fulfill(json({ enabled: true }))
    }

    if (request.method() === 'POST' && path === '/api/v1/auth/register') {
      const body = request.postDataJSON()
      return route.fulfill(json({
        token: 'jwt-role-user',
        user: {
          userId: 42,
          username: body.username,
          status: 'ENABLED',
          role: 'ROLE_USER'
        },
        roles: ['ROLE_USER']
      }))
    }

    if (request.method() === 'GET' && path === '/api/v1/agents') {
      return route.fulfill(json([]))
    }

    if (request.method() === 'GET' && path === '/api/v1/agents/chat/sessions') {
      return route.fulfill(json([]))
    }

    if (path.startsWith('/api/')) {
      return route.fulfill(json({ success: true }))
    }

    return route.continue()
  })

  return { requests }
}

test.describe('Personal registration browser acceptance', () => {
  test('creates a ROLE_USER account and enters /chat', async ({ page }) => {
    const backend = await mockRegisterBackend(page)

    await page.goto('/register')
    await expect(page.getByRole('heading', { name: '注册账号' })).toBeVisible()

    await page.getByPlaceholder('3-32 位字母、数字或 _.-').fill('new-user')
    await page.getByPlaceholder('可选').fill('new-user@example.com')
    await page.getByPlaceholder('至少 8 位').fill('StrongPass123')
    await page.getByPlaceholder('再次输入密码').fill('StrongPass123')
    await page.getByRole('button', { name: '注册并进入' }).click()

    await expect(page).toHaveURL(/\/chat$/)
    await expect.poll(async () => page.evaluate(() => window.localStorage.getItem('orin_token'))).toBe('jwt-role-user')
    expect(backend.requests.some((request) => request.method === 'POST' && request.path === '/api/v1/auth/register')).toBe(true)
  })
})
