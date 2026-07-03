import { describe, expect, it } from 'vitest'
import { ROUTES } from '@/router/routes'
import { getActiveMenuId, getDefaultHomeByRoles, getVisibleMenus } from '@/router/topMenuConfig'

describe('top menu IA behavior', () => {
  it('filters admin-only system domain for non-admin users', () => {
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])
    const userMenus = getVisibleMenus(['ROLE_USER'])

    expect(adminMenus.some((menu) => menu.id === 'control')).toBe(true)
    expect(userMenus).toEqual([])
  })

  it('keeps API key self-service outside the admin control console', () => {
    const userMenus = getVisibleMenus(['ROLE_USER'])
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])

    expect(ROUTES.PORTAL_API_KEYS).toBe('/portal/api-keys')
    expect(userMenus).toEqual([])
    expect(adminMenus.find((menu) => menu.id === 'control').children).toContainEqual(expect.objectContaining({
      title: '统一网关',
      path: ROUTES.SYSTEM.GATEWAY,
    }))
  })

  it('routes each role cohort to its default landing page', () => {
    expect(getDefaultHomeByRoles(['ROLE_ADMIN'])).toBe(ROUTES.SYSTEM.ADMIN_DASHBOARD)
    expect(getDefaultHomeByRoles(['ROLE_USER'])).toBe(ROUTES.PORTAL_API_KEYS)
  })

  it('keeps regular users out of dashboard navigation', () => {
    expect(getVisibleMenus(['ROLE_USER'])).toEqual([])
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
})
