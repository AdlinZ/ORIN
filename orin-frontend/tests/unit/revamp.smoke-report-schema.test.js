import { describe, expect, it } from 'vitest'
import { buildReasonStats, buildSmokeReport, REVAMP_SMOKE_SCHEMA_VERSION } from '@/utils/revampSmokeReport'

describe('revamp smoke report schema', () => {
  it('builds schema-consistent smoke report payload', () => {
    const report = buildSmokeReport({
      source: 'unit-test',
      rows: [
        { flag: 'revampAgentsHub', route: '/dashboard/applications/agents', passed: true, reason: '通过' },
        { flag: 'revampSystemUnifiedGateway', route: '/dashboard/control/gateway', passed: false, reason: 'route missing' }
      ],
      stageMatrix: [{ stage: 1, flags: ['revampAgentsHub'] }]
    })

    expect(report.schemaVersion).toBe(REVAMP_SMOKE_SCHEMA_VERSION)
    expect(report.summary.total).toBe(2)
    expect(report.summary.failed).toBe(1)
    expect(report.summary.passed).toBe(false)
  })

  it('groups failed reasons for UI stats', () => {
    const stats = buildReasonStats([
      { passed: false, reason: 'A' },
      { passed: false, reason: 'A' },
      { passed: false, reason: 'B' },
      { passed: true, reason: 'C' }
    ])
    expect(stats).toEqual([
      { reason: 'A', count: 2 },
      { reason: 'B', count: 1 }
    ])
  })
})
