import { describe, expect, it } from 'vitest'
import { ROUTES } from '@/router/routes'
import {
  getActiveMenuId,
  getDashboardGuardRedirect,
  getDefaultHomeByRoles,
  getVisibleMenus
} from '@/router/topMenuConfig'

describe('top menu IA behavior', () => {
  it('filters admin-only system domain for non-admin users', () => {
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])
    const userMenus = getVisibleMenus(['ROLE_USER'])

    expect(adminMenus.some((menu) => menu.id === 'control')).toBe(true)
    expect(userMenus.map((menu) => menu.id)).toEqual(['chat'])
  })

  it('keeps API key self-service outside the admin control console', () => {
    const userMenus = getVisibleMenus(['ROLE_USER'])
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])

    expect(ROUTES.CHAT).toBe('/chat')
    expect(ROUTES.PLATFORM).toBe('/platform')
    expect(ROUTES.PORTAL_API_KEYS).toBe('/platform/api-keys')
    expect(userMenus.map((menu) => menu.id)).toEqual(['chat'])
    expect(adminMenus.find((menu) => menu.id === 'control').children).toContainEqual(expect.objectContaining({
      title: '统一网关',
      path: ROUTES.SYSTEM.GATEWAY,
    }))
  })

  it('routes each role cohort to its default landing page', () => {
    expect(getDefaultHomeByRoles(['ROLE_ADMIN'])).toBe(ROUTES.SYSTEM.ADMIN_DASHBOARD)
    expect(getDefaultHomeByRoles(['ROLE_USER'])).toBe(ROUTES.CHAT)
  })

  it('keeps regular users out of dashboard navigation', () => {
    const userMenus = getVisibleMenus(['ROLE_USER'])
    expect(userMenus).toHaveLength(1)
    expect(userMenus[0]).toMatchObject({ id: 'chat', path: ROUTES.CHAT })
    expect(userMenus[0].children).toContainEqual(expect.objectContaining({
      title: 'ORIN Chat',
      path: ROUTES.CHAT
    }))
  })

  it('shows the complete dashboard menu for admins', () => {
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])

    expect(adminMenus.map((menu) => menu.title)).toEqual([
      '平台控制',
      '运行观测',
      '应用构建',
      '资源知识',
    ])

    const controlMenu = adminMenus.find((menu) => menu.id === 'control')
    expect(controlMenu.children.map((child) => child.title)).toContain('统一网关')
    expect(controlMenu.children.map((child) => child.title)).toContain('环境配置')
  })

  it('does not expose role management while the role model is simplified', () => {
    const controlMenu = getVisibleMenus(['ROLE_ADMIN']).find((menu) => menu.id === 'control')

    expect(controlMenu.children).not.toContainEqual(expect.objectContaining({
      title: '角色管理',
      path: ROUTES.SYSTEM.ROLES,
    }))
  })

  it('keeps section headers only when they have visible children', () => {
    const controlMenu = getVisibleMenus(['ROLE_ADMIN']).find((menu) => menu.id === 'control')

    expect(controlMenu.children).toContainEqual(expect.objectContaining({
      type: 'section',
      title: '组织权限',
    }))
    expect(controlMenu.children).toContainEqual(expect.objectContaining({
      type: 'section',
      title: '平台配置',
    }))
  })

  it('matches active top-level domain by route path', () => {
    expect(getActiveMenuId('/dashboard/applications/agents')).toBe('applications')
    expect(getActiveMenuId(ROUTES.MCP.SERVERS)).toBe('applications')
    expect(getActiveMenuId('/dashboard/runtime/overview')).toBe('runtime')
    expect(getActiveMenuId('/dashboard/control/users')).toBe('control')
  })

  it('keeps workflow management under application building', () => {
    const applicationMenu = getVisibleMenus(['ROLE_ADMIN']).find((menu) => menu.id === 'applications')

    expect(applicationMenu).toMatchObject({ path: ROUTES.AGENTS.ROOT })
    expect(applicationMenu.children).toContainEqual(expect.objectContaining({ title: '工作流中心', path: ROUTES.AGENTS.WORKFLOWS, icon: 'Connection' }))
    expect(applicationMenu.children).toContainEqual(expect.objectContaining({ title: '可视化编排', path: ROUTES.AGENTS.WORKFLOW_VISUAL, icon: 'Edit' }))
    expect(applicationMenu.children).toContainEqual(expect.objectContaining({ title: '执行记录', path: ROUTES.AGENTS.WORKFLOW_EXECUTION, icon: 'VideoPlay' }))
  })

  it('keeps multi-agent collaboration visible under application building', () => {
    const applicationMenu = getVisibleMenus(['ROLE_ADMIN']).find((menu) => menu.id === 'applications')

    expect(applicationMenu.children).toContainEqual(expect.objectContaining({
      title: '多智能体协同',
      path: ROUTES.AGENTS.COLLABORATION_WORKFLOWS,
      icon: 'Connection',
    }))
  })

  it('exposes extension tabs as navigation entries while keeping platform MCP configuration', () => {
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])
    const applicationMenu = adminMenus.find((menu) => menu.id === 'applications')
    const controlMenu = adminMenus.find((menu) => menu.id === 'control')

    expect(ROUTES.MCP.SERVERS).toBe('/dashboard/applications/extensions?tab=mcp')
    expect(ROUTES.AGENTS.SKILLS).toBe('/dashboard/applications/extensions?tab=skills')
    expect(ROUTES.AGENTS.MODEL_TOOLS).toBe('/dashboard/applications/extensions?tab=bindings')
    expect(applicationMenu.children).toContainEqual(expect.objectContaining({
      title: 'Skills',
      path: ROUTES.AGENTS.SKILLS,
      icon: 'Star',
    }))
    expect(applicationMenu.children).toContainEqual(expect.objectContaining({
      title: 'MCP 服务',
      path: ROUTES.MCP.SERVERS,
      icon: 'Connection',
    }))
    expect(applicationMenu.children).toContainEqual(expect.objectContaining({
      title: '模型工具',
      path: ROUTES.AGENTS.MODEL_TOOLS,
      icon: 'Setting',
    }))
    expect(controlMenu.children).toContainEqual(expect.objectContaining({
      title: 'MCP 服务',
      path: ROUTES.SYSTEM.SETTINGS_MCP_SERVICE,
      icon: 'Connection',
    }))
  })

  it('exposes a dedicated chat entry for ROLE_USER with /chat as the only child', () => {
    const userMenus = getVisibleMenus(['ROLE_USER'])
    expect(userMenus).toHaveLength(1)
    const chatMenu = userMenus[0]
    expect(chatMenu).toMatchObject({
      id: 'chat',
      path: ROUTES.CHAT,
      title: '对话'
    })
    expect(chatMenu.children).toEqual([
      expect.objectContaining({ title: 'ORIN Chat', path: ROUTES.CHAT })
    ])
  })

  it('keeps the chat entry out of the admin top menu', () => {
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])
    expect(adminMenus.some((menu) => menu.id === 'chat')).toBe(false)
  })

  it('redirects ROLE_USER away from /dashboard/* to /chat via the guard helper', () => {
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/dashboard')).toBe(ROUTES.CHAT)
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/dashboard/applications/agents')).toBe(ROUTES.CHAT)
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/dashboard/runtime/overview')).toBe(ROUTES.CHAT)
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/dashboard/control/users')).toBe(ROUTES.CHAT)
  })

  it('lets ROLE_USER stay on /dashboard/profile (shared page)', () => {
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/dashboard/profile')).toBeNull()
  })

  it('does not redirect ROLE_ADMIN away from any /dashboard/* page', () => {
    expect(getDashboardGuardRedirect(['ROLE_ADMIN'], '/dashboard')).toBeNull()
    expect(getDashboardGuardRedirect(['ROLE_ADMIN'], '/dashboard/applications/agents')).toBeNull()
    expect(getDashboardGuardRedirect(['ROLE_ADMIN'], '/dashboard/control/users')).toBeNull()
  })

  it('does not redirect non-dashboard paths', () => {
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/chat')).toBeNull()
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/platform')).toBeNull()
    expect(getDashboardGuardRedirect(['ROLE_USER'], '/')).toBeNull()
  })

  it('handles empty / undefined inputs safely', () => {
    expect(getDashboardGuardRedirect([], '/dashboard/applications/agents')).toBe(ROUTES.CHAT)
    expect(getDashboardGuardRedirect(['ROLE_USER'], '')).toBeNull()
    expect(getDashboardGuardRedirect(undefined, '/dashboard/applications/agents')).toBe(ROUTES.CHAT)
  })
})
