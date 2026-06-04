const asArray = (payload) => {
  if (Array.isArray(payload)) return payload
  if (!payload || typeof payload !== 'object') return []
  return (
    payload.content ||
    payload.records ||
    payload.items ||
    payload.list ||
    payload.data ||
    []
  )
}

const toNumber = (value, fallback = 0) => {
  const next = Number(value)
  return Number.isFinite(next) ? next : fallback
}

const formatDateText = (value) => {
  if (!value) return ''
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')} ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:${String(second).padStart(2, '0')}`
  }
  return String(value)
}

export const toSessionListViewModel = (payload = []) => asArray(payload).map((item) => ({
  id: item.id || item.sessionId || item.conversationId,
  sessionId: item.sessionId || item.conversationId || item.id || '',
  agentId: item.agentId || '',
  agentName: item.agentName || item.agent?.name || '',
  modelName: item.modelName || item.model || '',
  lastQuery: item.lastQuery || item.query || item.title || '',
  messageCount: toNumber(item.messageCount || item.messages),
  tokens: toNumber(item.tokens || item.cumulativeTokens || item.totalTokens),
  responseTime: toNumber(item.responseTime || item.latencyMs),
  time: formatDateText(item.time || item.createdAt || item.updatedAt),
  success: item.success !== false,
  traceId: item.traceId || item.trace_id || '',
  raw: item
}))

export const toModelListViewModel = (payload = []) => asArray(payload).map((item) => ({
  id: item.id || item.modelId || item.name,
  name: item.name || item.modelName || item.modelId || '',
  modelId: item.modelId || item.model || item.name || '',
  provider: item.provider || item.providerName || '',
  type: item.type || item.modelType || 'CHAT',
  status: item.status || (item.enabled === false ? 'DISABLED' : 'ENABLED'),
  createTime: formatDateText(item.createTime || item.createdAt),
  description: item.description || '',
  traceId: item.traceId || item.trace_id || '',
  raw: item
}))

export const toSkillListViewModel = (payload = []) => asArray(payload).map((item) => ({
  id: item.id || item.skillId || item.skillName,
  skillName: item.skillName || item.name || '',
  skillType: item.skillType || item.type || 'API',
  description: item.description || '',
  status: item.status || (item.enabled === false ? 'INACTIVE' : 'ACTIVE'),
  version: item.version || '1.0.0',
  createdAt: formatDateText(item.createdAt || item.createTime),
  traceId: item.traceId || item.trace_id || '',
  raw: item
}))

export const toPlaygroundSummaryViewModel = (payload = {}) => {
  const agents = asArray(payload.agents)
  const workflows = asArray(payload.workflows)
  const templates = asArray(payload.templates)
  return {
    agents,
    workflows,
    templates,
    metrics: [
      { key: 'agents', label: 'Agents', value: agents.length, meta: '可参与编排的智能体' },
      { key: 'workflows', label: '方案', value: workflows.length, meta: '已保存协作方案' },
      { key: 'templates', label: '模式', value: templates.length, meta: '可用编排模式' }
    ]
  }
}
