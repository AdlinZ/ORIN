export const RUN_STATUS_META = {
  QUEUED: { label: '排队中', type: 'warning' },
  LEASED: { label: '已分配', type: 'warning' },
  RUNNING: { label: '运行中', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' },
}

export function getRunStatusMeta(status) {
  return RUN_STATUS_META[status] || { label: status || '未知', type: 'info' }
}

export function isRunActive(status) {
  return status === 'QUEUED' || status === 'LEASED' || status === 'RUNNING'
}

export function isRunCancellable(status) {
  return isRunActive(status)
}

export function isRunRetryable(run = {}) {
  return (run.status === 'FAILED' || run.status === 'CANCELLED')
    && run.retryCount < run.maxRetries
}

export function getRunOutcomeGroup(status) {
  if (isRunActive(status)) return 'ACTIVE'
  if (status === 'COMPLETED') return 'SUCCEEDED'
  if (status === 'FAILED') return 'NEEDS_ACTION'
  if (status === 'CANCELLED') return 'STOPPED'
  return 'UNKNOWN'
}

export function getRunStatusStep(status) {
  if (!status) return 0
  if (status === 'COMPLETED') return 4
  if (status === 'FAILED' || status === 'CANCELLED') return 3
  return ['QUEUED', 'LEASED', 'RUNNING'].indexOf(status)
}

export function getTerminalReasonLabel(reason) {
  const labels = {
    USER_CANCELLED: '用户主动取消',
    NETWORK_LOST: 'Runner 失联或网络中断',
    CREDENTIAL_REVOKED: 'Runner 凭据被撤销',
    SECRET_REVOKED: '关联密钥已被撤销',
    RUNNER_REVOKED: 'Runner 已被管理员撤销',
    RUNNER_FAILED: '执行过程中出错',
    LEASE_EXPIRED: 'Lease 超时未续约',
    CANCELLED: '已取消',
    SECRET_BIND_FAILED: '密钥绑定失败',
  }
  return labels[reason] || reason || '—'
}

export function getAgentDeliveryState(agent = {}) {
  if (agent.activeVersionStatus === 'DEPRECATED') {
    return { key: 'DEPRECATED', label: '已退役', type: 'info', action: '查看版本' }
  }
  if (agent.activeVersionStatus === 'FROZEN') {
    return { key: 'READY', label: '可运行', type: 'success', action: '运行' }
  }
  return { key: 'DRAFT', label: '待冻结', type: 'warning', action: '继续配置' }
}

export function getAgentVersionId(version = {}) {
  return version.id || version.agentVersionId || version.agent_version_id || ''
}

export function getAgentVersionNumber(version = {}) {
  return version.versionNumber ?? version.version_number ?? null
}

export function getAgentVersionState(version = {}) {
  if (version.status === 'DEPRECATED') {
    return { key: 'DEPRECATED', label: '已退役', type: 'info' }
  }
  if (version.isActive || version.is_active) {
    return { key: 'CURRENT', label: '当前使用', type: 'primary' }
  }
  if (version.status === 'FROZEN') {
    return { key: 'READY', label: '可运行', type: 'success' }
  }
  return { key: 'UNKNOWN', label: version.status || '未知', type: 'warning' }
}

export function chooseDeliverableVersion(agent = {}, versions = [], preferredVersionId = '') {
  const preferred = versions.find(
    (version) => getAgentVersionId(version) === preferredVersionId && version.status === 'FROZEN'
  )
  if (preferred) return getAgentVersionId(preferred)
  const activeId = agent.activeVersionId || agent.active_version_id
  const active = versions.find((version) => getAgentVersionId(version) === activeId)
  if (active) return getAgentVersionId(active)
  const latest = [...versions].sort(
    (left, right) => (getAgentVersionNumber(right) || 0) - (getAgentVersionNumber(left) || 0)
  )[0]
  return getAgentVersionId(latest)
}

export function chooseRunVersion(agent = {}, versions = [], preferredVersionId = '') {
  return chooseDeliverableVersion(agent, versions, preferredVersionId)
}

export function chooseRunRunner(runners = [], currentRunnerId = '') {
  if (runners.some((runner) => runner.id === currentRunnerId)) return currentRunnerId
  return runners[0]?.id || ''
}

export function normalizeAgentVersionDetail(raw = {}) {
  const secretRefs = raw.secretRefs || raw.secret_refs || []
  return {
    id: getAgentVersionId(raw),
    agentId: raw.agentId || raw.agent_id || '',
    versionNumber: getAgentVersionNumber(raw),
    status: raw.status || 'UNKNOWN',
    isActive: raw.isActive ?? raw.is_active ?? false,
    changeDescription: raw.changeDescription || raw.change_description || '',
    contentDigest: raw.contentDigest || raw.content_digest || '',
    snapshotSchemaVersion: raw.snapshotSchemaVersion ?? raw.snapshot_schema_version ?? null,
    createdAt: raw.createdAt || raw.created_at || null,
    createdBy: raw.createdBy || raw.created_by || '',
    frozenAt: raw.frozenAt || raw.frozen_at || null,
    frozenBy: raw.frozenBy || raw.frozen_by || '',
    deprecatedAt: raw.deprecatedAt || raw.deprecated_at || null,
    deprecationReason: raw.deprecationReason || raw.deprecation_reason || '',
    secretRefs: secretRefs.map((secret) => ({
      alias: secret.alias || '',
      source: secret.source || '',
      injectAs: secret.injectAs || secret.inject_as || '',
      required: secret.required !== false,
    })),
  }
}

export function getEndpointStatusMeta(status) {
  if (status === 'ACTIVE') return { label: '服务中', type: 'success' }
  if (status === 'INACTIVE') return { label: '已下线', type: 'info' }
  return { label: status || '异常', type: 'danger' }
}

export const RUNNER_STATUS_META = {
  ONLINE: {
    label: '可运行',
    type: 'success',
    description: '正在连接，可以接收新任务',
  },
  ENROLLING: {
    label: '接入中',
    type: 'warning',
    description: '正在建立首次连接',
  },
  DRAINING: {
    label: '暂停接单',
    type: 'warning',
    description: '不再接收新任务，已有任务会继续完成',
  },
  DEGRADED: {
    label: '需要检查',
    type: 'danger',
    description: '仍有连接，但运行环境存在异常',
  },
  OFFLINE: {
    label: '离线',
    type: 'info',
    description: '当前无法接收任务',
  },
  REVOKED: {
    label: '已撤销',
    type: 'info',
    description: '接入凭据已失效，不能再次连接',
  },
  NEW: {
    label: '等待连接',
    type: 'warning',
    description: '尚未收到 Runner 的首次连接',
  },
}

export function getRunnerStatusMeta(status) {
  return RUNNER_STATUS_META[status] || {
    label: status || '未知',
    type: 'info',
    description: '状态暂时无法识别',
  }
}

export function getRunnerReadiness(runners = []) {
  const online = runners.filter((runner) => runner.status === 'ONLINE').length
  const paused = runners.filter(
    (runner) => runner.status === 'DRAINING' || runner.drainRequested
  ).length
  const needsAttention = runners.filter(
    (runner) => runner.status === 'OFFLINE' || runner.status === 'DEGRADED'
  ).length
  return {
    online,
    paused,
    needsAttention,
    ready: online > 0,
  }
}

export function compactId(value, head = 8, tail = 4) {
  const text = String(value || '—')
  if (text.length <= head + tail + 1) return text
  return `${text.slice(0, head)}…${text.slice(-tail)}`
}

export function formatWorkspaceTime(value) {
  if (!value) return '—'
  const numeric = Number(value)
  const date = Number.isFinite(numeric)
    ? new Date(numeric < 1e12 ? numeric * 1000 : numeric)
    : new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
