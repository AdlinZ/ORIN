import { expect, test } from '@playwright/test'

const token = `header.${Buffer.from(JSON.stringify({
  sub: 'admin',
  exp: Math.floor(Date.now() / 1000) + 3600
})).toString('base64url')}.sig`

const json = (body) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
})

test('captures representative dashboard surfaces', async ({ page }) => {
  test.setTimeout(90_000)

  await page.addInitScript(({ tokenValue }) => {
    const roles = ['ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_USER']
    window.localStorage.setItem('orin_token', tokenValue)
    window.sessionStorage.setItem('orin_setup_completed', 'true')
    document.cookie = `orin_token=${tokenValue}; path=/`
    document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(roles))}; path=/`
    document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify({
      userId: 1,
      username: 'admin',
      nickname: '管理员'
    }))}; path=/`
  }, { tokenValue: token })

  await page.route('**/*', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/v1/setup/status') {
      return route.fulfill(json({ completed: true, canInitialize: false }))
    }
    if (url.pathname === '/api/v1/dashboard/summary') {
      return route.fulfill(json({}))
    }
    if (url.pathname === '/api/v1/conversation-logs/grouped') {
      return route.fulfill(json({ content: [], totalElements: 0 }))
    }
    if (url.pathname === '/api/v1/roles') {
      return route.fulfill(json({
        data: [
          {
            roleId: 1,
            roleCode: 'ROLE_SUPER_ADMIN',
            roleName: '超级管理员',
            description: '拥有全局控制权限，可管理组织与平台全部能力',
            createTime: '2026-07-29T20:36:00'
          },
          {
            roleId: 2,
            roleCode: 'ROLE_ADMIN',
            roleName: '系统管理员',
            description: '拥有系统所有权限，可管理用户、配置、API等',
            createTime: '2026-07-29T20:36:00'
          }
        ],
        total: 2
      }))
    }
    if (url.pathname.startsWith('/api/')) {
      return route.fulfill(json([]))
    }
    return route.continue()
  })

  const previews = [
    ['agents', '/dashboard/applications/agents'],
    ['tasks', '/dashboard/runtime/tasks'],
    ['roles', '/dashboard/control/roles']
  ]

  for (const [name, path] of previews) {
    await page.goto(path, { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.main-layout.dashboard-redesign')).toBeVisible()
    await expect(page.locator('.content-area')).not.toHaveClass(/is-reference-page/)
    await expect(page.locator('.page-header-container')).toBeVisible()

    const canvasColor = await page.locator('.content-area').evaluate(
      (element) => getComputedStyle(element).backgroundColor
    )
    expect(canvasColor).toBe('rgb(244, 247, 246)')

    if (name === 'roles') {
      await expect(page.locator('.premium-table .el-table__row')).toHaveCount(2)
      const layout = await page.evaluate(() => ({
        viewportWidth: document.documentElement.clientWidth,
        documentWidth: document.documentElement.scrollWidth,
        cardRight: document.querySelector('.governance-card')?.getBoundingClientRect().right || 0
      }))
      expect(layout.documentWidth).toBeLessThanOrEqual(layout.viewportWidth)
      expect(layout.cardRight).toBeLessThanOrEqual(layout.viewportWidth)
    }

    if (process.env.ORIN_CAPTURE_DESIGN_PREVIEWS === '1') {
      await page.screenshot({
        path: `/tmp/orin-${name}-preview.png`,
        fullPage: false
      })
    }
  }

  await page.goto('/dashboard/applications/workflows/visual', { waitUntil: 'domcontentloaded' })
  await expect(page.locator('.content-area')).toHaveClass(/is-reference-page/)
  const referencePrimary = await page.locator('.main-layout').evaluate(
    (element) => getComputedStyle(element).getPropertyValue('--orin-primary').trim()
  )
  expect(referencePrimary).toBe('#0d9488')
})
