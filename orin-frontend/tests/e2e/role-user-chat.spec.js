import { expect, test } from '@playwright/test'

/**
 * ROLE_USER /chat 浏览器 E2E
 *
 * 覆盖：
 * - 登录后默认进入 /chat（route 守卫在登录流之后不拦截）
 * - ROLE_USER 顶栏只有「对话 → ORIN Chat」
 * - 看到智能体列表、选中、新建会话、发消息（含 mock 流式 + 错误 traceId）
 * - 刷新后能恢复最近会话
 * - 工作台资源域可访问，管理台治理域仍会被重定向到 /chat
 * - 个人中心使用独立的 /chat/profile 页面
 *
 * 全部用 page.route mock 后端，无需真实 Java 服务。
 */

const json = (body, init = {}) => ({
  status: init.status || 200,
  contentType: 'application/json',
  body: JSON.stringify(body),
  ...(init.headers ? { headers: init.headers } : {})
})

const SSE_LINE_BREAK = '\n\n'

const now = '2026-07-06T10:00:00Z'

const AGENT_ID = 'agent-demo-001'
const AGENT_NAME = '客服智能体 Demo'
const SESSION_ID = 'sess-demo-001'
const SESSION_ID_2 = 'sess-demo-002'
const SESSION_ID_NEW = 'sess-demo-new'

function buildChatBackend({
  sendBehavior = 'message', // 'message' | 'delta' | 'error' | 'silent'
  sessionList = [
    {
      id: SESSION_ID,
      agentId: AGENT_ID,
      title: '上次对话',
      createdAt: now,
      updatedAt: now
    },
    {
      id: SESSION_ID_2,
      agentId: AGENT_ID,
      title: '历史会话',
      createdAt: now,
      updatedAt: now
    }
  ],
  sessionMessages = {
    [SESSION_ID]: [
      { role: 'user', content: '你好', createdAt: now },
      { role: 'assistant', content: '你好！我是 ORIN。', createdAt: now }
    ]
  }
} = {}) {
  const sessions = [...sessionList]
  const messages = JSON.parse(JSON.stringify(sessionMessages))
  const kbsBySession = {}
  const requests = []
  const events = []

  const recordEvent = (method, path, payload) => {
    events.push({ method, path, payload })
  }

  const route = async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    requests.push({ method: request.method(), path })

    // agents
    if (request.method() === 'GET' && path === '/agents') {
      return route.fulfill(json([{
        id: AGENT_ID,
        name: AGENT_NAME,
        description: '默认客服智能体',
        modelName: 'demo-model',
        status: 'active'
      }]))
    }

    // monitored agents list (workspace AgentListV2 调用的真实端点)
    if (request.method() === 'GET' && path === '/monitor/agents/list') {
      return route.fulfill(json([{
        id: AGENT_ID,
        name: AGENT_NAME,
        description: '默认客服智能体',
        modelName: 'demo-model',
        providerType: 'Local',
        viewType: 'CHAT',
        status: 'active',
        ownerUserId: 7
      }]))
    }

    // sessions
    if (request.method() === 'GET' && path === '/agents/chat/sessions') {
      return route.fulfill(json(sessions))
    }
    if (request.method() === 'POST' && path === '/agents/chat/sessions') {
      const body = request.postDataJSON() || {}
      const created = {
        id: SESSION_ID_NEW,
        agentId: body.agentId || AGENT_ID,
        title: body.title || '新对话',
        createdAt: now,
        updatedAt: now
      }
      sessions.unshift(created)
      messages[created.id] = []
      return route.fulfill(json(created))
    }

    const sessionMatch = path.match(/^\/agents\/chat\/sessions\/([^/]+)$/)
    if (sessionMatch) {
      const sid = sessionMatch[1]
      if (request.method() === 'DELETE') {
        const idx = sessions.findIndex((s) => s.id === sid)
        if (idx >= 0) sessions.splice(idx, 1)
        return route.fulfill(json({ success: true }))
      }
      if (request.method() === 'GET') {
        const session = sessions.find((s) => s.id === sid)
        if (!session) {
          return route.fulfill(json({ code: 'NOT_FOUND', message: 'session not found', traceId: 'trace-miss' }, { status: 404 }))
        }
        return route.fulfill(json({
          ...session,
          attachedKbs: (kbsBySession[sid] || []).map((id) => ({ id }))
        }))
      }
    }

    // session messages
    const messagesMatch = path.match(/^\/agents\/chat\/sessions\/([^/]+)\/messages$/)
    if (messagesMatch) {
      const sid = messagesMatch[1]
      if (request.method() === 'GET') {
        return route.fulfill(json({ messages: messages[sid] || [] }))
      }
      if (request.method() === 'POST') {
        if (sendBehavior === 'error') {
          return route.fulfill(json({
            message: 'mock 500 from blocking',
            traceId: 'trace-blocking-err-1'
          }, { status: 500 }))
        }
        const body = request.postDataJSON() || {}
        const userMsg = {
          role: 'user',
          content: body.message || '',
          createdAt: now
        }
        const assistantMsg = {
          role: 'assistant',
          content: sendBehavior === 'silent' ? '' : `Mock reply: ${body.message || ''}`,
          createdAt: now
        }
        messages[sid] = messages[sid] || []
        messages[sid].push(userMsg, assistantMsg)
        recordEvent('POST', path, body)
        return route.fulfill(json(assistantMsg))
      }
    }

    // KB attach / detach / list
    const attachMatch = path.match(/^\/agents\/chat\/sessions\/([^/]+)\/attach-kb$/)
    if (attachMatch && request.method() === 'POST') {
      const sid = attachMatch[1]
      const body = request.postDataJSON() || {}
      kbsBySession[sid] = Array.from(new Set([...(kbsBySession[sid] || []), body.kbId]))
      return route.fulfill(json({ success: true }))
    }
    const detachMatch = path.match(/^\/agents\/chat\/sessions\/([^/]+)\/detach-kb$/)
    if (detachMatch && request.method() === 'POST') {
      const sid = detachMatch[1]
      const body = request.postDataJSON() || {}
      kbsBySession[sid] = (kbsBySession[sid] || []).filter((id) => id !== body.kbId)
      return route.fulfill(json({ success: true }))
    }
    if (path.match(/^\/agents\/chat\/sessions\/([^/]+)\/kbs$/) && request.method() === 'GET') {
      const sid = path.match(/^\/agents\/chat\/sessions\/([^/]+)\/kbs$/)[1]
      return route.fulfill(json((kbsBySession[sid] || []).map((id) => ({ id }))))
    }

    // SSE stream endpoint
    const streamMatch = path.match(/^\/agents\/chat\/sessions\/([^/]+)\/messages\/stream$/)
    if (streamMatch && request.method() === 'POST') {
      const body = request.postDataJSON() || {}
      if (sendBehavior === 'error') {
        return route.fulfill(json({
          message: 'mock 500 from stream',
          traceId: 'trace-stream-err-1'
        }, { status: 500 }))
      }
      if (sendBehavior === 'silent') {
        return route.fulfill({
          status: 200,
          contentType: 'text/event-stream',
          body: 'event: done\ndata: {}\n\n'
        })
      }
      if (sendBehavior === 'delta') {
        const events = [
          { event: 'delta', data: JSON.stringify({ delta: '增量A ' }) },
          { event: 'delta', data: JSON.stringify({ delta: '增量B' }) },
          { event: 'done', data: '{}' }
        ]
        return route.fulfill({
          status: 200,
          contentType: 'text/event-stream',
          body: events.map((e) => `event: ${e.event}\ndata: ${e.data}${SSE_LINE_BREAK}`).join('')
        })
      }
      // default 'message'
      const reply = `Mock stream reply: ${body.message || ''}`
      return route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: `event: message\ndata: ${JSON.stringify({ content: reply })}${SSE_LINE_BREAK}event: done\ndata: {}${SSE_LINE_BREAK}`
      })
    }

    // knowledge bases (sidebar)
    if (request.method() === 'GET' && path === '/knowledge/list') {
      return route.fulfill(json([
        { id: 'kb-1', name: '参考资料 A', documentCount: 12 },
        { id: 'kb-2', name: '参考资料 B', documentCount: 4 }
      ]))
    }

    return route.fulfill(json({}))
  }

  return { route, requests, events, sessions, messages, kbsBySession }
}

async function authenticate(page, roles = ['ROLE_USER']) {
  await page.addInitScript((value) => {
    window.localStorage.setItem('orin_token', 'e2e-role-user-token')
    window.localStorage.setItem('orin_menu_mode', 'topbar')
    document.cookie = 'orin_token=e2e-role-user-token; path=/'
    document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(value))}; path=/`
    document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify({ userId: 7, username: 'alice' }))}; path=/`
  }, roles)
}

async function gotoChat(page) {
  await page.goto('/chat')
  // sidebar 出现智能体卡片
  await expect(page.getByText(AGENT_NAME).first()).toBeVisible({ timeout: 8000 })
}

test.describe('ROLE_USER /chat browser acceptance', () => {
  test('ROLE_USER 登录后默认进入 /chat 且页面呈现 ORIN Chat 入口', async ({ page }) => {
    const backend = buildChatBackend()
    await authenticate(page, ['ROLE_USER'])
    await page.route('**/api/v1/**', backend.route)
    await page.route('**/api/v1/auth/login', (route) =>
      route.fulfill(json({ token: 'e2e-role-user-token', user: { userId: 7, username: 'alice' }, roles: ['ROLE_USER'] }))
    )

    // 1) 登录页
    await page.goto('/login')
    await page.getByPlaceholder('请输入用户名').fill('alice')
    await page.getByPlaceholder('请输入密码').fill('secret')
    await page.getByRole('button', { name: '登录' }).click()

    // 2) 跳到 /chat
    await expect(page).toHaveURL(/\/chat/, { timeout: 8000 })
    await expect(page.getByText(AGENT_NAME).first()).toBeVisible()

    // 3) /chat 页面只呈现已闭环的导航入口
    await expect(page.getByRole('button', { name: /新对话/ })).toBeVisible()
    await expect(page.getByRole('button', { name: 'AI 创作', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: /可用服务/ })).toBeVisible()
    await expect(page.getByRole('button', { name: /搜索聊天|文件库|已安排|更多/ })).toHaveCount(0)
    // 头像菜单里没有「管理端」（非 admin）
    const userButton = page.locator('.portal-user-wrapper')
    await userButton.click()
    await expect(page.getByRole('menuitem', { name: '管理端' })).toHaveCount(0)
    // Chat 不提供任何后台产品切换入口
    await expect(page.getByRole('menuitem', { name: 'ORIN 工作台' })).toHaveCount(0)
    await expect(page.getByRole('menuitem', { name: 'API Key 自助' })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: '退出登录' })).toBeVisible()
  })

  test('首页快捷操作均有真实行为', async ({ page }) => {
    const backend = buildChatBackend()
    await authenticate(page, ['ROLE_USER'])
    await page.route('**/api/v1/**', backend.route)

    await gotoChat(page)

    await page.getByRole('button', { name: '生成图片' }).click()
    await expect(page.locator('.creation-stage')).toBeVisible()
    await expect(page.getByRole('button', { name: '图像', exact: true })).toBeVisible()

    await page.getByRole('button', { name: '返回对话' }).click()
    const composer = page.locator('.composer-input textarea').first()
    await page.getByRole('button', { name: '撰写或编辑' }).click()
    await expect(composer).toHaveValue(/撰写或编辑/)

    await page.getByRole('button', { name: '查找资料' }).click()
    await expect(composer).toHaveValue(/参考资料/)
  })

  test('ROLE_USER 在 /chat 可看到智能体、发送消息并看到助手回复（流式或 blocking 兜底）', async ({ page }) => {
    const backend = buildChatBackend({ sendBehavior: 'delta' })
    await authenticate(page, ['ROLE_USER'])
    await page.route('**/api/v1/**', backend.route)

    await gotoChat(page)

    // 选中已有 session "上次对话"，等历史消息加载
    await page.getByText('上次对话').first().click()
    await expect(page.locator('.chat-stage').getByText('你好！我是 ORIN。')).toBeVisible({ timeout: 8000 })

    // 实际发一条消息，断言助手气泡最终出现（SSE delta 或 blocking 兜底都允许）
    const composer = page.locator('.composer-input textarea').first()
    await composer.fill('在吗')
    await composer.press('Enter')

    // 助手回复至少出现 mock 文本（"增量A 增量B" via SSE delta, 或 "Mock reply: 在吗" via blocking fallback）
    const stage = page.locator('.chat-stage')
    await expect(async () => {
      const html = await stage.innerHTML()
      const hasStream = html.includes('增量A')
      const hasFallback = html.includes('Mock reply')
      if (!hasStream && !hasFallback) {
        throw new Error('neither stream nor fallback assistant message rendered')
      }
    }).toPass({ timeout: 10000 })

    // 验证流式 / 阻断端点至少被调用过一次
    const streamHit = backend.requests.some((r) => r.path.endsWith('/messages/stream'))
    const blockingHit = backend.requests.some((r) => /\/messages$/.test(r.path) && r.method === 'POST')
    expect(streamHit || blockingHit).toBe(true)
  })

  test('刷新后会话能恢复（持久化的 session id 重新被选中）', async ({ page }) => {
    const backend = buildChatBackend()
    await authenticate(page, ['ROLE_USER'])
    await page.route('**/api/v1/**', backend.route)

    await gotoChat(page)
    await page.getByText('上次对话').first().click()
    // 等到对话历史已加载
    await expect(page.locator('.chat-stage').getByText('你好！我是 ORIN。')).toBeVisible({ timeout: 8000 })

    // 模拟"刷新"
    await page.reload()

    // 智能体和会话应该被自动恢复
    await expect(page.getByText(AGENT_NAME).first()).toBeVisible({ timeout: 8000 })
    await expect(page.getByText('上次对话').first()).toBeVisible()
    // 对话区历史消息重新加载
    await expect(page.locator('.chat-stage').getByText('你好！我是 ORIN。')).toBeVisible({ timeout: 8000 })

    expect(backend.requests.filter((r) => r.path === `/agents/chat/sessions/${SESSION_ID}/messages`)).toHaveLength(2)
  })

  test('发送消息失败时对话区显示 traceId', async ({ page }) => {
    const backend = buildChatBackend({ sendBehavior: 'error' })
    await authenticate(page, ['ROLE_USER'])
    await page.route('**/api/v1/**', backend.route)

    await gotoChat(page)
    await page.getByText('上次对话').first().click()

    const composer = page.locator('.composer-input textarea').first()
    await composer.fill('触发一次错误')
    await composer.press('Enter')

    // 对话区最终出现 traceId（来自 stream 或 blocking 回退）
    await expect(page.locator('.chat-stage').getByText(/trace-(stream|blocking)-err-1/)).toBeVisible({ timeout: 10000 })
  })

  test('ROLE_USER 可以进入工作台智能体列表', async ({ page }) => {
    const backend = buildChatBackend()
    await authenticate(page, ['ROLE_USER'])
    await page.route('**/api/v1/**', backend.route)

    await page.goto('/dashboard/applications/agents')
    await expect(page).toHaveURL((url) => url.pathname === '/workspace/agents', { timeout: 8000 })
    await expect(page.getByText('智能体列表').first()).toBeVisible()
    await expect(page.getByText(AGENT_NAME).first()).toBeVisible()
  })

  test('ROLE_USER 直接访问 /dashboard/control/users 也会被重定向到 /chat', async ({ page }) => {
    const backend = buildChatBackend()
    await authenticate(page, ['ROLE_USER'])
    await page.route('**/api/v1/**', backend.route)

    await page.goto('/dashboard/control/users')
    await expect(page).toHaveURL(/\/chat/, { timeout: 8000 })
  })

  test('ROLE_USER 访问旧 /dashboard/profile 会进入独立个人中心', async ({ page }) => {
    const backend = buildChatBackend()
    await authenticate(page, ['ROLE_USER'])
    await page.route('**/api/v1/**', backend.route)
    await page.route('**/api/v1/users/me', (route) =>
      route.fulfill(json({ userId: 7, username: 'alice', roles: ['ROLE_USER'] }))
    )

    await page.goto('/dashboard/profile')
    await expect(page).toHaveURL(/\/chat\/profile/, { timeout: 8000 })
    await expect(page.getByRole('heading', { name: '个人中心' })).toBeVisible()
  })
})
