import { describe, expect, it } from 'vitest'
import { LEGACY_ROUTE_REDIRECTS, ROUTES } from '@/router/routes'
import { getDashboardGuardRedirect } from '@/router/topMenuConfig'
import router from '@/router'

describe('route cleanup contracts', () => {
  it('contains no self redirect entries', () => {
    const entries = Object.entries(LEGACY_ROUTE_REDIRECTS)
    const hasSelfRedirect = entries.some(([from, to]) => from === to)
    expect(hasSelfRedirect).toBe(false)
  })

  it('keeps required historical paths', () => {
    expect(ROUTES.SETUP).toBe('/setup')
    expect(ROUTES.CHAT).toBe('/chat')
    expect(ROUTES.REGISTER).toBe('/register')
    expect(ROUTES.PLATFORM).toBe('/platform')
    expect(ROUTES.PLATFORM_API_KEYS).toBe('/platform/api-keys')
    expect(ROUTES.WORKSPACE).toBe('/workspace/developer')
    expect(ROUTES.ADMIN).toBe('/admin/admin-overview')
    expect(ROUTES.PORTAL).toBe(ROUTES.CHAT)
    expect(ROUTES.PORTAL_API_KEYS).toBe(ROUTES.PLATFORM_API_KEYS)
    expect(LEGACY_ROUTE_REDIRECTS['/workflow']).toBe('/workspace/workflows')
    expect(LEGACY_ROUTE_REDIRECTS['/system/api-keys']).toBe('/admin/gateway?workspace=access')
  })

  it('resolves workspace and admin to their main-layout mounts', () => {
    const workspace = router.resolve(ROUTES.WORKSPACE)
    const admin = router.resolve(ROUTES.ADMIN)

    // Phase 7b: workspace 与 admin 都已迁到 top-level canonical mount
    expect(workspace.name).toBe('ApplicationDeveloper')
    expect(workspace.matched.some((record) => record.path === ROUTES.WORKSPACE_ROOT)).toBe(true)
    expect(admin.name).toBe('ControlAdminDashboard')
    expect(admin.matched.some((record) => record.path === ROUTES.ADMIN_ROOT)).toBe(true)
  })

  it('keeps AI infrastructure and open-platform routes in the admin product surface', () => {
    const modelManagement = router.resolve(ROUTES.ADMIN_PATHS.MODELS)
    const gateway = router.resolve(ROUTES.ADMIN_PATHS.GATEWAY)
    const workspaceModelRoute = router.resolve('/workspace/models')

    expect(modelManagement.name).toBe('ControlModels')
    expect(modelManagement.matched.some((record) => record.path === ROUTES.ADMIN_ROOT)).toBe(true)
    expect(modelManagement.matched.some((record) => record.path === ROUTES.WORKSPACE_ROOT)).toBe(false)
    expect(gateway.matched.some((record) => record.path === ROUTES.ADMIN_ROOT)).toBe(true)
    expect(workspaceModelRoute.name).toBe('GlobalNotFound')
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
      '/workspace/extensions?tab=mcp'
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/mcp/servers']).toBe(
      '/workspace/extensions?tab=mcp'
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

  it('redirects former product-surface paths to canonical mounts', () => {
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/applications/workspace']).toBe(
      ROUTES.AGENTS.WORKSPACE
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/resources/assets']).toBe(
      ROUTES.KNOWLEDGE.ASSETS
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/runtime/overview']).toBe(
      ROUTES.MONITOR.HOME
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/runtime/maintenance']).toBe(
      ROUTES.MONITOR.MAINTENANCE
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/control/admin-overview']).toBe(
      ROUTES.SYSTEM.ADMIN_DASHBOARD
    )
    expect(LEGACY_ROUTE_REDIRECTS['/dashboard/control/api-keys']).toBe(
      ROUTES.SYSTEM.API_KEYS
    )
  })

  it('uses collaboration as the canonical route for multi-agent coordination', () => {
    expect(ROUTES.AGENTS.COLLABORATION_WORKFLOWS).toBe(
      '/workspace/collaboration/workflows'
    )
    expect(ROUTES.AGENTS.PLAYGROUND_WORKFLOWS).toBeUndefined()
  })

  it('removes rollout constants and keeps alert alias compatible', () => {
    expect(ROUTES.SYSTEM.REVAMP_ROLLOUT).toBeUndefined()
    expect(ROUTES.MONITOR.ALERT_RULES).toBe(ROUTES.MONITOR.ALERTS)
  })

  it('keeps legacy workflow routes and redirects removed V2 entries to V1 fallback', () => {
    expect(ROUTES.AGENTS.WORKFLOWS).toBe('/workspace/workflows')
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
  it('allows ROLE_USER into the workspace application domain', () => {
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/dashboard/applications/agents')).toBeNull()
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/dashboard/resources/assets')).toBeNull()
  })

  it('redirects ROLE_USER on /dashboard/runtime/overview to /chat', () => {
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/dashboard/runtime/overview')).toBe(ROUTES.CHAT)
  })

  it('redirects ROLE_USER on /dashboard/control/users to /chat', () => {
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/dashboard/control/users')).toBe(ROUTES.CHAT)
  })

  it('redirects ROLE_USER from the legacy dashboard profile to the chat profile', () => {
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/dashboard/profile')).toBe(ROUTES.CHAT_PROFILE)
  })

  it('does not redirect ROLE_ADMIN on any /dashboard path', () => {
    expect(getDashboardGuardRedirect(['ROLE_ADMIN'], '/dashboard/applications/agents')).toBeNull()
    expect(getDashboardGuardRedirect(['ROLE_ADMIN'], '/dashboard/control/users')).toBeNull()
    expect(getDashboardGuardRedirect(['ROLE_ADMIN'], '/dashboard/profile')).toBeNull()
  })
})
