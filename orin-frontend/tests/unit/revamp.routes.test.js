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
    expect(LEGACY_ROUTE_REDIRECTS['/system/api-keys']).toBe('/dashboard/control/gateway?workspace=access')
  })

  it('redirects collapsed duplicate paths to canonical routes', () => {
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/applications/collaboration/tasks']).toBe(
      ROUTES.AGENTS.COLLABORATION
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/applications/collaboration/config']).toBe(
      ROUTES.AGENTS.COLLABORATION
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/applications/playground/workflows']).toBe(
      ROUTES.AGENTS.COLLABORATION_WORKFLOWS
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

  it('uses collaboration as the canonical route for multi-agent coordination', () => {
    expect(ROUTES.AGENTS.COLLABORATION_WORKFLOWS).toBe(
      '/dashboard/applications/collaboration/workflows'
    )
    expect(ROUTES.AGENTS.PLAYGROUND_WORKFLOWS).toBeUndefined()
  })

  it('removes rollout constants and keeps alert alias compatible', () => {
    expect(ROUTES.SYSTEM.REVAMP_ROLLOUT).toBeUndefined()
    expect(ROUTES.MONITOR.ALERT_RULES).toBe(ROUTES.MONITOR.ALERTS)
  })

  it('keeps legacy workflow routes and redirects removed V2 entries to V1 fallback', () => {
    expect(ROUTES.AGENTS.WORKFLOWS).toBe('/dashboard/applications/workflows')
    expect(ROUTES.AGENTS.WORKFLOWS_V2).toBeUndefined()
    expect(ROUTES.AGENTS.WORKFLOWS_V2_CANVAS).toBeUndefined()
    expect(ROUTES.AGENTS.WORKFLOWS_V2_RUNS).toBeUndefined()
    expect(ROUTES.AGENTS.WORKFLOW_V2_DETAIL).toBeUndefined()
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/applications/workflows-v2']).toBe(
      ROUTES.AGENTS.WORKFLOWS
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/applications/workflows-v2/canvas']).toBe(
      ROUTES.AGENTS.WORKFLOW_VISUAL
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/applications/workflows-v2/runs']).toBe(
      ROUTES.AGENTS.WORKFLOW_EXECUTION
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/applications/workflows-v2/:id']).toBe(
      ROUTES.AGENTS.WORKFLOWS
    )
  })
})
