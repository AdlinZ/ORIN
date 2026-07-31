import { describe, expect, it } from 'vitest'
import { getDocumentProcessingState, getRetrievalDiagnostics } from '@/utils/knowledgeProcessing'

describe('knowledge processing state', () => {
  it('does not let a pending vector status hide a parse failure', () => {
    expect(getDocumentProcessingState({
      parseStatus: 'FAILED',
      vectorStatus: 'PENDING',
      raw: { parseError: 'PDF 内容为空' },
    })).toMatchObject({
      stage: 'parse',
      label: '解析失败',
      tone: 'fail',
      active: false,
      detail: 'PDF 内容为空',
    })
  })

  it('marks parsing and vectorization stages as active', () => {
    expect(getDocumentProcessingState({ parseStatus: 'PARSING', vectorStatus: 'PENDING' }).active).toBe(true)
    expect(getDocumentProcessingState({ parseStatus: 'PARSED', vectorStatus: 'INDEXING' })).toMatchObject({
      stage: 'vector',
      label: '向量化中',
      active: true,
    })
  })

  it('marks a successfully indexed document as retrievable', () => {
    expect(getDocumentProcessingState({ parseStatus: 'PARSED', vectorStatus: 'SUCCESS' })).toMatchObject({
      stage: 'ready',
      label: '可检索',
      tone: 'ok',
    })
  })

  it('treats vector success as authoritative for legacy documents without parse state', () => {
    expect(getDocumentProcessingState({ parseStatus: 'PENDING', vectorStatus: 'SUCCESS' })).toMatchObject({
      stage: 'ready',
      label: '可检索',
    })
  })
})

describe('retrieval diagnostics', () => {
  it('surfaces backend fallback reasons', () => {
    expect(getRetrievalDiagnostics({
      retrievalMode: 'KEYWORD_FALLBACK',
      fallback: true,
      vectorHealthy: false,
      fallbackReason: 'Milvus 不可用',
    })).toEqual({
      mode: 'KEYWORD_FALLBACK',
      fallback: true,
      vectorHealthy: false,
      type: 'warning',
      title: '检索已降级（KEYWORD_FALLBACK）',
      description: 'Milvus 不可用',
    })
  })

  it('reports healthy vector retrieval', () => {
    expect(getRetrievalDiagnostics({ retrievalMode: 'VECTOR', fallback: false })).toMatchObject({
      type: 'success',
      title: '检索链路正常（VECTOR）',
    })
  })
})
