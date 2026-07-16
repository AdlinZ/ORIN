import { describe, expect, it } from 'vitest'
import maintenanceSource from '@/views/System/SystemMaintenance.vue?raw'

describe('SystemMaintenance workbench surface', () => {
  it('uses lightweight status and operation panels instead of nested element cards', () => {
    expect(maintenanceSource).toContain('class="maintenance-grid"')
    expect(maintenanceSource).toContain('class="maintenance-panel operations-panel"')
    expect(maintenanceSource).toContain('<OrinDataTable compact class="maintenance-log-table">')
    expect(maintenanceSource).not.toContain('operations-card')
    expect(maintenanceSource).not.toContain('log-card')
  })

  it('keeps destructive maintenance actions available from the operation panel', () => {
    expect(maintenanceSource).toContain('@click="openRestoreDialog"')
    expect(maintenanceSource).toContain('@click="openCacheDialog"')
    expect(maintenanceSource).toContain('@click="openHealthCheck"')
  })
})
