package com.adlin.orin.modules.agent.freeze.audit;

import com.adlin.orin.modules.audit.service.AuditHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * F02 AgentVersion / AgentDraft 审计写入器。
 *
 * <p>遵循仓库现有 {@code AuditHelper.log(userId, operation, endpoint, detail, success, errorMsg)}
 * 惯例，把 AgentVersion 事件归到 {@code providerType="SYSTEM"} / {@code providerId=<operation>}。
 * <b>绝不在 detail 串里写明文 Secret / value / 凭据</b>，仅携带 alias/source/ref_id/injectAs/digest。
 *
 * <p>事件常量表（建议前端按这里展示）：
 * <ul>
 *   <li>{@code AGENT_VERSION_FROZEN}：freeze 成功后（包含 idempotent replay 命中）</li>
 *   <li>{@code AGENT_DRAFT_UPDATED}：草稿 upsert 成功后</li>
 *   <li>{@code AGENT_DRAFT_SECRET_REF_CHANGED}：freeze 时 secretRefs 与上次不同</li>
 *   <li>{@code AGENT_ACTIVE_VERSION_SWITCHED}：切 active 成功</li>
 *   <li>{@code AGENT_VERSION_DEPRECATED}：deprecate 成功</li>
 *   <li>{@code AGENT_VERSION_FREEZE_REJECTED}：freeze 失败（非 200），用于审计失败轨迹</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentVersionAuditWriter {

    public static final String OP_FROZEN = "AGENT_VERSION_FROZEN";
    public static final String OP_CREATED = "AGENT_CREATED";
    public static final String OP_DRAFT_UPDATED = "AGENT_DRAFT_UPDATED";
    public static final String OP_DRAFT_SECRET_REF_CHANGED = "AGENT_DRAFT_SECRET_REF_CHANGED";
    public static final String OP_ACTIVE_SWITCHED = "AGENT_ACTIVE_VERSION_SWITCHED";
    public static final String OP_DEPRECATED = "AGENT_VERSION_DEPRECATED";
    public static final String OP_FREEZE_REJECTED = "AGENT_VERSION_FREEZE_REJECTED";

    private final AuditHelper auditHelper;

    /** Agent 创建。 */
    public void logAgentCreated(String actor, String agentId, String name) {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("agent_id", agentId);
        meta.put("name", name);
        write(actor, OP_CREATED, draftEndpoint(agentId), true, null, meta);
    }

    /** Draft upsert（含 INSERT 与 UPDATE）。 */
    public void logDraftUpdated(String actor, String agentId, String changeDescription) {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("agent_id", agentId);
        meta.put("change_description", changeDescription);
        write(actor, OP_DRAFT_UPDATED, draftEndpoint(agentId), true, null, meta);
    }

    /** Draft 上 pendingSecretRefs 与历史不一致（detected at freeze time）。 */
    public void logDraftSecretRefChanged(String actor, String agentId, int fromCount, int toCount) {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("agent_id", agentId);
        meta.put("from_refs", fromCount);
        meta.put("to_refs", toCount);
        write(actor, OP_DRAFT_SECRET_REF_CHANGED, draftEndpoint(agentId), true, null, meta);
    }

    /** Freeze 成功（含 idempotent replay）。 */
    public void logFrozen(String actor, String agentId, String agentVersionId,
                          int versionNumber, String digest, short schemaVersion,
                          boolean idempotentReplay, String secretRefsDigestDetail) {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("agent_id", agentId);
        meta.put("agent_version_id", agentVersionId);
        meta.put("version_number", versionNumber);
        meta.put("content_digest", digest);
        meta.put("snapshot_schema_version", schemaVersion);
        meta.put("idempotent_replay", idempotentReplay);
        meta.put("secret_refs", secretRefsDigestDetail);
        write(actor, OP_FROZEN, freezeEndpoint(agentId), true, null, meta);
    }

    /** Freeze 拒绝（写失败轨迹供 support 排查）。 */
    public void logFreezeRejected(String actor, String agentId, String idempotencyKeyHash,
                                  String errorCode, String errorMessage) {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("agent_id", agentId);
        meta.put("idempotency_key_hash", idempotencyKeyHash);
        meta.put("error_code", errorCode);
        write(actor, OP_FREEZE_REJECTED, freezeEndpoint(agentId), false, errorMessage, meta);
    }

    /** Active version 切换。 */
    public void logActiveSwitched(String actor, String agentId, String fromVersionId, String toVersionId) {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("agent_id", agentId);
        meta.put("from_version_id", fromVersionId);
        meta.put("to_version_id", toVersionId);
        write(actor, OP_ACTIVE_SWITCHED, activeEndpoint(agentId), true, null, meta);
    }

    /** Active 切换被拒绝。 */
    public void logActiveSwitchRejected(String actor, String agentId, String toVersionId, String errorCode) {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("agent_id", agentId);
        meta.put("to_version_id", toVersionId);
        meta.put("error_code", errorCode);
        write(actor, "AGENT_ACTIVE_SWITCH_REJECTED", activeEndpoint(agentId), false, errorCode, meta);
    }

    /** Deprecate 成功。 */
    public void logDeprecated(String actor, String agentId, String agentVersionId, String reason) {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("agent_id", agentId);
        meta.put("agent_version_id", agentVersionId);
        meta.put("reason", reason);
        write(actor, OP_DEPRECATED, deprecateEndpoint(agentId, agentVersionId), true, null, meta);
    }

    /** Deprecate 被拒绝。 */
    public void logDeprecateRejected(String actor, String agentId, String agentVersionId, String errorCode) {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("agent_id", agentId);
        meta.put("agent_version_id", agentVersionId);
        meta.put("error_code", errorCode);
        write(actor, "AGENT_VERSION_DEPRECATE_REJECTED", deprecateEndpoint(agentId, agentVersionId),
                false, errorCode, meta);
    }

    private void write(String actor, String operation, String endpoint, boolean success,
                       String errorMsg, Map<String, Object> meta) {
        try {
            String detail = toJson(meta);
            auditHelper.log(actor, operation, endpoint, detail, success, errorMsg);
        } catch (Exception e) {
            log.warn("Failed to write F02 audit op={} actor={}: {}", operation, actor, e.getMessage());
        }
    }

    private static String freezeEndpoint(String agentId) {
        return "POST /api/v1/agents/" + agentId + "/versions";
    }

    private static String draftEndpoint(String agentId) {
        return "PUT /api/v1/agents/" + agentId + "/draft";
    }

    private static String activeEndpoint(String agentId) {
        return "PUT /api/v1/agents/" + agentId + "/active-version";
    }

    private static String deprecateEndpoint(String agentId, String versionId) {
        return "POST /api/v1/agents/" + agentId + "/versions/" + versionId + "/deprecate";
    }

    private static String toJson(Map<String, Object> meta) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(meta);
        } catch (Exception e) {
            return meta.toString();
        }
    }
}
