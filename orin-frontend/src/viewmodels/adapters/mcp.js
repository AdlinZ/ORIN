const asArray = (payload) => {
  if (Array.isArray(payload)) return payload
  if (!payload || typeof payload !== 'object') return []
  return payload.content || payload.records || payload.items || payload.data || []
}

export const toMcpServiceListViewModel = (payload = []) => asArray(payload).map((item) => {
  const type = String(item.type || 'STDIO').toUpperCase()
  const status = String(item.status || 'DISCONNECTED').toUpperCase()
  return {
    id: item.id ?? item.serviceId ?? item.name,
    name: item.name || item.serviceName || '未命名 MCP 服务',
    toolKey: item.toolKey || item.tool_key || '',
    type,
    endpoint: type === 'STDIO'
      ? (item.command || '')
      : (item.url || item.endpoint || ''),
    command: item.command || '',
    url: item.url || item.endpoint || '',
    description: item.description || '',
    enabled: item.enabled !== false,
    status,
    lastConnected: item.lastConnected || item.last_connected || '',
    lastError: item.lastError || item.last_error || '',
    healthScore: Number.isFinite(Number(item.healthScore)) ? Number(item.healthScore) : null,
    raw: item,
  }
})

export const mcpServiceReadiness = (service = {}) => {
  if (!service.enabled) return 'DISABLED'
  if (service.status === 'CONNECTED') return 'READY'
  if (service.status === 'ERROR') return 'ERROR'
  if (service.status === 'TESTING') return 'TESTING'
  return 'NEEDS_TEST'
}
