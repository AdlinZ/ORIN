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

function asArray(payload) {
  if (Array.isArray(payload)) return payload
  if (!payload || typeof payload !== 'object') return []
  return payload.content || payload.records || payload.items || payload.list || payload.data || []
}

function resolveTraceId(payload) {
  if (!payload || typeof payload !== 'object') return ''
  const data = payload.data && typeof payload.data === 'object' ? payload.data : {}
  return String(
    payload.traceId ||
    payload.trace_id ||
    payload.langfuseTraceId ||
    payload.langfuse_trace_id ||
    data.traceId ||
    data.trace_id ||
    data.langfuseTraceId ||
    data.langfuse_trace_id ||
    ''
  )
}

function resolveWorkflowDefinition(item) {
  return item.workflowDefinition || item.definition || item.dsl || {}
}

export function toWorkflowListViewModel(payload) {
  const rows = asArray(payload)
  return rows.map((item) => ({
    ...item,
    id: item.id || item.workflowId,
    workflowName: item.workflowName || item.name || '未命名工作流',
    description: item.description || '',
    status: item.status || 'DRAFT',
    updatedAt: item.updatedAt || item.updateTime || item.modifiedAt || null,
    createdAt: item.createdAt || item.createTime || null,
    source: item.source || 'ORIN',
    workflowDefinition: resolveWorkflowDefinition(item),
    traceId: resolveTraceId(item),
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

export function toWorkflowInstanceViewModel(payload) {
  return asArray(payload).map((item, index) => ({
    id: item.id || item.instanceId || item.runId || `instance-${index}`,
    workflowId: item.workflowId || item.workflow_id || '',
    workflowInstanceId: item.workflowInstanceId || item.instanceId || item.id || '',
    status: item.status || item.state || 'UNKNOWN',
    createdAt: item.createdAt || item.startTime || item.startedAt || null,
    finishedAt: item.finishedAt || item.completedAt || item.endTime || null,
    durationMs: item.durationMs || item.duration || 0,
    traceId: resolveTraceId(item),
    raw: item
  }))
}

export function toWorkflowTaskViewModel(payload) {
  return asArray(payload).map((item, index) => ({
    id: item.id || item.taskId || `task-${index}`,
    taskId: item.taskId || item.id || `task-${index}`,
    workflowId: item.workflowId || '',
    workflowInstanceId: item.workflowInstanceId || item.instanceId || '',
    status: item.status || 'UNKNOWN',
    retryCount: Number(item.retryCount || 0),
    maxRetries: Number(item.maxRetries || 0),
    durationMs: item.durationMs || item.duration || 0,
    updatedAt: item.updatedAt || item.updateTime || item.createdAt || null,
    createdAt: item.createdAt || item.createTime || null,
    errorMessage: item.errorMessage || item.error || '',
    traceId: resolveTraceId(item),
    raw: item
  }))
}

export function toWorkflowDslValidationViewModel(issues = []) {
  const rows = Array.isArray(issues) ? issues : []
  const level = rows.length === 0 ? 'FULL' : rows.length <= 2 ? 'PARTIAL' : 'BLOCKED'
  return {
    level,
    issueCount: rows.length,
    issues: rows.map((issue) => String(issue || '')).filter(Boolean),
    publishable: rows.length === 0
  }
}
