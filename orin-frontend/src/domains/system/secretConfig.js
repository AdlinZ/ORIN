export const MASKED_SECRET = '********'

export const isMaskedSecret = (value) => value === MASKED_SECRET

/**
 * The backend interprets the fixed mask as “keep the existing secret”.
 * UI code must never use this value as an external-service credential.
 */
export const hasNewSecretValue = (value) => {
  const normalized = String(value || '').trim()
  return normalized.length > 0 && !isMaskedSecret(normalized)
}
