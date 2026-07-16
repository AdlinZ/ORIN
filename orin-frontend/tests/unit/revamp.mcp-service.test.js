import { describe, expect, it } from 'vitest'
import mcpServiceSource from '@/views/System/McpService.vue?raw'

describe('McpService collection surface', () => {
  it('uses one collection surface with the workbar owned by each host mode', () => {
    expect(mcpServiceSource).toContain('<template #filters>')
    expect(mcpServiceSource).toContain('class="mcp-collection-workbar"')
    expect(mcpServiceSource).toContain('class="mcp-collection-workbar embedded-toolbar"')
    expect(mcpServiceSource.match(/<OrinDataTable/g)).toHaveLength(1)
    expect(mcpServiceSource).not.toContain('<el-card')
    expect(mcpServiceSource).not.toContain('embedded-stats')
    expect(mcpServiceSource).not.toContain('tab-wrapper-card')
  })

  it('preserves the service and market tabs plus standalone table and embedded cards', () => {
    expect(mcpServiceSource).toContain('<el-tab-pane label="服务列表" name="list">')
    expect(mcpServiceSource).toContain('<el-tab-pane label="工具市场" name="market"')
    expect(mcpServiceSource).toContain('<div v-if="embedded" class="service-card-grid">')
    expect(mcpServiceSource).toContain('<template v-else>')
    expect(mcpServiceSource).toContain('<el-table')
    expect(mcpServiceSource.match(/@click="openAddDialog"/g)).toHaveLength(2)
  })

  it('uses the complete server-filtered list instead of presenting local pagination as a total', () => {
    expect(mcpServiceSource).toContain("keyword: searchQuery.value.trim() || undefined")
    expect(mcpServiceSource).toContain('Number.isFinite(Number(res?.total))')
    expect(mcpServiceSource).toContain(': rows.length')
    expect(mcpServiceSource).toContain('searchTimer = window.setTimeout(loadMcpServices, 250)')
    expect(mcpServiceSource).not.toContain('<el-pagination')
    expect(mcpServiceSource).not.toContain('currentPage')
    expect(mcpServiceSource).not.toContain('pageSize')
  })
})
