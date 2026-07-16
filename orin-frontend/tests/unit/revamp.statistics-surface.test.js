import { describe, expect, it } from 'vitest'
import statisticsSource from '@/views/System/Statistics.vue?raw'

describe('Statistics analysis surface', () => {
  it('uses a compact workbar and lightweight metric/chart panels', () => {
    expect(statisticsSource).toContain('class="statistics-workbar"')
    expect(statisticsSource).toContain('class="stat-card"')
    expect(statisticsSource).toContain('class="analysis-panel"')
    expect(statisticsSource).not.toContain('<el-card')
  })

  it('uses each data table as its own surface', () => {
    expect(statisticsSource).toContain('<OrinDataTable compact class="statistics-table">')
    expect(statisticsSource).not.toContain('<OrinDataTable compact>\n              <el-table')
    expect(statisticsSource).toContain('任务状态分布')
    expect(statisticsSource).toContain('Top 10 智能体调用')
  })
})
