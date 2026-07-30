import { describe, expect, it } from 'vitest'
import {
  hasNewSecretValue,
  isMaskedSecret,
  MASKED_SECRET,
} from '@/domains/system/secretConfig'

describe('system secret configuration contract', () => {
  it('recognizes the backend mask as a keep-existing sentinel', () => {
    expect(MASKED_SECRET).toBe('********')
    expect(isMaskedSecret(MASKED_SECRET)).toBe(true)
    expect(hasNewSecretValue(MASKED_SECRET)).toBe(false)
  })

  it('only treats a non-empty replacement as a new secret', () => {
    expect(hasNewSecretValue('')).toBe(false)
    expect(hasNewSecretValue('   ')).toBe(false)
    expect(hasNewSecretValue('new-secret')).toBe(true)
  })
})
