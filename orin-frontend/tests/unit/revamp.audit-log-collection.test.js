import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import ErrorStats from '@/views/Monitor/ErrorStats.vue'
import LogArchive from '@/views/Monitor/LogArchive.vue'

const projectRoot = path.resolve(__dirname, '../..')

const read = (file) => fs.readFileSync(path.join(projectRoot, file), 'utf8')

describe('audit and log collection layout', () => {
  it('compiles the admin runtime collection pages', () => {
    expect(ErrorStats).toBeTruthy()
    expect(LogArchive).toBeTruthy()
  })

  it('keeps audit navigation in the page-shell workbar without summary cards', () => {
    const source = read('src/views/revamp/system/AuditCenterV2.vue')

    expect(source).toContain('<template #filters>')
    expect(source).toContain('class="audit-workbar"')
    expect(source).toContain('class="audit-content-surface audit-config-surface"')
    expect(source).not.toContain('OrinStatusSummary')
    expect(source).not.toContain('OrinDetailPanel')
    expect(source).not.toContain('<el-card')
  })

  it('uses one table surface for log archive filters, rows, and pagination', () => {
    const source = read('src/views/Monitor/LogArchive.vue')

    expect(source).toContain('<template #filters>')
    expect(source).toContain('class="log-workbar"')
    expect(source.match(/<OrinDataTable/g)).toHaveLength(1)
    expect(source).not.toContain('<el-card')
    expect(source).toContain('getGatewayAuditLogs')
  })

  it('uses one error workspace with bare data tables instead of nested cards', () => {
    const source = read('src/views/Monitor/ErrorStats.vue')

    expect(source).toContain('class="error-workbar"')
    expect(source).toContain('class="error-workspace"')
    expect(source.match(/surface="bare"/g)).toHaveLength(2)
    expect(source).not.toContain('<el-card')
    expect(source).toContain('getErrorDistribution')
  })
})
