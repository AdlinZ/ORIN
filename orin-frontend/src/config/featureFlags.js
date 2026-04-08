const FLAG_PREFIX = 'orin_ff_'

export const FEATURE_FLAGS = Object.freeze({
  revampAgentsHub: true,
  revampKnowledgeHub: true,
  revampWorkflowHub: true,
  revampCollaboration: true,
  revampRuntimeOverview: true,
  revampSystemGateway: true,
  revampAuditCenter: true,
  revampSystemConfigHub: true,
  showMaturityBadge: true
})

export const FEATURE_FLAG_KEYS = Object.freeze(Object.keys(FEATURE_FLAGS))

export function getFeatureFlagStorageKey(flagName) {
  return `${FLAG_PREFIX}${flagName}`
}

export function isFeatureEnabled(flagName, fallback = false) {
  if (!Object.prototype.hasOwnProperty.call(FEATURE_FLAGS, flagName)) {
    return fallback
  }

  try {
    const raw = window.localStorage.getItem(getFeatureFlagStorageKey(flagName))
    if (raw === null) {
      return FEATURE_FLAGS[flagName]
    }
    return raw === 'true'
  } catch (error) {
    return FEATURE_FLAGS[flagName]
  }
}

export function setFeatureFlag(flagName, enabled) {
  if (!Object.prototype.hasOwnProperty.call(FEATURE_FLAGS, flagName)) {
    return
  }
  window.localStorage.setItem(getFeatureFlagStorageKey(flagName), String(Boolean(enabled)))
}

export function setFeatureFlagsBatch(nextFlags = {}) {
  FEATURE_FLAG_KEYS.forEach((flag) => {
    if (Object.prototype.hasOwnProperty.call(nextFlags, flag)) {
      setFeatureFlag(flag, nextFlags[flag])
    }
  })
}

export function resetFeatureFlags() {
  FEATURE_FLAG_KEYS.forEach((flag) => {
    window.localStorage.removeItem(getFeatureFlagStorageKey(flag))
  })
}

export function getFeatureFlagsSnapshot(storage) {
  const source = storage || (typeof window !== 'undefined' ? window.localStorage : null)
  return FEATURE_FLAG_KEYS.reduce((acc, flag) => {
    if (!source || typeof source.getItem !== 'function') {
      acc[flag] = FEATURE_FLAGS[flag]
      return acc
    }
    const raw = source.getItem(getFeatureFlagStorageKey(flag))
    acc[flag] = raw === null ? FEATURE_FLAGS[flag] : raw === 'true'
    return acc
  }, {})
}
