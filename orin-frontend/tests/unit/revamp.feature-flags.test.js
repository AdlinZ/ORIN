import { beforeEach, describe, expect, it } from 'vitest'
import {
  FEATURE_FLAG_KEYS,
  getFeatureFlagsSnapshot,
  getFeatureFlagStorageKey,
  isFeatureEnabled,
  resetFeatureFlags,
  setFeatureFlag,
  setFeatureFlagsBatch
} from '@/config/featureFlags'

const storage = {}

const localStorageMock = {
  getItem: (key) => (Object.prototype.hasOwnProperty.call(storage, key) ? storage[key] : null),
  setItem: (key, value) => {
    storage[key] = String(value)
  },
  removeItem: (key) => {
    delete storage[key]
  },
  clear: () => {
    Object.keys(storage).forEach((key) => delete storage[key])
  }
}

describe('feature flags', () => {
  beforeEach(() => {
    Object.defineProperty(window, 'localStorage', {
      value: localStorageMock,
      configurable: true
    })
    localStorageMock.clear()
  })

  it('returns fallback for unknown flag', () => {
    expect(isFeatureEnabled('not-exists', true)).toBe(true)
    expect(isFeatureEnabled('not-exists', false)).toBe(false)
  })

  it('uses localStorage override for known flag', () => {
    expect(isFeatureEnabled('revampAgentsHub')).toBe(true)
    setFeatureFlag('revampAgentsHub', false)
    expect(isFeatureEnabled('revampAgentsHub')).toBe(false)
    setFeatureFlag('revampAgentsHub', true)
    expect(isFeatureEnabled('revampAgentsHub')).toBe(true)
  })

  it('builds storage key and exports snapshot', () => {
    expect(getFeatureFlagStorageKey('revampAgentsHub')).toBe('orin_ff_revampAgentsHub')
    setFeatureFlag('revampAgentsHub', false)
    const snapshot = getFeatureFlagsSnapshot(window.localStorage)
    expect(FEATURE_FLAG_KEYS.length).toBeGreaterThan(0)
    expect(snapshot.revampAgentsHub).toBe(false)
    expect(typeof snapshot.revampKnowledgeHub).toBe('boolean')
  })

  it('supports batch set and reset', () => {
    setFeatureFlagsBatch({
      revampAgentsHub: false,
      revampKnowledgeHub: false,
      showMaturityBadge: false
    })
    expect(isFeatureEnabled('revampAgentsHub')).toBe(false)
    expect(isFeatureEnabled('revampKnowledgeHub')).toBe(false)
    expect(isFeatureEnabled('showMaturityBadge')).toBe(false)

    resetFeatureFlags()
    expect(window.localStorage.getItem('orin_ff_revampAgentsHub')).toBe(null)
    expect(window.localStorage.getItem('orin_ff_showMaturityBadge')).toBe(null)
    expect(isFeatureEnabled('revampAgentsHub')).toBe(true)
  })
})
