import { describe, expect, it } from 'vitest'
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
    expect(getActiveMenuId('/dashboard/applications/mcp')).toBe('agents')
    expect(getActiveMenuId('/dashboard/runtime/overview')).toBe('monitor')
    expect(getActiveMenuId('/dashboard/control/users')).toBe('organization')
  })
})
