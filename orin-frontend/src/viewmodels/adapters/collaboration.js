const STATUS_ORDER = ['PLANNING', 'DECOMPOSING', 'EXECUTING', 'CONSENSUS', 'COMPLETED', 'FAILED', 'FALLBACK']

export function toCollaborationPackagesViewModel(payload) {
  const rows = Array.isArray(payload) ? payload : []
  return rows.map((item) => ({
    packageId: item.packageId || item.id,
    intent: item.intent || item.taskIntent || '-',
    status: item.status || 'PLANNING',
    collaborationMode: item.collaborationMode || 'SEQUENTIAL',
    intentCategory: item.intentCategory || item.category || 'ANALYSIS',
    createdAt: item.createdAt || item.createTime || null,
    updatedAt: item.updatedAt || item.updateTime || null,
    priority: item.priority || 'NORMAL',
    raw: item
  }))
}

export function toCollaborationStatsViewModel(payload) {
  const data = payload || {}
  const total = Number(data.totalTasks || data.total || 0)
  const completed = Number(data.completedTasks || data.completed || 0)
  const failed = Number(data.failedTasks || data.failed || 0)
  const running = Number(data.executingTasks || data.executing || 0)

  return {
    total,
    completed,
    failed,
    running,
    successRate: total > 0 ? Number(((completed / total) * 100).toFixed(1)) : 0,
    byStatus: STATUS_ORDER.reduce((acc, key) => {
      acc[key] = Number(data[key] || data[key.toLowerCase()] || 0)
      return acc
    }, {})
  }
}

export function toTimelineViewModel(payload) {
  const rows = Array.isArray(payload) ? payload : []
  return rows.map((item, index) => ({
    id: item.id || item.eventId || `${item.timestamp || 'evt'}-${index}`,
    title: item.eventType || item.type || 'EVENT',
    description: item.message || item.content || '-',
    timestamp: item.timestamp || item.createdAt || null,
    status: item.status || 'info',
    actor: item.actor || item.agentId || 'system'
  }))
}

export function toCollaborationSubtasksViewModel(payload) {
  const rows = Array.isArray(payload) ? payload : []
  return rows.map((item) => ({
    id: item.id,
    packageId: item.packageId,
    subTaskId: item.subTaskId || item.subtaskId || item.id,
    description: item.description || '-',
    expectedRole: item.expectedRole || item.role || '-',
    status: item.status || 'PENDING',
    dependsOn: item.dependsOn || '[]',
    result: item.result || '',
    errorMessage: item.errorMessage || '',
    retryCount: Number(item.retryCount || 0),
    startedAt: item.startedAt || null,
    completedAt: item.completedAt || null,
    raw: item
  }))
}
