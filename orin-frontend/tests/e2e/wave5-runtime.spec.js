import { expect, test } from '@playwright/test'

const now = '2026-05-22T10:00:00Z'

const traceRows = [
  {
    traceId: 'trace-wave5',
    spanId: 'span-1',
    operationName: 'agent.chat',
    serviceName: 'orin-backend',
    status: 'SUCCESS',
    startTime: now,
    durationMs: 128,
    metadata: { provider: 'mock' }
  }
]

const serverNode = {
  id: 'local',
  serverId: 'local',
  name: '本地节点',
  serverName: '本地节点',
  configured: true,
  enabled: true,
  online: true
}

const serverMetric = {
  serverId: 'local',
  serverName: '本地节点',
  timestamp: Date.now(),
  recordedAt: now,
  online: true,
  cpuUsage: 32,
  memoryUsage: 48,
  diskUsage: 56,
  gpuUsage: 0,
  cpuCores: 8,
  cpuLogicalCores: 8,
  memoryTotal: 17179869184,
  memoryUsed: 8246337208,
  diskTotal: 512000000000,
  diskUsed: 286720000000,
  os: 'macOS',
  uptime: '3 days'
}

const paged = (content = []) => ({
  content,
  records: content,
  total: content.length,
  totalElements: content.length,
  size: 20,
  number: 0
})

const json = (body) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
})

async function authenticate(page) {
  await page.addInitScript(() => {
    const roles = ['ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_USER']
    window.localStorage.setItem('orin_token', 'wave5-token')
    window.sessionStorage.setItem('orin_setup_completed', 'true')
    document.cookie = 'orin_token=wave5-token; path=/'
    document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(roles))}; path=/`
    document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify({ userId: 1, username: 'admin' }))}; path=/`
  })
}

async function mockBackends(page) {
  await page.route('**/*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname

    if (!path.startsWith('/api/v1/') && !path.startsWith('/api/')) {
      return route.continue()
    }

    if (path === '/api/v1/setup/status') {
      return route.fulfill(json({ completed: true, canInitialize: false }))
    }

    if (path === '/api/v1/dashboard/summary' || path === '/api/v1/monitor/dashboard/summary') {
      return route.fulfill(json({
        roles: ['ROLE_ADMIN'],
        defaultHome: '/dashboard/runtime/overview',
        systemHealth: {
          backend: { status: 'UP' },
          aiEngine: { status: 'UP' },
          database: { status: 'UP' }
        },
        metrics: [
          { label: '调用量', value: 128 },
          { label: '成功率', value: '99.1%' }
        ],
        recentActivity: [{ id: 'activity-1', title: '运行监控 smoke', time: now }],
        quickLinks: [{ title: 'Trace', path: '/dashboard/runtime/traces' }]
      }))
    }

    if (path === '/api/v1/monitor/tokens/stats') {
      return route.fulfill(json({
        totalTokens: 12000,
        promptTokens: 7200,
        completionTokens: 4800,
        totalCost: 12.3,
        callCount: 32,
        averageTokens: 375
      }))
    }
    if (path === '/api/v1/monitor/tokens/history') {
      return route.fulfill(json(paged([
        { id: 'tok-1', timestamp: now, provider: 'DeepSeek', model: 'deepseek-chat', totalTokens: 512, cost: 0.12 }
      ])))
    }
    if (path === '/api/v1/monitor/tokens/distribution') {
      return route.fulfill(json([{ provider: 'DeepSeek', tokens: 12000, cost: 12.3 }]))
    }
    if (
      path === '/api/v1/monitor/tokens/trend' ||
      path === '/api/v1/monitor/tokens/by-day-of-week' ||
      path === '/api/v1/monitor/tokens/by-hour' ||
      path === '/api/v1/monitor/tokens/by-type'
    ) {
      return route.fulfill(json([{ timestamp: now, date: '2026-05-22', tokens: 1200, totalTokens: 1200, count: 12 }]))
    }
    if (path === '/api/v1/monitor/sessions') {
      return route.fulfill(json([{ id: 'session-1', providerId: 'DeepSeek', success: false, errorMessage: '超时', createdAt: now }]))
    }

    if (path === '/api/v1/monitor/latency/stats') {
      return route.fulfill(json({ avgLatency: 240, p50Latency: 180, p95Latency: 640, p99Latency: 980, maxLatency: 1200 }))
    }
    if (path === '/api/v1/monitor/latency/history') {
      return route.fulfill(json(paged([{ id: 'lat-1', timestamp: now, provider: 'DeepSeek', model: 'deepseek-chat', latency: 240 }])))
    }
    if (path === '/api/v1/monitor/latency/trend') {
      return route.fulfill(json([{ timestamp: now, latency: 240 }]))
    }
    if (path === '/api/v1/monitor/stats/success-rate') {
      return route.fulfill(json({ totalCalls: 32, successCalls: 30, failedCalls: 2, successRate: 0.9375 }))
    }
    if (path === '/api/v1/monitor/stats/error-distribution') {
      return route.fulfill(json([{ providerId: 'DeepSeek', errorMessage: '超时', count: 2 }]))
    }

    if (path === '/api/v1/traces/recent' || path === '/api/v1/traces/search') {
      return route.fulfill(json(path.endsWith('/search') ? { found: true } : traceRows))
    }
    if (path === '/api/v1/traces/trace-wave5') {
      return route.fulfill(json(traceRows))
    }
    if (path === '/api/v1/traces/trace-wave5/stats') {
      return route.fulfill(json({ totalSpans: 1, durationMs: 128, errorCount: 0, serviceCount: 1 }))
    }
    if (path === '/api/v1/traces/trace-wave5/summary') {
      return route.fulfill(json({ traceId: 'trace-wave5', status: 'SUCCESS', summary: 'Wave 5 smoke trace' }))
    }
    if (path === '/api/v1/traces/trace-wave5/link') {
      return route.fulfill(json({ workflowTasks: [], collaborationPackages: [], auditLogs: [] }))
    }
    if (path === '/api/v1/dataflow/trace-wave5') {
      return route.fulfill(json({ traceId: 'trace-wave5', nodes: [], edges: [] }))
    }

    if (path === '/api/v1/workflow-tasks/statistics') {
      return route.fulfill(json({ statusStatistics: { QUEUED: 1, RUNNING: 1, COMPLETED: 3, FAILED: 1, DEAD: 0, CANCELLED: 0 } }))
    }
    if (path === '/api/v1/workflow-tasks/priority-statistics') {
      return route.fulfill(json({ HIGH: 1, NORMAL: 2, LOW: 1 }))
    }
    if (
      path === '/api/v1/workflow-tasks/queued' ||
      path === '/api/v1/workflow-tasks/running' ||
      path === '/api/v1/workflow-tasks/failed' ||
      path === '/api/v1/workflow-tasks/dead' ||
      path === '/api/v1/workflow-tasks/cancelled'
    ) {
      return route.fulfill(json(paged([{ taskId: 'task-wave5', status: 'QUEUED', priority: 'NORMAL', traceId: 'trace-wave5', updatedAt: now }])))
    }

    if (path === '/api/v1/monitor/server-hardware' || path === '/api/v1/monitor/prometheus/server-status') {
      return route.fulfill(json(serverMetric))
    }
    if (path === '/api/v1/monitor/server-hardware/nodes' || path === '/api/v1/monitor/server-info/list') {
      return route.fulfill(json([serverNode]))
    }
    if (path === '/api/v1/monitor/server-hardware/history') {
      return route.fulfill(json(paged([serverMetric])))
    }
    if (path === '/api/v1/monitor/server-hardware/trend') {
      return route.fulfill(json([serverMetric]))
    }
    if (path === '/api/v1/monitor/server-hardware/stats') {
      return route.fulfill(json({ nodeCount: 1, onlineCount: 1, averageCpuUsage: 32 }))
    }
    if (path === '/api/v1/monitor/prometheus/config') {
      return route.fulfill(json({ enabled: true, prometheusUrl: 'http://localhost:9090', cacheTtl: 10, refreshInterval: 15 }))
    }
    if (path === '/api/v1/monitor/system/properties') {
      return route.fulfill(json({ 'orin.hardware.monitor.enabled': 'true' }))
    }

    if (path === '/api/v1/alerts/rules') {
      return route.fulfill(json([{ id: 'rule-1', name: '错误率告警', enabled: true, ruleType: 'ERROR_RATE', severity: 'WARN' }]))
    }
    if (path === '/api/v1/alerts/history') {
      return route.fulfill(json(paged([{ id: 'alert-1', title: '错误率升高', severity: 'WARN', status: 'OPEN', createdAt: now }])))
    }
    if (path === '/api/v1/alerts/stats') {
      return route.fulfill(json({ total: 1, unread: 1, resolved: 0 }))
    }
    if (path === '/api/v1/alerts/notification-config' || path === '/api/v1/alerts/notification-config/status') {
      return route.fulfill(json({ enabled: true, channels: ['email'] }))
    }
    if (path === '/api/v1/alerts/history/unread-count') {
      return route.fulfill(json({ count: 1 }))
    }

    if (path === '/api/v1/admin/rate-limit/config' || path === '/api/v1/admin/rate-limit/config/cached') {
      return route.fulfill(json({
        enabled: true,
        globalQps: 100,
        userQps: 20,
        ipQps: 30,
        tokenQps: 10,
        responseHeadersEnabled: true
      }))
    }

    return route.fulfill(json({}))
  })
}

test.describe('Wave 5 runtime monitoring browser smoke', () => {
  test('opens runtime pages and legacy redirects without blank screens or runtime errors', async ({ page }) => {
    const runtimeErrors = []
    page.on('pageerror', (error) => runtimeErrors.push(`pageerror: ${error.message}`))
    page.on('console', (message) => {
      if (message.type() === 'error') runtimeErrors.push(`console error: ${message.text()}`)
    })

    await authenticate(page)
    await mockBackends(page)

    const paths = [
      '/dashboard/runtime/overview',
      '/dashboard/runtime/metrics',
      '/dashboard/runtime/latency',
      '/dashboard/runtime/errors',
      '/dashboard/runtime/traces',
      '/dashboard/runtime/traces/trace-wave5',
      '/dashboard/runtime/dataflow/trace-wave5',
      '/dashboard/runtime/tasks',
      '/dashboard/runtime/server',
      '/dashboard/runtime/server/local',
      '/dashboard/runtime/alerts',
      '/dashboard/runtime/logs',
      '/dashboard/runtime/rate-limit',
      '/dashboard/monitor/traces',
      '/dashboard/monitor/server',
      '/dashboard/monitor/tasks'
    ]

    for (const path of paths) {
      const startErrorCount = runtimeErrors.length
      await page.goto(path, { waitUntil: 'domcontentloaded' })
      await expect(page.locator('body')).not.toHaveText(/^\\s*$/)
      await expect(page.locator('body')).not.toContainText('登录工作台')
      expect(runtimeErrors.slice(startErrorCount), path).toEqual([])
    }

    await expect(page).toHaveURL(/\/dashboard\/runtime\/tasks/)
  })
})
