const TYPE_ALIAS = Object.freeze({
  DOCUMENT: 'UNSTRUCTURED'
})

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
  const rows = Array.isArray(payload) ? payload : []
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
