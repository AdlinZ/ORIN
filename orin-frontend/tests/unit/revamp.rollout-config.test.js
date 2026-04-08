import { describe, expect, it } from 'vitest'
import { FEATURE_FLAG_KEYS } from '@/config/featureFlags'
import { REVAMP_ROLLOUT_ITEMS, REVAMP_ROLLOUT_STAGES } from '@/config/revampRollout'

describe('revamp rollout config', () => {
  it('keeps all rollout items mapped to valid feature flags', () => {
    for (const item of REVAMP_ROLLOUT_ITEMS) {
      expect(FEATURE_FLAG_KEYS).toContain(item.key)
      expect(typeof item.route).toBe('string')
      expect(item.route.length).toBeGreaterThan(0)
      expect(item.v2View.startsWith('@/views/revamp/')).toBe(true)
    }
  })

  it('defines non-empty staged rollout with distinct flags', () => {
    const flags = REVAMP_ROLLOUT_STAGES.flatMap((stage) => stage.flags)
    expect(REVAMP_ROLLOUT_STAGES.length).toBeGreaterThan(0)
    expect(new Set(flags).size).toBe(flags.length)
  })
})
