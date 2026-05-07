export function isWorkflowPublished(status) {
  return ['ACTIVE', 'PUBLISHED'].includes(String(status || '').toUpperCase())
}

export function workflowStatusLabel(status) {
  const normalized = String(status || '').toUpperCase()
  if (isWorkflowPublished(normalized)) return '已发布'
  if (normalized === 'ARCHIVED') return '已归档'
  return '草稿'
}

export function workflowStatusTagType(status) {
  const normalized = String(status || '').toUpperCase()
  if (isWorkflowPublished(normalized)) return 'success'
  if (normalized === 'ARCHIVED') return 'warning'
  return 'info'
}

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
    if (isWorkflowPublished(row.status)) {
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
