import { describe, expect, it } from 'vitest'
import { LEGACY_ROUTE_REDIRECTS, ROUTES } from '@/router/routes'

describe('route cleanup contracts', () => {
  it('contains no self redirect entries', () => {
    const entries = Object.entries(LEGACY_ROUTE_REDIRECTS)
    const hasSelfRedirect = entries.some(([from, to]) => from === to)
    expect(hasSelfRedirect).toBe(false)
  })

  it('keeps required historical paths', () => {
    expect(LEGACY_ROUTE_REDIRECTS['/workflow']).toBe('/dashboard/applications/workflows')
    expect(LEGACY_ROUTE_REDIRECTS['/system/api-keys']).toBe('/dashboard/control/api-keys')
  })

  it('redirects collapsed duplicate paths to canonical routes', () => {
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/applications/collaboration/tasks']).toBe(
      ROUTES.AGENTS.COLLABORATION
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/applications/collaboration/config']).toBe(
      ROUTES.AGENTS.COLLABORATION
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/applications/tools']).toBe(
      '/dashboard/applications/mcp'
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/runtime/alert-rules']).toBe(
      ROUTES.MONITOR.ALERTS
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/applications/version']).toBe(
      ROUTES.AGENTS.WORKFLOW_EXECUTION
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/applications/test']).toBe(
      ROUTES.AGENTS.WORKFLOW_EXECUTION
    )
  })

  it('removes rollout constants and keeps alert alias compatible', () => {
    expect(ROUTES.SYSTEM.REVAMP_ROLLOUT).toBeUndefined()
    expect(ROUTES.MONITOR.ALERT_RULES).toBe(ROUTES.MONITOR.ALERTS)
  })
})
