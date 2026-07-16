import { describe, expect, it } from 'vitest'
import latencySource from '@/views/Monitor/LatencyStats.vue?raw'

describe('LatencyStats workbench surface', () => {
  it('does not nest a table surface inside an outer history card', () => {
    expect(latencySource).toContain('<OrinDataTable compact class="latency-panel history-table">')
    expect(latencySource).not.toContain('class="latency-panel history-card"')
    expect(latencySource).not.toContain('<OrinDataTable compact>\n      <el-table')
  })

  it('keeps date filtering and pagination in the same trace-sample surface', () => {
    expect(latencySource).toContain('@change="handleDateRangeChange"')
    expect(latencySource).toContain('@size-change="fetchHistoryData"')
    expect(latencySource).toContain('@current-change="fetchHistoryData"')
    expect(latencySource).toContain('共 {{ total }} 条样本')
  })
})
