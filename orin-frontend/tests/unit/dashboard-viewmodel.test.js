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
    expect(vm.defaultHome).toBe('/portal')
    expect(vm.systemHealth.backend.status).toBe('UNKNOWN')
    expect(vm.metrics.openTasks).toBe(0)
    expect(vm.recentActivity).toEqual([])
  })
})
