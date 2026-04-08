import { describe, expect, it } from 'vitest'
import { getMaturityTagType, getMaturityText } from '@/utils/maturity'

describe('maturity helpers', () => {
  it('returns expected text by maturity level', () => {
    expect(getMaturityText('available')).toBe('可用')
    expect(getMaturityText('beta')).toBe('内测')
    expect(getMaturityText('planned')).toBe('规划中')
  })

  it('falls back to available config for unknown level', () => {
    expect(getMaturityText('unknown')).toBe('可用')
    expect(getMaturityTagType('unknown')).toBe('success')
  })
})
