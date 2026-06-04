import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

const projectRoot = path.resolve(__dirname, '..', '..')

function read(file) {
  return fs.readFileSync(path.join(projectRoot, file), 'utf8')
}

function collectSourceFiles(dirs) {
  const files = []
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name)
      if (entry.isDirectory()) {
        walk(full)
      } else if (/\.(vue|js|ts)$/.test(entry.name)) {
        files.push(full)
      }
    }
  }

  for (const dir of dirs) {
    walk(path.join(projectRoot, dir))
  }
  return files
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

  it('does not expose frontend entry points for backend mock monitor data', () => {
    const monitorApi = read('src/api/monitor.js')

    expect(monitorApi).not.toContain('triggerMockData')
    expect(monitorApi).not.toContain('/monitor/mock/trigger')
  })

  it('keeps product frontend code free from debug console output', () => {
    const offenders = collectSourceFiles(['src/api', 'src/views', 'src/components', 'src/stores', 'src/router', 'src/layout', 'src/utils'])
      .filter((file) => {
        const source = fs.readFileSync(file, 'utf8')
        return /\bconsole\.(log|debug)\s*\(/.test(source)
      })
      .map((file) => path.relative(projectRoot, file))

    expect(offenders).toEqual([])
  })

  it('keeps removed fake-data pages deleted', () => {
    const removedFiles = [
      'src/views/Knowledge/KBList.vue',
      'src/views/revamp/system/SystemUnifiedGatewayV2.vue'
    ]

    for (const file of removedFiles) {
      expect(fs.existsSync(path.join(projectRoot, file))).toBe(false)
    }
  })

  it('keeps knowledge document upload progress tied to real transport events', () => {
    const api = read('src/api/knowledge.js')
    const component = read('src/views/Knowledge/components/DocumentList.vue')

    expect(api).toContain('uploadDocument = (kbId, file, uploadedBy, options = {})')
    expect(api).toContain('...options')
    expect(component).toContain('onUploadProgress')
    expect(component).not.toContain('模拟上传进度')
    expect(component).not.toContain('uploadProgress.value += 10')
  })

  it('keeps asset schema preview deterministic and based on uploaded text', () => {
    const source = read('src/views/Knowledge/AssetSchema.vue')

    expect(source).toContain('previewFile.value.text()')
    expect(source).toContain('buildPreviewChunks(text)')
    expect(source).not.toContain('Math.random')
    expect(source).not.toContain('Mock processing delay')
    expect(source).not.toContain('simulated content')
  })

  it('keeps log archive page backed by real audit log APIs', () => {
    const source = read('src/views/Monitor/LogArchive.vue')

    expect(source).toContain('getGatewayAuditLogs')
    expect(source).not.toContain('模拟数据')
    expect(source).not.toContain('System started successfully')
    expect(source).not.toContain('导出功能开发中')
    expect(source).not.toContain('console.log')
  })

  it('keeps knowledge creation preview free from sample text fallbacks', () => {
    const source = read('src/views/Knowledge/KBCreate.vue')

    expect(source).not.toContain('getMockText')
    expect(source).not.toContain('示例文本')
    expect(source).not.toContain('Fallback to local file reading or mock')
    expect(source).toContain('readFileAsText(targetFile.rawFile)')
    expect(source).toContain('当前文件尚未获得真实解析文本')
  })

  it('keeps rag evaluation metrics grounded in retrieval scores', () => {
    const source = read('src/views/Knowledge/components/RagEvaluation.vue')

    expect(source).toContain('检索摘要')
    expect(source).toContain('metrics.averageScore')
    expect(source).toContain('Math.max(...scores)')
    expect(source).not.toContain('Precision@K')
    expect(source).not.toContain('Recall@K')
    expect(source).not.toContain('NDCG@K')
    expect(source).not.toContain('metrics.precisionAtK')
    expect(source).not.toContain('Mock metrics calculation')
    expect(source).not.toContain('基准运行完成')
  })

  it('keeps knowledge detail benchmarks backed by backend results', () => {
    const source = read('src/views/Knowledge/KBDetail.vue')

    expect(source).toContain('getEvaluationBenchmarks')
    expect(source).toContain('normalizeBenchmarks')
    expect(source).toContain('formatBenchmarkScore')
    expect(source).not.toContain('Math.random')
    expect(source).not.toContain('基准执行完成')
    expect(source).not.toContain('正在执行：')
  })
})
