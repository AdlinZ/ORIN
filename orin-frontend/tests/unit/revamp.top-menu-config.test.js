import { describe, expect, it } from 'vitest'
import { ROUTES } from '@/router/routes'
import { getActiveMenuId, getDefaultHomeByRoles, getVisibleMenus } from '@/router/topMenuConfig'

describe('top menu IA behavior (Workspace vNext 四入口)', () => {
  it('filters legacy admin sections for non-admin users', () => {
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])
    const userMenus = getVisibleMenus(['ROLE_USER'])

    // Admins see the full menu including legacy
    expect(adminMenus.some((menu) => menu.id === 'legacy')).toBe(true)
    // Regular users with only ROLE_USER have no dashboard menu access
    expect(userMenus).toEqual([])
  })

  it('keeps API key self-service outside the admin dashboard', () => {
    const userMenus = getVisibleMenus(['ROLE_USER'])
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])

    expect(ROUTES.PORTAL_API_KEYS).toBe('/portal/api-keys')
    expect(userMenus).toEqual([])
    // Gateway is now under "更多工具" (legacy)
    const legacyMenu = adminMenus.find((menu) => menu.id === 'legacy')
    expect(legacyMenu.children).toContainEqual(expect.objectContaining({
      title: '统一网关',
      path: ROUTES.SYSTEM.GATEWAY,
    }))
  })

  it('routes each role cohort to its default landing page', () => {
    // vNext: all roles land on the workspace home (Agents)
    expect(getDefaultHomeByRoles(['ROLE_ADMIN'])).toBe(ROUTES.HOME)
    expect(getDefaultHomeByRoles(['ROLE_USER'])).toBe(ROUTES.HOME)
  })

  it('keeps regular users out of dashboard navigation', () => {
    expect(getVisibleMenus(['ROLE_USER'])).toEqual([])
  })

  it('shows the complete workspace-first menu for admins', () => {
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])

    expect(adminMenus.map((menu) => menu.title)).toEqual([
      'Agents',
      'Runners',
      'Runs',
      'Endpoints',
      '更多工具',
    ])

    // Workspace entries
    expect(adminMenus.some((menu) => menu.id === 'agents')).toBe(true)
    expect(adminMenus.some((menu) => menu.id === 'runners')).toBe(true)
    expect(adminMenus.some((menu) => menu.id === 'runs')).toBe(true)
    expect(adminMenus.some((menu) => menu.id === 'endpoints')).toBe(true)

    // Legacy menu still contains essential admin tools
    const legacyMenu = adminMenus.find((menu) => menu.id === 'legacy')
    expect(legacyMenu.children.map((child) => {
      if (child.type === 'section') return child.title
      return child.title
    })).toContain('用户管理')
  })

  it('does not expose role management while the role model is simplified', () => {
    const legacyMenu = getVisibleMenus(['ROLE_ADMIN']).find((menu) => menu.id === 'legacy')

    expect(legacyMenu.children).not.toContainEqual(expect.objectContaining({
      title: '角色管理',
      path: ROUTES.SYSTEM.ROLES,
    }))
  })

  it('keeps section headers only when they have visible children', () => {
    const legacyMenu = getVisibleMenus(['ROLE_ADMIN']).find((menu) => menu.id === 'legacy')

    expect(legacyMenu.children).toContainEqual(expect.objectContaining({
      type: 'section',
      title: '智能体（旧版）',
    }))
    expect(legacyMenu.children).toContainEqual(expect.objectContaining({
      type: 'section',
      title: '工作流',
    }))
  })

  it('matches active top-level domain by route path', () => {
    // /workspace paths map to the new four entries
    expect(getActiveMenuId(ROUTES.WORKSPACE.AGENTS)).toBe('agents')
    expect(getActiveMenuId(ROUTES.WORKSPACE.RUNNERS)).toBe('runners')
    expect(getActiveMenuId(ROUTES.WORKSPACE.RUNS)).toBe('runs')
    expect(getActiveMenuId(ROUTES.WORKSPACE.ENDPOINTS)).toBe('endpoints')
    // Legacy paths still map to "更多工具"
    expect(getActiveMenuId('/dashboard/runtime/overview')).toBe('legacy')
    expect(getActiveMenuId('/dashboard/control/users')).toBe('legacy')
  })

  it('keeps workflow management under legacy tools', () => {
    const legacyMenu = getVisibleMenus(['ROLE_ADMIN']).find((menu) => menu.id === 'legacy')

    expect(legacyMenu.children).toContainEqual(expect.objectContaining({
      title: '工作流中心',
      path: ROUTES.AGENTS.WORKFLOWS,
      icon: 'Connection',
    }))
    expect(legacyMenu.children).toContainEqual(expect.objectContaining({
      title: '执行记录',
      path: ROUTES.AGENTS.WORKFLOW_EXECUTION,
      icon: 'VideoPlay',
    }))
  })

  it('exposes extension tabs in legacy tools while keeping platform MCP config', () => {
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])
    const legacyMenu = adminMenus.find((menu) => menu.id === 'legacy')

    expect(ROUTES.MCP.SERVERS).toBe('/dashboard/applications/extensions?tab=mcp')
    expect(ROUTES.AGENTS.SKILLS).toBe('/dashboard/applications/extensions?tab=skills')
    expect(ROUTES.AGENTS.MODEL_TOOLS).toBe('/dashboard/applications/extensions?tab=bindings')
    expect(legacyMenu.children).toContainEqual(expect.objectContaining({
      title: 'Skills',
      path: ROUTES.AGENTS.SKILLS,
      icon: 'Star',
    }))
    expect(legacyMenu.children).toContainEqual(expect.objectContaining({
      title: 'MCP 服务',
      path: ROUTES.AGENTS.MCP,
      icon: 'Connection',
    }))
    expect(legacyMenu.children).toContainEqual(expect.objectContaining({
      title: '用户管理',
      path: ROUTES.SYSTEM.USERS,
    }))
  })
})
