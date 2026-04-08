const STATUS_ALIAS = Object.freeze({
  ACTIVE: 'RUNNING',
  IDLE: 'STOPPED',
  INACTIVE: 'STOPPED'
})

export function normalizeAgentStatus(status) {
  const safe = (status || 'UNKNOWN').toUpperCase()
  return STATUS_ALIAS[safe] || safe
}

export function toAgentListViewModel(payload) {
  const rows = Array.isArray(payload) ? payload : []
  return rows.map((item) => {
    const status = normalizeAgentStatus(item.status)
    return {
      id: item.id || item.agentId || item.agentCode || item.agentName,
      agentName: item.agentName || item.name || '未命名智能体',
      modelName: item.modelName || item.model || '-',
      providerType: item.providerType || item.provider || 'Local',
      viewType: item.viewType || item.type || 'CHAT',
      status,
      lastHeartbeat: item.lastHeartbeat || item.updatedAt || item.createTime || null,
      raw: item
    }
  })
}
