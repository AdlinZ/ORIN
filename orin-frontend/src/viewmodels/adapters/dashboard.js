const DEFAULT_METRICS = {
  agents: 0,
  knowledgeBases: 0,
  workflows: 0,
  collaborationPackages: 0,
  traces: 0,
  tasks: {},
  openTasks: 0,
  failedTasks: 0
}

const DEFAULT_ADMIN_STATS = {
  totalUsers: 0,
  totalApiKeys: 0,
  activeAlerts: 0,
  resolvedAlerts: 0
}

const DEFAULT_TRENDS = {
  range: { start: '', end: '' },
  requestCount: [],
  tokenUsage: []
}

const TASK_STATUS_ORDER = [
  'QUEUED',
  'RUNNING',
  'RETRYING',
  'COMPLETED',
  'FAILED',
  'DEAD',
  'CANCELLED'
]

const normalizeNumber = (value) => {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

const normalizeHealth = (health = {}) => {
  const aiEngine = health.aiEngine || {}
  const backend = health.backend || {}
  return {
    backend: {
      status: String(backend.status || 'UNKNOWN').toUpperCase()
    },
    aiEngine: {
      status: String(aiEngine.status || 'UNKNOWN').toUpperCase(),
      service: aiEngine.service || 'orin-ai-engine',
      reachable: Boolean(aiEngine.reachable)
    }
  }
}

const normalizeTrendSeries = (series) => {
  if (!Array.isArray(series)) return []
  return series
    .map((point) => ({
      date: String(point?.date || ''),
      value: normalizeNumber(point?.value)
    }))
    .filter((point) => /^\d{4}-\d{2}-\d{2}$/.test(point.date))
}

const normalizeAgentTypes = (types) => {
  if (!Array.isArray(types)) return []
  return types
    .map((entry) => ({
      key: String(entry?.key || 'other').toLowerCase(),
      label: String(entry?.label || entry?.key || '其他'),
      count: normalizeNumber(entry?.count)
    }))
    .filter((entry) => entry.count > 0)
}

export function toDashboardSummaryViewModel(payload = {}) {
  const metrics = {
    ...DEFAULT_METRICS,
    ...(payload.metrics || {})
  }

  const adminStats = {
    ...DEFAULT_ADMIN_STATS,
    ...(payload.adminStats || {})
  }

  const trendsRaw = payload.trends || {}
  const trends = {
    range: {
      start: trendsRaw.range?.start || DEFAULT_TRENDS.range.start,
      end: trendsRaw.range?.end || DEFAULT_TRENDS.range.end
    },
    requestCount: normalizeTrendSeries(trendsRaw.requestCount),
    tokenUsage: normalizeTrendSeries(trendsRaw.tokenUsage)
  }

  const agentTypes = normalizeAgentTypes(payload.agentTypes)

  const topAlertEvents = Array.isArray(payload.topAlertEvents)
    ? payload.topAlertEvents.map((item) => ({
      endpoint: item.endpoint || '',
      method: item.method || '',
      statusCode: item.statusCode ?? null,
      createdAt: item.createdAt || ''
    }))
    : []

  const systemHealth = normalizeHealth(payload.systemHealth)
  const isOnline = systemHealth.backend.status === 'UP'
    && systemHealth.aiEngine.reachable === true

  const taskStatusMap = metrics.tasks && typeof metrics.tasks === 'object'
    ? metrics.tasks
    : {}
  const taskStatuses = TASK_STATUS_ORDER.map((status) => ({
    status,
    label: status,
    count: normalizeNumber(taskStatusMap[status])
  }))

  return {
    roles: Array.isArray(payload.roles) ? payload.roles : ['ROLE_USER'],
    defaultHome: payload.defaultHome || '/chat',
    isOnline,
    systemHealth,
    metrics: {
      agents: normalizeNumber(metrics.agents),
      knowledgeBases: normalizeNumber(metrics.knowledgeBases),
      workflows: normalizeNumber(metrics.workflows),
      collaborationPackages: normalizeNumber(metrics.collaborationPackages),
      traces: normalizeNumber(metrics.traces),
      tasks: taskStatusMap,
      taskStatuses,
      totalTasks: taskStatuses.reduce((sum, item) => sum + item.count, 0),
      openTasks: normalizeNumber(metrics.openTasks),
      failedTasks: normalizeNumber(metrics.failedTasks)
    },
    adminStats: {
      totalUsers: normalizeNumber(adminStats.totalUsers),
      totalApiKeys: normalizeNumber(adminStats.totalApiKeys),
      activeAlerts: normalizeNumber(adminStats.activeAlerts),
      resolvedAlerts: normalizeNumber(adminStats.resolvedAlerts)
    },
    trends,
    agentTypes,
    agentTypeTotal: agentTypes.reduce((sum, item) => sum + item.count, 0),
    topAlertEvents,
    recentActivity: Array.isArray(payload.recentActivity)
      ? payload.recentActivity.map((item) => ({
        id: item.id || '',
        endpoint: item.endpoint || '',
        method: item.method || '',
        success: item.success === true,
        statusCode: item.statusCode ?? null,
        providerType: item.providerType || '',
        traceId: item.traceId || '',
        createdAt: item.createdAt || ''
      }))
      : [],
    quickLinks: Array.isArray(payload.quickLinks)
      ? payload.quickLinks.map((item) => ({
        title: item.title || '',
        path: item.path || '/'
      })).filter((item) => item.title && item.path)
      : [],
    generatedAt: payload.generatedAt || ''
  }
}
