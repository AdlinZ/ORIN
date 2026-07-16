import { describe, expect, it } from 'vitest'
import roleManagementSource from '@/views/System/RoleManagement.vue?raw'

describe('RoleManagement collection surface', () => {
  it('keeps a single table surface with a compact collection workbar', () => {
    expect(roleManagementSource).toContain('<OrinPageShell')
    expect(roleManagementSource).toContain('class="role-toolbar"')
    expect(roleManagementSource).toContain('<OrinDataTable compact>')
    expect(roleManagementSource).not.toContain('OrinStatusSummary')
    expect(roleManagementSource).not.toContain('premium-card')
    expect(roleManagementSource).not.toContain('<el-card')
  })

  it('uses the server-filtered total and protects every built-in role in the UI', () => {
    expect(roleManagementSource).toContain('totalRoles.value = res.total || 0')
    expect(roleManagementSource).toContain('totalRoles.value > pageSize.value')
    for (const roleCode of [
      'ROLE_ADMIN',
      'ROLE_USER',
      'ROLE_OPERATOR',
      'ROLE_PLATFORM_ADMIN',
      'ROLE_SUPER_ADMIN'
    ]) {
      expect(roleManagementSource).toContain(`'${roleCode}'`)
    }
  })

  it('debounces the search request and sends the complete pagination contract', () => {
    expect(roleManagementSource).toContain('searchTimer = window.setTimeout(loadRoles, 250)')
    expect(roleManagementSource).toContain('page: currentPage.value - 1')
    expect(roleManagementSource).toContain('size: pageSize.value')
    expect(roleManagementSource).toContain('search: searchQuery.value')
  })
})
