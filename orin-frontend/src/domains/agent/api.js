import request from '@/utils/request';

/**
 * F02 Agent 领域 API client（位于 targets/domains/agent/api.js）。
 * 命名遵循 docs/前端重建方案.md §5 目标目录（先建最小集）；
 * surfaces/ 与 domains/ 顶层结构待 F02 后续 PR 收敛。
 *
 * 路由映射（与 backend AgentFreezeController / AgentManageController 一一对应）：
 *   POST /agents                          → createAgent
 *   GET  /agents                          → listAgents（落表于 AgentManageController.listAgents）
 *   GET  /agents/{id}/draft               → getAgentDraft
 *   PUT  /agents/{id}/draft               → upsertAgentDraft（含 pendingSecretRefs）
 *   POST /agents/{id}/versions            → freezeAgentVersion（要求 Idempotency-Key）
 *   GET  /agents/{id}/versions            → getAgentVersions
 *   GET  /agents/{id}/versions/{vid}      → getAgentVersionDetail
 *   PUT  /agents/{id}/active-version      → switchActiveAgentVersion
 *   POST /agents/{id}/versions/{vid}/deprecate → deprecateAgentVersion
 *   GET  /agents/_active-gateway-secrets  → listActiveGatewaySecrets
 */

const AGENT_ROOT = '/agents'

function resolveIdempotencyKey(explicit) {
    if (explicit) return explicit
    if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
    // fallback：与 Native UUID v4 弱兼容
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
        const r = (Math.random() * 16) | 0
        const v = c === 'x' ? r : (r & 0x3) | 0x8
        return v.toString(16)
    })
}

/** F02 R3：创建新 Agent（后端生成 agentId，不接受客户端传入）。 */
export const createAgent = (payload) =>
    request.post(AGENT_ROOT, payload || {})

/** F02 R3：列出当前 Control Plane 真实 Agent 列表（来源：agent_metadata 表）。 */
export const listAgents = () => request.get(AGENT_ROOT)

export const getAgentDraft = (agentId) => request.get(`${AGENT_ROOT}/${agentId}/draft`)

export const upsertAgentDraft = (agentId, payload) =>
    request.put(`${AGENT_ROOT}/${agentId}/draft`, payload)

export const freezeAgentVersion = (agentId, idempotencyKey) =>
    request.post(`${AGENT_ROOT}/${agentId}/versions`, null, {
        headers: { 'Idempotency-Key': resolveIdempotencyKey(idempotencyKey) }
    })

export const getAgentVersions = (agentId) =>
    request.get(`${AGENT_ROOT}/${agentId}/versions`)

export const getAgentVersionDetail = (agentId, versionId) =>
    request.get(`${AGENT_ROOT}/${agentId}/versions/${versionId}`)

export const switchActiveAgentVersion = (agentId, versionId) =>
    request.put(`${AGENT_ROOT}/${agentId}/active-version`, { version_id: versionId })

export const deprecateAgentVersion = (agentId, versionId, reason) =>
    request.post(`${AGENT_ROOT}/${agentId}/versions/${versionId}/deprecate`, { reason })

export const listActiveGatewaySecrets = () =>
    request.get(`${AGENT_ROOT}/_active-gateway-secrets`)
