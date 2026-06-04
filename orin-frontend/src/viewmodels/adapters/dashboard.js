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

export function toDashboardSummaryViewModel(payload = {}) {
  const metrics = {
    ...DEFAULT_METRICS,
    ...(payload.metrics || {})
  }

  const adminStats = {
    ...DEFAULT_ADMIN_STATS,
    ...(payload.adminStats || {})
  }

  const topAlertEvents = Array.isArray(payload.topAlertEvents)
    ? payload.topAlertEvents.map((item) => ({
      endpoint: item.endpoint || '',
      method: item.method || '',
      statusCode: item.statusCode ?? null,
      createdAt: item.createdAt || ''
    }))
    : []

  return {
    roles: Array.isArray(payload.roles) ? payload.roles : ['ROLE_USER'],
    defaultHome: payload.defaultHome || '/portal',
    systemHealth: normalizeHealth(payload.systemHealth),
    metrics: {
      agents: normalizeNumber(metrics.agents),
      knowledgeBases: normalizeNumber(metrics.knowledgeBases),
      workflows: normalizeNumber(metrics.workflows),
      collaborationPackages: normalizeNumber(metrics.collaborationPackages),
      traces: normalizeNumber(metrics.traces),
      tasks: metrics.tasks || {},
      openTasks: normalizeNumber(metrics.openTasks),
      failedTasks: normalizeNumber(metrics.failedTasks)
    },
    adminStats: {
      totalUsers: normalizeNumber(adminStats.totalUsers),
      totalApiKeys: normalizeNumber(adminStats.totalApiKeys),
      activeAlerts: normalizeNumber(adminStats.activeAlerts),
      resolvedAlerts: normalizeNumber(adminStats.resolvedAlerts)
    },
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
