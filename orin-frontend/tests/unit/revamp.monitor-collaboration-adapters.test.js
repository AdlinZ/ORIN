import { describe, expect, it } from 'vitest'
import {
  toCollaborationPackagesViewModel,
  toCollaborationStatsViewModel,
  toLangfuseStatusViewModel,
  toRuntimeSummaryViewModel,
  toSuccessRateViewModel,
  toTimelineViewModel
} from '@/viewmodels'

describe('revamp monitor/collaboration adapters', () => {
  it('normalizes runtime and success metrics with alias fields', () => {
    const runtime = toRuntimeSummaryViewModel({
      onlineAgents: '8',
      requestCount: '1200',
      tokenUsage: '45678',
      averageLatency: '233'
    })
    expect(runtime.activeAgents).toBe(8)
    expect(runtime.totalCalls).toBe(1200)
    expect(runtime.totalTokens).toBe(45678)
    expect(runtime.avgLatency).toBe(233)

    const success = toSuccessRateViewModel({
      totalCalls: '100',
      successCalls: '93',
      successRate: '93'
    })
    expect(success.total).toBe(100)
    expect(success.success).toBe(93)
    expect(success.ratio).toBe(93)
  })

  it('builds langfuse fallback message and link', () => {
    const disabled = toLangfuseStatusViewModel({ enabled: false, configured: false })
    expect(disabled.message).toContain('未启用')

    const enabled = toLangfuseStatusViewModel({ enabled: true, configured: true, url: 'https://demo.langfuse.local' })
    expect(enabled.message).toContain('已启用')
    expect(enabled.dashboardLink).toBe('https://demo.langfuse.local')
  })

  it('normalizes collaboration packages, stats and timeline ids', () => {
    const packages = toCollaborationPackagesViewModel([
      { id: 'pkg-1', taskIntent: 'Build report', category: 'REPORT' }
    ])
    expect(packages[0].packageId).toBe('pkg-1')
    expect(packages[0].intent).toBe('Build report')
    expect(packages[0].intentCategory).toBe('REPORT')

    const stats = toCollaborationStatsViewModel({
      totalTasks: 5,
      completedTasks: 4,
      failedTasks: 1,
      executingTasks: 0
    })
    expect(stats.total).toBe(5)
    expect(stats.completed).toBe(4)
    expect(stats.failed).toBe(1)
    expect(stats.successRate).toBe(80)
    expect(stats.byStatus.EXECUTING).toBe(0)

    const timeline = toTimelineViewModel([
      { timestamp: '2026-04-09T00:00:00Z', eventType: 'DISPATCHED', message: 'task sent' }
    ])
    expect(timeline[0].title).toBe('DISPATCHED')
    expect(timeline[0].description).toBe('task sent')
    expect(timeline[0].id).toContain('2026-04-09T00:00:00Z')
  })
})
