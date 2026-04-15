import { describe, expect, it } from 'vitest'
import { UI_TEXT } from '@/constants/uiText'
import { getMaturityTagType, getMaturityText } from '@/utils/maturity'

describe('maturity helpers', () => {
  it('returns expected text by maturity level', () => {
    expect(getMaturityText('available')).toBe(UI_TEXT.maturity.available)
    expect(getMaturityText('beta')).toBe(UI_TEXT.maturity.beta)
    expect(getMaturityText('planned')).toBe(UI_TEXT.maturity.planned)
  })

  it('falls back to available config for unknown level', () => {
    expect(getMaturityText('unknown')).toBe(UI_TEXT.maturity.available)
    expect(getMaturityTagType('unknown')).toBe('success')
  })
})
