export function toWorkflowListViewModel(payload) {
  const rows = Array.isArray(payload) ? payload : []
  return rows.map((item) => ({
    id: item.id || item.workflowId,
    workflowName: item.workflowName || item.name || '未命名工作流',
    description: item.description || '',
    status: item.status || 'DRAFT',
    updatedAt: item.updatedAt || item.updateTime || item.modifiedAt || null,
    createdAt: item.createdAt || item.createTime || null,
    source: item.source || 'ORIN',
    raw: item
  }))
}

export function toWorkflowStatsViewModel(rows) {
  const list = Array.isArray(rows) ? rows : []
  return list.reduce((acc, row) => {
    acc.total += 1
    if (row.status === 'PUBLISHED') {
      acc.published += 1
    } else {
      acc.draft += 1
    }
    return acc
  }, {
    total: 0,
    published: 0,
    draft: 0
  })
}
