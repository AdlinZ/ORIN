import { describe, expect, it } from 'vitest'
import { getActiveMenuId, getVisibleMenus } from '@/router/topMenuConfig'

describe('top menu IA behavior', () => {
  it('filters admin-only system domain for non-admin users', () => {
    const nonAdminMenus = getVisibleMenus(false)
    const adminMenus = getVisibleMenus(true)

    expect(nonAdminMenus.some((menu) => menu.id === 'system')).toBe(false)
    expect(adminMenus.some((menu) => menu.id === 'system')).toBe(true)
  })

  it('matches active top-level domain by route path', () => {
    expect(getActiveMenuId('/dashboard/applications/agents')).toBe('agents')
    expect(getActiveMenuId('/dashboard/runtime/overview')).toBe('monitor')
    expect(getActiveMenuId('/dashboard/control/users')).toBe('system')
    expect(getActiveMenuId('/dashboard/control/revamp-rollout')).toBe('system')
  })
})
