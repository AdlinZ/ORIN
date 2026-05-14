import { describe, expect, it } from 'vitest'
import { ROUTES } from '@/router/routes'
import { getActiveMenuId, getVisibleMenus } from '@/router/topMenuConfig'

describe('top menu IA behavior', () => {
  it('filters admin-only system domain for non-admin users', () => {
    const nonAdminMenus = getVisibleMenus([])
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])

    expect(nonAdminMenus.some((menu) => menu.id === 'system')).toBe(false)
    expect(adminMenus.some((menu) => menu.id === 'system')).toBe(true)
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

    expect(workflowMenu.path).toBe(ROUTES.AGENTS.WORKFLOWS)
    expect(workflowMenu.children).toEqual([
      { title: '工作流中心', path: ROUTES.AGENTS.WORKFLOWS, icon: 'Connection' },
      { title: '可视化编排', path: ROUTES.AGENTS.WORKFLOW_VISUAL, icon: 'Edit' },
      { title: '执行记录', path: ROUTES.AGENTS.WORKFLOW_EXECUTION, icon: 'VideoPlay' },
    ])
  })

  it('keeps multi-agent collaboration visible under agent management', () => {
    const agentMenu = getVisibleMenus(['ROLE_ADMIN']).find((menu) => menu.id === 'agents')

    expect(agentMenu.children).toContainEqual({
      title: '多智能体协同',
      path: ROUTES.AGENTS.COLLABORATION_WORKFLOWS,
      icon: 'Connection',
    })
  })

  it('keeps MCP service management inside the extensions tab instead of a separate agent menu item', () => {
    const adminMenus = getVisibleMenus(['ROLE_ADMIN'])
    const agentMenu = adminMenus.find((menu) => menu.id === 'agents')
    const systemMenu = adminMenus.find((menu) => menu.id === 'system')

    expect(ROUTES.MCP.SERVERS).toBe('/dashboard/applications/extensions?tab=mcp')
    expect(agentMenu.children).not.toContainEqual({
      title: 'MCP 服务',
      path: ROUTES.MCP.SERVERS,
      icon: 'Connection',
    })
    expect(agentMenu.children).toContainEqual({
      title: '扩展管理',
      path: ROUTES.AGENTS.EXTENSIONS,
      icon: 'Star',
    })
    expect(systemMenu.children).toContainEqual({
      title: 'MCP 服务',
      path: ROUTES.SYSTEM.SETTINGS_MCP_SERVICE,
      icon: 'Connection',
    })
  })
})
