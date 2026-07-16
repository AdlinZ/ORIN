import { describe, expect, it } from 'vitest'
import { toDashboardSummaryViewModel } from '@/viewmodels'

describe('dashboard summary viewmodel', () => {
  it('normalizes dashboard summary payload', () => {
    const vm = toDashboardSummaryViewModel({
      roles: ['ROLE_ADMIN'],
      defaultHome: '/dashboard/runtime/overview',
      systemHealth: {
        backend: { status: 'UP' },
        aiEngine: { status: 'UP', service: 'orin-ai-engine', reachable: true }
      },
      metrics: {
        agents: '3',
        openTasks: 2,
        failedTasks: null
      },
      recentActivity: [
        {
          id: 'audit-1',
          endpoint: '/v1/chat/completions',
          method: 'POST',
          success: true,
          requestParams: '{"token":"secret"}'
        }
      ],
      quickLinks: [{ title: '运行监控', path: '/dashboard/runtime/overview' }]
    })

    expect(vm.roles).toEqual(['ROLE_ADMIN'])
    expect(vm.defaultHome).toBe('/dashboard/runtime/overview')
    expect(vm.metrics.agents).toBe(3)
    expect(vm.metrics.failedTasks).toBe(0)
    expect(vm.systemHealth.aiEngine.reachable).toBe(true)
    expect(vm.recentActivity[0]).not.toHaveProperty('requestParams')
    expect(vm.quickLinks[0].title).toBe('运行监控')
  })

  it('falls back to portal shape for empty payload', () => {
    const vm = toDashboardSummaryViewModel()

    expect(vm.roles).toEqual(['ROLE_USER'])
    expect(vm.defaultHome).toBe('/chat')
    expect(vm.systemHealth.backend.status).toBe('UNKNOWN')
    expect(vm.metrics.openTasks).toBe(0)
    expect(vm.recentActivity).toEqual([])
  })

  it('defaults trends and agentTypes when missing', () => {
    const vm = toDashboardSummaryViewModel()

    expect(vm.trends).toEqual({ range: { start: '', end: '' }, requestCount: [], tokenUsage: [] })
    expect(vm.agentTypes).toEqual([])
    expect(vm.agentTypeTotal).toBe(0)
    expect(vm.metrics.taskStatuses).toHaveLength(7)
    expect(vm.metrics.totalTasks).toBe(0)
    expect(vm.isOnline).toBe(false)
  })

  it('normalizes trends series and agent type counts', () => {
    const vm = toDashboardSummaryViewModel({
      systemHealth: {
        backend: { status: 'UP' },
        aiEngine: { status: 'UP', service: 'orin-ai-engine', reachable: true }
      },
      trends: {
        range: { start: '2026-06-30', end: '2026-07-13' },
        requestCount: [
          { date: '2026-07-12', value: '12' },
          { date: '2026-07-13', value: 8 },
          { date: 'invalid', value: 5 }
        ],
        tokenUsage: [
          { date: '2026-07-13', value: 1024 }
        ]
      },
      agentTypes: [
        { key: 'agent', label: 'Agent', count: 4 },
        { key: 'CHAT', label: 'Chat', count: 2 },
        { key: 'unknown', label: null, count: 0 }
      ]
    })

    expect(vm.isOnline).toBe(true)
    expect(vm.trends.range.start).toBe('2026-06-30')
    expect(vm.trends.requestCount).toEqual([
      { date: '2026-07-12', value: 12 },
      { date: '2026-07-13', value: 8 }
    ])
    expect(vm.trends.tokenUsage[0]).toEqual({ date: '2026-07-13', value: 1024 })
    expect(vm.agentTypes).toEqual([
      { key: 'agent', label: 'Agent', count: 4 },
      { key: 'chat', label: 'Chat', count: 2 }
    ])
    expect(vm.agentTypeTotal).toBe(6)
  })

  it('derives task statuses in canonical order', () => {
    const vm = toDashboardSummaryViewModel({
      metrics: {
        tasks: { RUNNING: 3, FAILED: 1, COMPLETED: 10 }
      }
    })

    expect(vm.metrics.taskStatuses[0]).toEqual({ status: 'QUEUED', label: 'QUEUED', count: 0 })
    expect(vm.metrics.taskStatuses[1]).toEqual({ status: 'RUNNING', label: 'RUNNING', count: 3 })
    expect(vm.metrics.taskStatuses[3]).toEqual({ status: 'COMPLETED', label: 'COMPLETED', count: 10 })
    expect(vm.metrics.taskStatuses[4]).toEqual({ status: 'FAILED', label: 'FAILED', count: 1 })
    expect(vm.metrics.totalTasks).toBe(14)
  })
})
