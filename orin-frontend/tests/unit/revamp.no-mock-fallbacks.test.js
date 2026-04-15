import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

const projectRoot = path.resolve(__dirname, '..', '..')

function read(file) {
  return fs.readFileSync(path.join(projectRoot, file), 'utf8')
}

describe('mock fallback guardrails', () => {
  it('keeps latency page free from fake trend/history fallback logic', () => {
    const source = read('src/views/Monitor/LatencyStats.vue')

    expect(source).not.toContain('Math.random')
    expect(source).not.toContain('mockTrend')
    expect(source).not.toContain('mockHistory')
    expect(source).toContain('renderTrendChart([])')
    expect(source).toContain("ElMessage.error('获取延迟历史数据失败')")
  })

  it('keeps removed fake-data pages deleted', () => {
    const removedFiles = [
      'src/views/Knowledge/KBList.vue',
      'src/views/revamp/system/SystemGatewayV2.vue'
    ]

    for (const file of removedFiles) {
      expect(fs.existsSync(path.join(projectRoot, file))).toBe(false)
    }
  })
})
