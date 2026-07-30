import { resolvePublicOrigin } from '@/domains/endpoint/publishDelivery'

/**
 * Resolve the externally reachable Control Plane origin embedded in Runner
 * commands. A relative enrollment endpoint must not make a local Runner depend
 * on the Vite development proxy.
 */
export function resolveRunnerControlPlaneOrigin(
  enrollmentEndpoint,
  browserOrigin,
  configuredOrigin = '',
) {
  const fallbackOrigin = resolvePublicOrigin(browserOrigin, configuredOrigin)
  if (!enrollmentEndpoint) return fallbackOrigin

  try {
    return new URL(enrollmentEndpoint, `${fallbackOrigin}/`).origin
  } catch (_) {
    return fallbackOrigin
  }
}
