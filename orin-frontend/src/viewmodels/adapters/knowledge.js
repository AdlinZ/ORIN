const TYPE_ALIAS = Object.freeze({
  DOCUMENT: 'UNSTRUCTURED'
})

function asArray(payload) {
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.data)) return payload.data
  if (Array.isArray(payload?.records)) return payload.records
  if (Array.isArray(payload?.content)) return payload.content
  if (Array.isArray(payload?.list)) return payload.list
  if (Array.isArray(payload?.rows)) return payload.rows
  if (Array.isArray(payload?.results)) return payload.results
  return []
}

function pickTraceId(payload) {
  return payload?.traceId || payload?.trace_id || payload?.requestId || payload?.request_id || null
}

function normalizeType(type) {
  const safe = type || 'UNSTRUCTURED'
  return TYPE_ALIAS[safe] || safe
}

function normalizeStats(item) {
  const stats = item.stats || {}
  return {
    documentCount: Number(stats.documentCount || item.docCount || 0),
    tableCount: Number(stats.tableCount || 0),
    skillCount: Number(stats.skillCount || 0),
    memoryEntryCount: Number(stats.memoryEntryCount || 0)
  }
}

export function toKnowledgeListViewModel(payload) {
  const rows = asArray(payload)
  return rows.map((item) => {
    const type = normalizeType(item.type)
    return {
      id: item.id,
      name: item.name || '未命名知识库',
      description: item.description || item.remark || '',
      type,
      stats: normalizeStats(item),
      createdAt: item.createdAt || item.createTime || null,
      updatedAt: item.updatedAt || item.updateTime || null,
      raw: item
    }
  })
}

export function toKnowledgeAssetListViewModel(knowledgePayload, graphPayload) {
  const knowledgeRows = toKnowledgeListViewModel(knowledgePayload)
  const graphs = asArray(graphPayload)
  const graphMap = new Map()

  for (const graph of graphs) {
    const kbId = graph.knowledgeBaseId || graph.kbId || graph.kb_id
    if (!kbId) continue
    graphMap.set(String(kbId), {
      id: graph.id || graph.graphId,
      name: graph.name || graph.title || '未命名图谱',
      description: graph.description || '',
      buildStatus: graph.buildStatus || graph.status || 'PENDING',
      entityCount: Number(graph.entityCount || graph.entities || 0),
      relationCount: Number(graph.relationCount || graph.relations || 0),
      knowledgeBaseId: kbId,
      updatedAt: graph.updatedAt || graph.gmtModified || graph.lastBuildAt || graph.createdAt || null,
      errorMessage: graph.errorMessage || graph.error || '',
      raw: graph
    })
  }

  return knowledgeRows.map((kb) => ({
    kb,
    graph: graphMap.get(String(kb.id)) || null,
    traceId: pickTraceId(knowledgePayload) || pickTraceId(graphPayload),
    raw: kb.raw
  }))
}

export function toKnowledgeDocumentListViewModel(payload) {
  return asArray(payload).map((item, index) => ({
    id: item.id || item.docId || item.documentId || `document-${index}`,
    fileName: item.fileName || item.originalFilename || item.name || `文档-${item.id || index + 1}`,
    fileType: item.fileType || item.type || '',
    parseStatus: item.parseStatus || item.status || 'PENDING',
    vectorStatus: item.vectorStatus || item.indexStatus || 'PENDING',
    charCount: Number(item.charCount || item.wordCount || item.size || 0),
    uploadTime: item.uploadTime || item.createdAt || item.createTime || null,
    traceId: pickTraceId(item) || pickTraceId(payload),
    raw: item
  }))
}

export function toRetrievalResultViewModel(payload) {
  return asArray(payload).map((item, index) => {
    const metadata = item.metadata || {}
    return {
      id: item.id || item.chunkId || metadata.chunk_id || `retrieval-${index}`,
      content: item.content || item.text || item.pageContent || '',
      score: Number(item.score || item.similarity || 0),
      chunkId: item.chunkId || metadata.chunk_id || metadata.chunkId || '',
      docId: item.docId || item.documentId || metadata.doc_id || metadata.documentId || '',
      sourceDoc: item.sourceDoc || metadata.source || metadata.fileName || metadata.doc_name || '未知文档',
      matchType: item.matchType || item.type || metadata.matchType || 'VECTOR',
      metadata,
      traceId: pickTraceId(item) || pickTraceId(payload),
      raw: item
    }
  })
}

export function toKnowledgeSummaryViewModel(rows) {
  const list = Array.isArray(rows) ? rows : []
  return list.reduce((acc, item) => {
    acc.total += 1
    if (item.type === 'UNSTRUCTURED') acc.unstructured += 1
    if (item.type === 'STRUCTURED') acc.structured += 1
    if (item.type === 'PROCEDURAL') acc.procedural += 1
    if (item.type === 'META_MEMORY') acc.memory += 1
    return acc
  }, {
    total: 0,
    unstructured: 0,
    structured: 0,
    procedural: 0,
    memory: 0
  })
}
