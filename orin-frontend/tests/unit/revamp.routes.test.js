import { describe, expect, it } from 'vitest'
import { LEGACY_ROUTE_REDIRECTS, ROUTES } from '@/router/routes'
import { getDashboardGuardRedirect } from '@/router/topMenuConfig'

describe('route cleanup contracts', () => {
  it('contains no self redirect entries', () => {
    const entries = Object.entries(LEGACY_ROUTE_REDIRECTS)
    const hasSelfRedirect = entries.some(([from, to]) => from === to)
    expect(hasSelfRedirect).toBe(false)
  })

  it('keeps required historical paths', () => {
    expect(ROUTES.SETUP).toBe('/setup')
    expect(ROUTES.CHAT).toBe('/chat')
    expect(ROUTES.PLATFORM).toBe('/platform')
    expect(ROUTES.PLATFORM_API_KEYS).toBe('/platform/api-keys')
    expect(ROUTES.PORTAL).toBe(ROUTES.CHAT)
    expect(ROUTES.PORTAL_API_KEYS).toBe(ROUTES.PLATFORM_API_KEYS)
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
      ROUTES.MCP.SERVERS
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/applications/mcp']).toBe(
      '/dashboard/applications/extensions?tab=mcp'
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/mcp/servers']).toBe(
      '/dashboard/applications/extensions?tab=mcp'
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

describe('ROLE_USER dashboard boundary', () => {
  it('redirects ROLE_USER on /dashboard/applications/agents to /chat', () => {
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/dashboard/applications/agents')).toBe(ROUTES.CHAT)
  })

  it('redirects ROLE_USER on /dashboard/runtime/overview to /chat', () => {
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/dashboard/runtime/overview')).toBe(ROUTES.CHAT)
  })

  it('redirects ROLE_USER on /dashboard/control/users to /chat', () => {
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/dashboard/control/users')).toBe(ROUTES.CHAT)
  })

  it('does not redirect ROLE_USER on /dashboard/profile (shared)', () => {
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/dashboard/profile')).toBeNull()
  })

  it('does not redirect ROLE_ADMIN on any /dashboard path', () => {
    expect(getDashboardGuardRedirect(['ROLE_ADMIN'], '/dashboard/applications/agents')).toBeNull()
    expect(getDashboardGuardRedirect(['ROLE_ADMIN'], '/dashboard/control/users')).toBeNull()
    expect(getDashboardGuardRedirect(['ROLE_ADMIN'], '/dashboard/profile')).toBeNull()
  })
})
