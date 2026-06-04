import { expect, test } from '@playwright/test'

const now = '2026-05-22T10:00:00Z'

const json = (body) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
})

const paged = (content = []) => ({
  content,
  records: content,
  data: content,
  total: content.length,
  totalElements: content.length,
  size: 20,
  number: 0
})

async function authenticate(page) {
  await page.addInitScript(() => {
    const roles = ['ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_USER']
    window.localStorage.setItem('orin_token', 'wave6-token')
    window.sessionStorage.setItem('orin_setup_completed', 'true')
    document.cookie = 'orin_token=wave6-token; path=/'
    document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(roles))}; path=/`
    document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify({ userId: 1, username: 'admin' }))}; path=/`
  })
}

async function mockBackends(page) {
  await page.route('**/*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname

    if (!path.startsWith('/api/')) return route.continue()

    if (path === '/api/v1/setup/status') {
      return route.fulfill(json({ completed: true, canInitialize: false }))
    }

    if (path === '/api/v1/users') {
      return route.fulfill(json({
        data: [{
          id: 1,
          username: 'admin',
          email: 'admin@orin.local',
          role: 'ROLE_ADMIN',
          status: 'ENABLED',
          departmentId: 1,
          createTime: now,
          lastLoginTime: now
        }],
        total: 1
      }))
    }
    if (path === '/api/v1/departments' || path === '/api/v1/departments/all') {
      return route.fulfill(json({
        data: [{
          departmentId: 1,
          departmentName: '平台部',
          departmentCode: 'PLATFORM',
          status: 'ENABLED',
          orderNum: 1,
          children: []
        }]
      }))
    }
    if (path === '/api/v1/roles') {
      return route.fulfill(json({
        data: [{
          roleId: 1,
          roleCode: 'ROLE_ADMIN',
          roleName: '管理员',
          description: '系统管理员',
          createTime: now
        }],
        total: 1
      }))
    }
    if (path === '/api/v1/api-keys') {
      return route.fulfill(json([{
        id: 'key-1',
        name: '平台访问密钥',
        keyPrefix: 'orin_sk_live',
        enabled: true,
        rateLimitPerMinute: 100,
        rateLimitPerDay: 10000,
        monthlyTokenQuota: 1000000,
        monthlyTokensUsed: 1200,
        createdAt: now,
        expiresAt: null
      }]))
    }
    if (path === '/api/v1/api-keys/external') {
      return route.fulfill(json([{
        id: 'ext-1',
        name: 'DeepSeek',
        provider: 'DeepSeek',
        apiKey: 'sk-****',
        baseUrl: 'https://api.deepseek.com',
        enabled: true,
        createdAt: now
      }]))
    }
    if (path === '/api/v1/system/providers' || path === '/api/v1/system/providers/all') {
      return route.fulfill(json([{ providerKey: 'deepseek', displayName: 'DeepSeek', enabled: true }]))
    }

    if (path === '/api/v1/multimodal/files' || path.startsWith('/api/v1/multimodal/files/type/')) {
      return route.fulfill(json([{
        id: 'file-1',
        fileName: 'demo.png',
        fileType: 'image',
        fileSize: 1024,
        uploadTime: now
      }]))
    }
    if (path === '/api/v1/multimodal/stats') {
      return route.fulfill(json({ totalFiles: 1, imageCount: 1, videoCount: 0, audioCount: 0, documentCount: 0 }))
    }
    if (path === '/api/v1/agents') {
      return route.fulfill(json([{ id: 'agent-1', agentId: 'agent-1', name: '客服智能体' }]))
    }
    if (path.includes('/knowledge/sync/client/agent-1/checkpoint')) {
      return route.fulfill(json({ checkpoint: now }))
    }
    if (path.includes('/knowledge/sync/client/agent-1/pending/count')) {
      return route.fulfill(json({ pendingCount: 0 }))
    }
    if (path.includes('/knowledge/sync/client/agent-1/changes')) {
      return route.fulfill(json(paged([{ id: 'change-1', changeType: 'UPDATED', changedAt: now, source: 'client' }])))
    }
    if (path.includes('/knowledge/sync/client/agent-1/webhooks')) {
      return route.fulfill(json([]))
    }
    if (path === '/api/v1/system/integrations/dify') {
      return route.fulfill(json({ apiUrl: 'http://localhost:3000/v1', enabled: false }))
    }
    if (path === '/api/v1/sync/dify/overview') {
      return route.fulfill(json({ apps: 0, workflows: 0, conversations: 0 }))
    }

    if (path === '/api/v1/monitor/system/properties') {
      return route.fulfill(json({
        'spring.datasource.url': 'jdbc:mysql://localhost:3306/orin',
        'spring.datasource.username': 'orin',
        'spring.data.redis.host': 'localhost',
        'spring.data.redis.port': '6379'
      }))
    }
    if (path === '/api/v1/knowledge/diagnose/milvus') {
      return route.fulfill(json({ healthy: true, message: 'ok' }))
    }
    if (path === '/api/v1/monitor/costs/distribution') {
      return route.fulfill(json([{ name: 'DeepSeek', value: 12.3, share: 1 }]))
    }
    if (path === '/api/v1/monitor/tokens/stats') {
      return route.fulfill(json({ totalTokens: 12000, totalCost: 12.3, callCount: 32 }))
    }
    if (path.includes('/api/v1/system/integrations/') || path.includes('/api/v1/monitor/')) {
      return route.fulfill(json({ enabled: true, healthy: true, status: 'UP' }))
    }

    if (path === '/api/v1/system/gateway/workbench') {
      return route.fulfill(json({
        overview: {
          requestCount: 100,
          errorCount: 2,
          avgLatencyMs: 120,
          topRoutes: [{ routeName: 'chat', requestCount: 80, avgLatencyMs: 120 }],
          serviceHealth: [{ serviceName: 'orin-backend', instanceCount: 1, healthyCount: 1, status: 'UP' }]
        },
        recentFailures: [],
        controlPlaneCoverage: { covered: 4, total: 4 }
      }))
    }
    if (path === '/api/v1/system/gateway/routes') {
      return route.fulfill(json([{ id: 'route-1', name: 'Chat', pathPattern: '/v1/chat/completions', method: 'POST', enabled: true, targetType: 'SERVICE' }]))
    }
    if (path === '/api/v1/system/gateway/services') {
      return route.fulfill(json([{ id: 'svc-1', serviceName: 'orin-backend', serviceKey: 'backend', protocol: 'HTTP', basePath: '/api', instanceCount: 1, status: 'UP' }]))
    }
    if (
      path === '/api/v1/system/gateway/acl' ||
      path === '/api/v1/system/gateway/policies' ||
      path === '/api/v1/system/gateway/policies/rate-limit' ||
      path === '/api/v1/system/gateway/policies/circuit-breaker' ||
      path === '/api/v1/system/gateway/policies/retry' ||
      path === '/api/v1/system/gateway/audit-logs'
    ) {
      return route.fulfill(json([]))
    }

    if (path === '/api/system/mcp/services') {
      return route.fulfill(json({
        data: [{ id: 'mcp-1', name: '本地工具', type: 'HTTP', url: 'http://localhost:8000/mcp', status: 'CONNECTED', enabled: true, healthScore: 98, lastConnected: now }],
        total: 1
      }))
    }
    if (path === '/api/system/mcp/tools' || path === '/api/v1/mcp/secrets') {
      return route.fulfill(json([]))
    }

    if (path === '/api/v1/pricing/config') {
      return route.fulfill(json([{ id: 1, providerId: 'deepseek-chat', tenantGroup: 'default', billingMode: 'TOKEN', inputCostUnit: 0.001, outputCostUnit: 0.002, inputPriceUnit: 0.002, outputPriceUnit: 0.004, currency: 'USD' }]))
    }
    if (path === '/api/v1/models') {
      return route.fulfill(json([{ id: 1, name: 'DeepSeek Chat', modelId: 'deepseek-chat', provider: 'DeepSeek', type: 'CHAT', status: 'ENABLED' }]))
    }

    if (path.includes('/api/v1/statistics/overview') || path.includes('/api/v1/api/v1/statistics/overview')) {
      return route.fulfill(json({ totalActiveUsers: 1, totalApiCalls: 100, totalTokens: 12000, totalTasks: 4 }))
    }
    if (path.includes('/api/v1/statistics/users') || path.includes('/api/v1/api/v1/statistics/users')) {
      return route.fulfill(json({ totalActiveUsers: 1, totalApiCalls: 100 }))
    }
    if (path.includes('/api/v1/statistics/agents') || path.includes('/api/v1/api/v1/statistics/agents')) {
      return route.fulfill(json({ topAgents: [{ agent: '客服智能体', count: 12 }] }))
    }
    if (path.includes('/api/v1/statistics/tokens') || path.includes('/api/v1/api/v1/statistics/tokens')) {
      return route.fulfill(json({ totalTokens: { total: 12000 }, daily: [{ date: '2026-05-22', tokens: 1200 }] }))
    }
    if (path.includes('/api/v1/statistics/tasks') || path.includes('/api/v1/api/v1/statistics/tasks')) {
      return route.fulfill(json({ byStatus: [{ status: 'COMPLETED', count: 4 }] }))
    }

    if (path === '/api/v1/alerts/notification-config' || path === '/api/v1/alerts/notification-config/status') {
      return route.fulfill(json({ emailEnabled: true, emailRecipients: 'admin@orin.local', criticalOnly: false }))
    }
    if (path === '/api/v1/system/mail-config' || path === '/api/v1/system/mail-config/status') {
      return route.fulfill(json({ enabled: true, host: 'smtp.local', port: 587 }))
    }
    if (path === '/api/v1/system/mail-templates') {
      return route.fulfill(json([]))
    }
    if (path === '/api/v1/system/mail-logs' || path === '/api/v1/system/mail-inbox') {
      return route.fulfill(json(paged([])))
    }
    if (path === '/api/v1/system/mail-inbox/unread-count') {
      return route.fulfill(json({ count: 0 }))
    }
    if (path === '/api/v1/system/mail-inbox/imap-status') {
      return route.fulfill(json({ connected: true }))
    }

    if (path === '/api/v1/audit/logs') {
      return route.fulfill(json(paged([{ id: 'audit-1', userName: 'admin', action: 'LOGIN', resource: 'system', createdAt: now }])))
    }
    if (path === '/api/v1/system/log-config') {
      return route.fulfill(json({ auditEnabled: true, logLevel: 'INFO', retentionDays: 30 }))
    }
    if (path === '/api/v1/system/log-config/stats') {
      return route.fulfill(json({ totalLogs: 1, errorLogs: 0, auditLogs: 1 }))
    }
    if (path === '/api/v1/system/log-config/loggers') {
      return route.fulfill(json([{ name: 'com.adlin.orin', level: 'INFO' }]))
    }

    if (path === '/api/v1/system/maintenance/info') {
      return route.fulfill(json({ version: '1.0.0', uptime: '1 天', dbVersion: 'MySQL 8', cpuUsage: 30, memoryUsage: 45, lastBackup: now }))
    }
    if (path === '/api/v1/system/maintenance/logs') {
      return route.fulfill(json([{ operation: '数据备份', status: 'success', operator: 'admin', timestamp: now, message: '完成' }]))
    }

    if (path === '/api/v1/help/articles' || path === '/api/v1/help/categories') {
      return route.fulfill(json([]))
    }

    return route.fulfill(json({}))
  })
}

test.describe('Wave 6 system control browser smoke', () => {
  test('opens system control pages and redirects without blank screens or runtime errors', async ({ page }) => {
    const runtimeErrors = []
    page.on('pageerror', (error) => runtimeErrors.push(`pageerror: ${error.message}`))
    page.on('console', (message) => {
      if (message.type() === 'error') runtimeErrors.push(`console error: ${message.text()}`)
    })

    await authenticate(page)
    await mockBackends(page)

    const paths = [
      '/dashboard/control/users',
      '/dashboard/control/departments',
      '/dashboard/control/roles',
      '/dashboard/control/data-assets',
      '/dashboard/control/system-env',
      '/dashboard/control/gateway',
      '/dashboard/control/mcp-service',
      '/dashboard/control/pricing',
      '/dashboard/control/statistics',
      '/dashboard/control/notification-channels',
      '/dashboard/control/audit-logs',
      '/dashboard/runtime/maintenance',
      '/dashboard/control/api-keys',
      '/dashboard/control/mail/setup'
    ]

    for (const path of paths) {
      const startErrorCount = runtimeErrors.length
      await page.goto(path, { waitUntil: 'domcontentloaded' })
      await expect(page.locator('body')).not.toHaveText(/^\\s*$/)
      await expect(page.locator('body')).not.toContainText('登录工作台')
      expect(runtimeErrors.slice(startErrorCount), path).toEqual([])
    }

    await expect(page).toHaveURL(/\/dashboard\/control\/notification-channels/)

    await page.goto('/dashboard/control/api-keys', { waitUntil: 'domcontentloaded' })
    await expect(page).toHaveURL(/\/dashboard\/control\/gateway/)
  })
})
