import { describe, expect, it } from 'vitest'
import { ROUTES } from '@/router/routes'
import { getActiveMenuId, getDefaultHomeByRoles, getVisibleMenus } from '@/router/topMenuConfig'

describe('top menu IA behavior', () => {
  it('filters admin-only system domain for non-admin users', () => {
    const nonAdminMenus = getVisibleMenus(['ROLE_OPERATOR'])
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])
    const userMenus = getVisibleMenus(['ROLE_USER'])

    expect(nonAdminMenus.some((menu) => menu.id === 'system')).toBe(false)
    expect(adminMenus.some((menu) => menu.id === 'system')).toBe(true)
    expect(userMenus).toEqual([])
  })

  it('keeps API key self-service outside the admin control console', () => {
    const userMenus = getVisibleMenus(['ROLE_USER'])
    const operatorMenus = getVisibleMenus(['ROLE_OPERATOR'])
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])

    expect(ROUTES.PORTAL_API_KEYS).toBe('/portal/api-keys')
    expect(userMenus).toEqual([])
    expect(operatorMenus.some((menu) => menu.id === 'system')).toBe(false)
    expect(adminMenus.find((menu) => menu.id === 'system').children).toContainEqual(expect.objectContaining({
      title: '统一网关',
      path: ROUTES.SYSTEM.GATEWAY,
    }))
  })

  it('routes each role cohort to its default landing page', () => {
    expect(getDefaultHomeByRoles(['ROLE_SUPER_ADMIN'])).toBe(ROUTES.HOME)
    expect(getDefaultHomeByRoles(['ROLE_PLATFORM_ADMIN'])).toBe(ROUTES.HOME)
    expect(getDefaultHomeByRoles(['ROLE_ADMIN'])).toBe(ROUTES.HOME)
    expect(getDefaultHomeByRoles(['ROLE_OPERATOR'])).toBe(ROUTES.AGENTS.LIST)
    expect(getDefaultHomeByRoles(['ROLE_USER'])).toBe(ROUTES.PORTAL)
  })

  it('keeps operator navigation focused on execution workspaces', () => {
    const operatorMenus = getVisibleMenus(['ROLE_OPERATOR'])

    expect(operatorMenus.map((menu) => menu.id)).toEqual(['agents', 'workflows', 'knowledge'])
    expect(operatorMenus.flatMap((menu) => menu.children).every((child) => child.roles.includes('ROLE_OPERATOR'))).toBe(true)
  })

  it('uses direct management-oriented menu labels', () => {
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])

    expect(adminMenus.map((menu) => menu.title)).toEqual([
      '智能体管理',
      '工作流管理',
      '知识库管理',
      '运行监控',
      '组织权限',
      '系统设置',
    ])

    const systemMenu = adminMenus.find((menu) => menu.id === 'system')
    expect(systemMenu.children.map((child) => child.title)).toContain('统一网关')
    expect(systemMenu.children.map((child) => child.title)).toContain('环境配置')
  })

  it('matches active top-level domain by route path', () => {
    expect(getActiveMenuId('/dashboard/applications/agents')).toBe('agents')
    expect(getActiveMenuId(ROUTES.MCP.SERVERS)).toBe('agents')
    expect(getActiveMenuId('/dashboard/runtime/overview')).toBe('monitor')
    expect(getActiveMenuId('/dashboard/control/users')).toBe('organization')
  })

  it('points workflow management to the V1 fallback entries', () => {
    const workflowMenu = getVisibleMenus(['ROLE_ADMIN']).find((menu) => menu.id === 'workflows')

    expect(workflowMenu).toMatchObject({ path: ROUTES.AGENTS.WORKFLOWS })
    expect(workflowMenu.children).toEqual([
      expect.objectContaining({ title: '工作流中心', path: ROUTES.AGENTS.WORKFLOWS, icon: 'Connection' }),
      expect.objectContaining({ title: '可视化编排', path: ROUTES.AGENTS.WORKFLOW_VISUAL, icon: 'Edit' }),
      expect.objectContaining({ title: '执行记录', path: ROUTES.AGENTS.WORKFLOW_EXECUTION, icon: 'VideoPlay' }),
    ])
  })

  it('keeps multi-agent collaboration visible under agent management', () => {
    const agentMenu = getVisibleMenus(['ROLE_ADMIN']).find((menu) => menu.id === 'agents')

    expect(agentMenu.children).toContainEqual(expect.objectContaining({
      title: '多智能体协同',
      path: ROUTES.AGENTS.COLLABORATION_WORKFLOWS,
      icon: 'Connection',
    }))
  })

  it('keeps MCP service management inside the extensions tab instead of a separate agent menu item', () => {
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])
    const agentMenu = adminMenus.find((menu) => menu.id === 'agents')
    const systemMenu = adminMenus.find((menu) => menu.id === 'system')

    expect(ROUTES.MCP.SERVERS).toBe('/dashboard/applications/extensions?tab=mcp')
    expect(agentMenu.children).not.toContainEqual(expect.objectContaining({
      title: 'MCP 服务',
      path: ROUTES.MCP.SERVERS,
      icon: 'Connection',
    }))
    expect(agentMenu.children).toContainEqual(expect.objectContaining({
      title: '扩展管理',
      path: ROUTES.AGENTS.EXTENSIONS,
      icon: 'Star',
    }))
    expect(systemMenu.children).toContainEqual(expect.objectContaining({
      title: 'MCP 服务',
      path: ROUTES.SYSTEM.SETTINGS_MCP_SERVICE,
      icon: 'Connection',
    }))
  })
})
