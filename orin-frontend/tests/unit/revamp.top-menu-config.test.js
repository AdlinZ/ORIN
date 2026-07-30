import { describe, expect, it } from 'vitest'
import { ROUTES } from '@/router/routes'
import { getActiveMenuId, getDefaultHomeByRoles, getVisibleMenus } from '@/router/topMenuConfig'

describe('top menu IA behavior (概览 → Agent → 运行 → 发布 → 资源 → 系统设置)', () => {
  it('keeps dashboard navigation admin-only', () => {
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])
    const userMenus = getVisibleMenus(['ROLE_USER'])

    expect(adminMenus).toHaveLength(6)
    expect(userMenus).toEqual([])
  })

  it('keeps API key self-service outside the admin dashboard', () => {
    const userMenus = getVisibleMenus(['ROLE_USER'])
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])

    expect(ROUTES.PORTAL_API_KEYS).toBe('/portal/api-keys')
    expect(userMenus).toEqual([])
    const publishMenu = adminMenus.find((menu) => menu.id === 'publish')
    expect(publishMenu.children).not.toContainEqual(expect.objectContaining({
      path: ROUTES.SYSTEM.GATEWAY,
    }))
    expect(ROUTES.SYSTEM.API_KEYS).toContain('/dashboard/control/gateway')
  })

  it('routes each role cohort to its default landing page', () => {
    expect(getDefaultHomeByRoles(['ROLE_ADMIN'])).toBe(ROUTES.HOME)
    expect(getDefaultHomeByRoles(['ROLE_USER'])).toBe(ROUTES.PORTAL_API_KEYS)
    expect(getDefaultHomeByRoles(['ROLE_ADMIN', 'ROLE_USER'])).toBe(ROUTES.HOME)
  })

  it('keeps regular users out of dashboard navigation', () => {
    expect(getVisibleMenus(['ROLE_USER'])).toEqual([])
  })

  it('shows the reduced product journey for admins', () => {
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])

    expect(adminMenus.map((menu) => menu.title)).toEqual([
      '概览',
      'Agent',
      '运行',
      '发布',
      '资源',
      '系统设置',
    ])

    expect(adminMenus.flatMap((menu) => menu.children)).toHaveLength(9)
  })

  it('makes single-destination domains direct links instead of fake dropdowns', () => {
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])

    for (const menuId of ['overview', 'runs', 'publish']) {
      const menu = adminMenus.find((item) => item.id === menuId)
      expect(menu.path).toBeTruthy()
      expect(menu.direct).toBe(true)
      expect(menu.children).toEqual([])
    }

    for (const menuId of ['agents', 'resources', 'settings']) {
      expect(adminMenus.find((item) => item.id === menuId).children.length).toBeGreaterThan(1)
    }
  })

  it('does not expose role management while the role model is simplified', () => {
    const settingsMenu = getVisibleMenus(['ROLE_ADMIN'])
      .find((menu) => menu.id === 'settings')

    expect(settingsMenu.children).not.toContainEqual(expect.objectContaining({
      title: '角色管理',
      path: ROUTES.SYSTEM.ROLES,
    }))
  })

  it('only exposes entries needed by the core product loop', () => {
    const visibleTitles = getVisibleMenus(['ROLE_ADMIN'])
      .flatMap((menu) => menu.children)
      .map((child) => child.title)

    expect(visibleTitles).toEqual([
      'Agent 列表',
      '工作流',
      'Runner',
      '模型',
      '知识库',
      'MCP 工具',
      '系统概览',
      '用户管理',
      '审计记录',
    ])
    expect(visibleTitles).not.toContain('调用链路')
    expect(visibleTitles).not.toContain('访问密钥与网关')
    expect(visibleTitles).not.toContain('Skills')
    expect(visibleTitles).not.toContain('定价配置')
    expect(visibleTitles).not.toContain('会话记录')
  })

  it('matches active top-level domain by route path', () => {
    expect(getActiveMenuId(ROUTES.WORKSPACE.OVERVIEW)).toBe('overview')
    expect(getActiveMenuId(ROUTES.WORKSPACE.AGENTS)).toBe('agents')
    expect(getActiveMenuId(ROUTES.AGENTS.WORKFLOWS)).toBe('agents')
    expect(getActiveMenuId(ROUTES.WORKSPACE.RUNNERS)).toBe('resources')
    expect(getActiveMenuId(ROUTES.AGENTS.MCP)).toBe('resources')
    expect(getActiveMenuId(ROUTES.WORKSPACE.RUNS)).toBe('runs')
    expect(getActiveMenuId(ROUTES.MONITOR.TRACES)).toBe('runs')
    expect(getActiveMenuId(ROUTES.WORKSPACE.ENDPOINTS)).toBe('publish')
    expect(getActiveMenuId(ROUTES.SYSTEM.GATEWAY)).toBe('publish')
    expect(getActiveMenuId(ROUTES.SYSTEM.USERS)).toBe('settings')
    expect(getActiveMenuId(ROUTES.SYSTEM.SETTINGS_MONITOR)).toBe('settings')
    expect(getActiveMenuId(ROUTES.SYSTEM.AUDIT_SETTINGS)).toBe('settings')
  })

  it('keeps workflows with Agent and moves technical dependencies into resources', () => {
    const agentMenu = getVisibleMenus(['ROLE_ADMIN']).find((menu) => menu.id === 'agents')
    const resourcesMenu = getVisibleMenus(['ROLE_ADMIN']).find((menu) => menu.id === 'resources')

    expect(agentMenu.children).toContainEqual(expect.objectContaining({
      title: '工作流',
      path: ROUTES.AGENTS.WORKFLOWS,
      icon: 'Connection',
    }))
    expect(resourcesMenu.children).toContainEqual(expect.objectContaining({
      title: 'MCP 工具',
      path: ROUTES.AGENTS.MCP,
      icon: 'SetUp',
    }))
  })
})
