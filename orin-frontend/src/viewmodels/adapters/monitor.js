export function toRuntimeSummaryViewModel(payload) {
  const data = payload || {}
  return {
    activeAgents: Number(data.activeAgents || data.onlineAgents || 0),
    totalCalls: Number(data.totalCalls || data.requestCount || 0),
    totalTokens: Number(data.totalTokens || data.tokenUsage || 0),
    avgLatency: Number(data.avgLatency || data.averageLatency || 0),
    errorRate: Number(data.errorRate || 0),
    successRate: Number(data.successRate || 0)
  }
}

export function toSuccessRateViewModel(payload) {
  const data = payload || {}
  return {
    total: Number(data.totalCalls || 0),
    success: Number(data.successCalls || 0),
    ratio: Number(data.successRate || 0)
  }
}

export function toLangfuseStatusViewModel(payload) {
  const data = payload || {}
  return {
    enabled: Boolean(data.enabled),
    configured: Boolean(data.configured),
    message: data.message || (data.enabled ? 'Langfuse tracing 已启用' : 'Langfuse tracing 未启用'),
    dashboardLink: data.link || data.url || ''
  }
}
