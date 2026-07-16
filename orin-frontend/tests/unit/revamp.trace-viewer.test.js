import { describe, expect, it } from 'vitest'
import traceViewerSource from '@/views/Trace/TraceViewer.vue?raw'

describe('TraceViewer collection entry', () => {
  it('keeps trace search in the page workbar and the recent list in one table surface', () => {
    expect(traceViewerSource).toContain('<template #filters>')
    expect(traceViewerSource).toContain('placeholder="输入 traceId 搜索调用链路"')
    expect(traceViewerSource).toContain('<OrinDataTable v-if="!activeTraceId" compact class="table-card">')
    expect(traceViewerSource).not.toContain('<el-card class="search-card"')
    expect(traceViewerSource).not.toContain('<el-card v-if="!activeTraceId" class="table-card">')
  })

  it('retains the trace detail workbench after a result is selected', () => {
    expect(traceViewerSource).toContain('<template v-else>')
    expect(traceViewerSource).toContain('当前 Trace ID')
    expect(traceViewerSource).toContain('activeDetailTab')
  })
})
