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

/**
 * F02 R3：列出当前 Control Plane 真实 Agent 列表。
 * 后端公开契约中的版本字段使用 snake_case；在领域 API 边界统一为前端 camelCase，
 * 避免每个页面各自猜测字段名。
 */
export const listAgents = async () => {
    const list = await request.get(AGENT_ROOT)
    return (list || []).map(agent => ({
        ...agent,
        activeVersionId: agent.activeVersionId ?? agent.active_version_id ?? null,
        activeVersionNumber: agent.activeVersionNumber ?? agent.active_version_number ?? null,
        activeVersionDigest: agent.activeVersionDigest ?? agent.active_version_digest ?? null,
        activeVersionStatus: agent.activeVersionStatus ?? agent.active_version_status ?? null,
    }))
}

export const getAgentDraft = (agentId) => request.get(`${AGENT_ROOT}/${agentId}/draft`)

export const upsertAgentDraft = (agentId, payload) =>
    request.put(`${AGENT_ROOT}/${agentId}/draft`, payload)

export const freezeAgentVersion = (agentId, idempotencyKey) =>
    request.post(`${AGENT_ROOT}/${agentId}/versions`, null, {
        headers: { 'Idempotency-Key': resolveIdempotencyKey(idempotencyKey) }
    })

export const getAgentVersions = async (agentId) => {
    const versions = await request.get(`${AGENT_ROOT}/${agentId}/versions`)
    const list = Array.isArray(versions) ? versions : versions?.data ?? []
    return list.map(version => {
        const versionId = version.id ?? version.agentVersionId ?? version.agent_version_id ?? null
        return {
            ...version,
            // Backend deliberately exposes the immutable-version list in
            // snake_case.  Normalize it once at the domain boundary so Agent
            // pages, Run creation and Endpoint publishing all select the same
            // immutable identifier instead of guessing field names.
            id: versionId,
            agentVersionId: version.agentVersionId ?? version.agent_version_id ?? versionId,
            versionNumber: version.versionNumber ?? version.version_number ?? null,
            versionTag: version.versionTag ?? version.version_tag ?? null,
            contentDigest: version.contentDigest ?? version.content_digest ?? null,
            snapshotSchemaVersion: version.snapshotSchemaVersion ?? version.snapshot_schema_version ?? null,
            frozenAt: version.frozenAt ?? version.frozen_at ?? null,
            createdBy: version.createdBy ?? version.created_by ?? null,
            isActive: version.isActive ?? version.is_active ?? false,
        }
    })
}

export const getAgentVersionDetail = (agentId, versionId) =>
    request.get(`${AGENT_ROOT}/${agentId}/versions/${versionId}`)

export const switchActiveAgentVersion = (agentId, versionId) =>
    request.put(`${AGENT_ROOT}/${agentId}/active-version`, { version_id: versionId })

export const deprecateAgentVersion = (agentId, versionId, reason) =>
    request.post(`${AGENT_ROOT}/${agentId}/versions/${versionId}/deprecate`, { reason })

export const listActiveGatewaySecrets = () =>
    request.get(`${AGENT_ROOT}/_active-gateway-secrets`)
