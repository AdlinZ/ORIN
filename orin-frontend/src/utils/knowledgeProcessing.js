const normalize = (value) => String(value || '').trim().toUpperCase()

const parseLabels = {
  PENDING: '等待解析',
  QUEUED: '等待解析',
  PARSING: '解析中',
  CHUNKING: '分块中',
}

const vectorLabels = {
  PENDING: '等待向量化',
  QUEUED: '等待向量化',
  INDEXING: '向量化中',
  VECTORIZING: '向量化中',
}

export function getDocumentProcessingState(document = {}) {
  const raw = document.raw || {}
  const parseStatus = normalize(document.parseStatus || raw.parseStatus)
  const vectorStatus = normalize(document.vectorStatus || raw.vectorStatus)

  if (parseStatus === 'FAILED') {
    return {
      stage: 'parse',
      status: 'FAILED',
      label: '解析失败',
      tone: 'fail',
      active: false,
      detail: document.parseError || raw.parseError || raw.contentPreview || '文档解析失败，请重新解析。',
    }
  }

  if (['SUCCESS', 'INDEXED', 'COMPLETED'].includes(vectorStatus)
    && !['PARSING', 'CHUNKING'].includes(parseStatus)) {
    return {
      stage: 'ready',
      status: vectorStatus,
      label: '可检索',
      tone: 'ok',
      active: false,
      detail: '解析与向量化已完成，可以进行检索测试。',
    }
  }

  if (parseLabels[parseStatus]) {
    return {
      stage: 'parse',
      status: parseStatus,
      label: parseLabels[parseStatus],
      tone: 'processing',
      active: true,
      detail: `${parseLabels[parseStatus]}，页面会自动刷新处理状态。`,
    }
  }

  if (vectorStatus === 'FAILED') {
    return {
      stage: 'vector',
      status: 'FAILED',
      label: '向量化失败',
      tone: 'fail',
      active: false,
      detail: document.vectorError || raw.vectorError || '向量化失败，请检查 Embedding 与 Milvus 配置后重试。',
    }
  }

  if (vectorLabels[vectorStatus]) {
    return {
      stage: 'vector',
      status: vectorStatus,
      label: vectorLabels[vectorStatus],
      tone: 'processing',
      active: true,
      detail: `${vectorLabels[vectorStatus]}，页面会自动刷新处理状态。`,
    }
  }

  return {
    stage: 'unknown',
    status: vectorStatus || parseStatus || 'UNKNOWN',
    label: '状态未知',
    tone: 'idle',
    active: false,
    detail: '暂时无法判断文档处理状态，请刷新后重试。',
  }
}

export function getRetrievalDiagnostics(payload = {}) {
  const mode = normalize(payload.retrievalMode) || 'UNKNOWN'
  const fallback = Boolean(payload.fallback)
  const empty = mode === 'EMPTY'

  return {
    mode,
    fallback,
    vectorHealthy: payload.vectorHealthy !== false,
    type: fallback || empty ? 'warning' : 'success',
    title: empty
      ? '本次检索没有命中结果'
      : fallback
        ? `检索已降级（${mode}）`
        : `检索链路正常（${mode}）`,
    description: payload.fallbackReason
      || (empty ? '请检查文档处理状态、检索阈值或问题表述。' : '结果来自已就绪的知识索引。'),
  }
}
