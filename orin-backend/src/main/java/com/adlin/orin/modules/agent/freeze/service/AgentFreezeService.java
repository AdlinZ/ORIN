package com.adlin.orin.modules.agent.freeze.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.common.snapshot.JcsCanonicalizer;
import com.adlin.orin.common.snapshot.Sha256Digest;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.entity.AgentVersion;
import com.adlin.orin.modules.agent.freeze.audit.AgentVersionAuditWriter;
import com.adlin.orin.modules.agent.freeze.dto.AgentVersionDetailResponse;
import com.adlin.orin.modules.agent.freeze.dto.AgentVersionListItem;
import com.adlin.orin.modules.agent.freeze.dto.FreezeAgentResponse;
import com.adlin.orin.modules.agent.freeze.dto.FreezeSecretRefItem;
import com.adlin.orin.modules.agent.freeze.entity.AgentVersionFreezeIdempotency;
import com.adlin.orin.modules.agent.freeze.entity.AgentVersionSecretRef;
import com.adlin.orin.modules.agent.freeze.repository.AgentVersionFreezeIdempotencyRepository;
import com.adlin.orin.modules.agent.freeze.repository.AgentVersionSecretRefRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.repository.AgentVersionRepository;
import com.adlin.orin.modules.agent.service.AgentOwnershipResolver;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.repository.GatewaySecretRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * F02 freeze 核心服务。
 *
 * <p>事务流程严格遵循 ADR-002 v4.1 §D-2.3.1：
 * <pre>
 *   BEGIN;
 *     1) SELECT * FROM agent_metadata WHERE agent_id = :id FOR UPDATE;
 *     2) 读草稿 pending_secret_refs → 校验（CONTROL_PLANE secret 必须 ACTIVE）；
 *     3) 计算 canonical envelope → SHA-256 digest；
 *     4) INSERT agent_versions（status=FROZEN）；
 *     5) INSERT agent_version_secret_refs（按 alias 升序）；
 *     6) INSERT agent_version_freeze_idempotency；
 *     7) 当 metadata.active_version_id 为空时 → 首次 freeze 自动设 active_version_id。
 *   COMMIT;
 * </pre>
 *
 * <p>幂等路径（先查 idempotency 表）：
 * <ul>
 *   <li>同 key_hash + 同 request_digest → 命中历史 versionId，事务内回退到 idempotent replay 路径；</li>
 *   <li>同 key_hash + 不同 request_digest → {@code IDEMPOTENCY_KEY_CONFLICT}（409）。</li>
 * </ul>
 *
 * <p>F02 R3 拒绝范围（抛 {@code SNAPSHOT_SCHEMA_INCOMPATIBLE} 或相关错误码）：
 * <ul>
 *   <li>Knowledge 引用（ADR-002 §D-2.4.1：R3 必拒冻结）—— MVP 草稿不暴露 knowledge 入口；</li>
 *   <li>Workflow / Collaboration DSL 引用 —— MVP 整段内联，不引用外部 snapshot；</li>
 *   <li>{@code source = RUNNER_LOCAL}（MVP 不实现三阶段 secret-bind → 抛 {@code RUNNER_LOCAL_SECRET_MISSING}）。</li>
 * </ul>
 *
 * <p>允许无 secret refs 冻结（用户要求）：{@code AGENT_DRAFT_INVALID} 仅在草稿上的 refs
 * 字段本身非法时抛。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentFreezeService {

    /** MVP envelope schema 版本；后续 bump 时同步更新 digest 算法。 */
    public static final short SNAPSHOT_SCHEMA_VERSION = 1;
    /** Idempotency-Key 保留时长；R3 默认值，cleanup job 后续 PR 加。 */
    public static final int IDEMPOTENCY_RETENTION_HOURS = 24;

    private final AgentMetadataRepository agentMetadataRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final AgentVersionSecretRefRepository secretRefRepository;
    private final AgentVersionFreezeIdempotencyRepository idempotencyRepository;
    private final GatewaySecretRepository gatewaySecretRepository;
    private final AgentVersionAuditWriter auditWriter;
    private final AgentDraftService draftService;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final AgentOwnershipResolver ownershipResolver;

    /**
     * 冻结入口。{@code idempotencyKey} 已经由 controller 哈希为 SHA-256 hex 字符串传入。
     *
     * <p>secret refs 由 service 从 {@code AgentMetadata.pending_secret_refs} 读取；
     * 不允许从 {@code FreezeAgentRequest} 传（F02 R3 = 草稿是唯一可变真相源）。
     */
    @Transactional
    public FreezeAgentResponse freeze(String agentId,
                                      String idempotencyKeyHash,
                                      String actor) {
        // 0) 锁草稿 → 读 pending secret refs
        AgentMetadata meta = lockDraftOrThrow(agentId);
        ownershipResolver.assertCanManage(meta);
        List<FreezeSecretRefItem> pendingRefs = draftService.readPendingSecretRefs(agentId);
        validateRefs(pendingRefs);

        // 0a) 草稿变更审计（与历史 freeze 比较 refs 数量；>0 触发即可）
        if (!pendingRefs.isEmpty()) {
            auditWriter.logDraftSecretRefChanged(actor, agentId,
                    historyRefsCount(agentId), pendingRefs.size());
        }

        // 1) 先基于锁定后的完整草稿生成 canonical envelope。request_digest 必须覆盖所有
        // freeze 输入，而不只是 SecretReference；否则同 key 修改 Prompt 后会错误回放旧版本。
        String canonicalEnvelope = canonicalizeDraft(meta, pendingRefs);
        String contentDigest = Sha256Digest.hex(canonicalEnvelope);
        String requestDigest = contentDigest;

        // 2) Idempotency 命中检查（DB-primary）
        Optional<AgentVersionFreezeIdempotency> existing =
                idempotencyRepository.findByAgentIdAndIdempotencyKeyHash(agentId, idempotencyKeyHash);
        if (existing.isPresent()) {
            return handleIdempotentReplay(agentId, idempotencyKeyHash, pendingRefs,
                    requestDigest, existing.get(), actor);
        }

        // 3) SecretReference 校验（CONTROL_PLANE 引用必须存在且 ACTIVE）
        validateSecretReferences(pendingRefs);

        // 4) INSERT agent_versions（FROZEN）
        int nextVersionNumber = agentVersionRepository.findMaxVersionNumber(agentId).orElse(0) + 1;
        AgentVersion version = AgentVersion.builder()
                .agentId(agentId)
                .versionNumber(nextVersionNumber)
                .configSnapshot(canonicalEnvelope)
                .changeDescription(pendingRefs.isEmpty() ? "freeze without secret refs" : null)
                .createdBy(actor)
                .status(AgentVersion.Status.FROZEN)
                .contentDigest(contentDigest)
                .snapshotSchemaVersion(SNAPSHOT_SCHEMA_VERSION)
                .frozenAt(LocalDateTime.now())
                .frozenBy(actor)
                .isActive(false)
                .build();
        version = agentVersionRepository.saveAndFlush(version);

        // 5) INSERT secret_refs（按 alias 升序）
        List<AgentVersionSecretRef> refs = new ArrayList<>();
        for (FreezeSecretRefItem item : pendingRefs) {
            refs.add(AgentVersionSecretRef.builder()
                    .agentVersionId(version.getId())
                    .alias(item.getAlias())
                    .source(item.getSource())
                    .secretId("CONTROL_PLANE".equals(item.getSource()) ? item.getSecretId() : null)
                    .localKey("RUNNER_LOCAL".equals(item.getSource()) ? item.getLocalKey() : null)
                    .required(item.isRequired())
                    .injectAs(item.getInjectAs())
                    .createdAt(LocalDateTime.now())
                    .build());
        }
        refs.sort(Comparator.comparing(AgentVersionSecretRef::getAlias));
        secretRefRepository.saveAll(refs);

        // 6) INSERT idempotency record
        AgentVersionFreezeIdempotency idem = AgentVersionFreezeIdempotency.builder()
                .agentId(agentId)
                .idempotencyKeyHash(idempotencyKeyHash)
                .requestDigest(requestDigest)
                .agentVersionId(version.getId())
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(IDEMPOTENCY_RETENTION_HOURS))
                .build();
        // 同一 agent 的 freeze 从方法开头即持有 agent_metadata 行锁，因此同 agent/key 不会并发
        // 穿透到这里；不要在 flush 失败后继续使用已标记 rollback-only 的事务。
        idempotencyRepository.saveAndFlush(idem);

        // 7) 首次 freeze 自动 active
        if (meta.getActiveVersionId() == null) {
            meta.setActiveVersionId(version.getId());
            meta.setSyncTime(LocalDateTime.now());
            agentMetadataRepository.save(meta);
        }

        // 8) Audit
        String secretRefsDigestDetail = toSecretRefsSummary(refs);
        auditWriter.logFrozen(actor, agentId, version.getId(), version.getVersionNumber(),
                contentDigest, SNAPSHOT_SCHEMA_VERSION, false, secretRefsDigestDetail);
        log.info("F02 freeze success agent={} version={} digest={}", agentId,
                version.getVersionNumber(), contentDigest);

        return FreezeAgentResponse.builder()
                .agentVersionId(version.getId())
                .agentId(agentId)
                .versionNumber(version.getVersionNumber())
                .status(version.getStatus().name())
                .contentDigest(contentDigest)
                .snapshotSchemaVersion(SNAPSHOT_SCHEMA_VERSION)
                .frozenAt(version.getFrozenAt())
                .frozenBy(version.getFrozenBy())
                .idempotentReplay(false)
                .build();
    }

    private FreezeAgentResponse handleIdempotentReplay(String agentId,
                                                       String idempotencyKeyHash,
                                                       List<FreezeSecretRefItem> pendingRefs,
                                                       String requestDigest,
                                                       AgentVersionFreezeIdempotency existing,
                                                       String actor) {
        if (!existing.getRequestDigest().equals(requestDigest)) {
            auditWriter.logFreezeRejected(actor, agentId, idempotencyKeyHash,
                    ErrorCode.IDEMPOTENCY_KEY_CONFLICT.getCode(),
                    "request_digest 与历史不匹配");
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                    "Idempotency-Key 与历史请求不一致（同一 key 仅允许相同 payload 回放）");
        }
        AgentVersion version = agentVersionRepository.findById(existing.getAgentVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_VERSION_NOT_FOUND,
                        "幂等命中但历史 AgentVersion 不存在：" + existing.getAgentVersionId()));
        auditWriter.logFrozen(actor, agentId, version.getId(), version.getVersionNumber(),
                version.getContentDigest(), version.getSnapshotSchemaVersion(),
                true, "(idempotent replay; secret refs 取自首次 freeze 的 bridge 行)");
        return FreezeAgentResponse.builder()
                .agentVersionId(version.getId())
                .agentId(agentId)
                .versionNumber(version.getVersionNumber())
                .status(version.getStatus().name())
                .contentDigest(version.getContentDigest())
                .snapshotSchemaVersion(version.getSnapshotSchemaVersion())
                .frozenAt(version.getFrozenAt())
                .frozenBy(version.getFrozenBy())
                .idempotentReplay(true)
                .build();
    }

    private AgentMetadata lockDraftOrThrow(String agentId) {
        AgentMetadata meta = entityManager.find(AgentMetadata.class, agentId, LockModeType.PESSIMISTIC_WRITE);
        if (meta == null) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND, "Agent 未找到：" + agentId);
        }
        return meta;
    }

    /** secret refs 列表校验；空 list 合法（F02 R3 允许无 secret ref 冻结）。 */
    private void validateRefs(List<FreezeSecretRefItem> refs) {
        for (FreezeSecretRefItem item : refs) {
            if ("RUNNER_LOCAL".equals(item.getSource())) {
                throw new BusinessException(ErrorCode.RUNNER_LOCAL_SECRET_MISSING,
                        "MVP 不支持 RUNNER_LOCAL 引用：" + item.getAlias());
            }
            if (!"CONTROL_PLANE".equals(item.getSource())) {
                throw new BusinessException(ErrorCode.SNAPSHOT_SCHEMA_INCOMPATIBLE,
                        "source 仅接受 CONTROL_PLANE 或 RUNNER_LOCAL，得到：" + item.getSource());
            }
            if (item.getSecretId() == null || item.getSecretId().isBlank()) {
                throw new BusinessException(ErrorCode.AGENT_DRAFT_INVALID,
                        "CONTROL_PLANE 引用必须填 secret_id：" + item.getAlias());
            }
        }
    }

    private Map<String, GatewaySecret> validateSecretReferences(List<FreezeSecretRefItem> refs) {
        List<String> ids = refs.stream()
                .filter(it -> "CONTROL_PLANE".equals(it.getSource()))
                .map(FreezeSecretRefItem::getSecretId)
                .distinct()
                .toList();
        Map<String, GatewaySecret> result = new LinkedHashMap<>();
        for (String sid : ids) {
            GatewaySecret gs = gatewaySecretRepository.findBySecretId(sid)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SECRET_REFERENCE_NOT_FOUND,
                            "引用的 Secret 不存在：" + sid));
            if (!gs.isActive()) {
                throw new BusinessException(ErrorCode.SECRET_REFERENCE_NOT_FOUND,
                        "引用的 Secret 非 ACTIVE 状态：" + sid);
            }
            result.put(sid, gs);
        }
        return result;
    }

    /** 历史已冻结版本中 secret refs 总数；用于草稿变更审计 diff。MVP 简化为当前 active 总数。 */
    private int historyRefsCount(String agentId) {
        // 不再嵌套 Optional.ofNullable -> findById(null) 这种坑写法；
        // 显式解 Optional，避免 mock strict 校验报错。
        Optional<AgentMetadata> metaOpt = agentMetadataRepository.findById(agentId);
        if (metaOpt.isEmpty()) {
            return 0;
        }
        String activeId = metaOpt.get().getActiveVersionId();
        if (activeId == null) {
            return 0;
        }
        return (int) secretRefRepository.countByAgentVersionId(activeId);
    }

    private Map<String, Object> buildEnvelope(AgentMetadata meta, List<FreezeSecretRefItem> refs) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("name", meta.getName());
        config.put("description", meta.getDescription());
        config.put("icon", meta.getIcon());
        config.put("mode", meta.getMode());
        config.put("temperature", meta.getTemperature());
        config.put("topP", meta.getTopP());
        config.put("maxTokens", meta.getMaxTokens());
        config.put("systemPrompt", meta.getSystemPrompt());
        config.put("parameters", meta.getParameters());
        config.put("toolCallingOverride", meta.getToolCallingOverride());
        config.put("mcpExposed", meta.isMcpExposed());

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("modelName", meta.getModelName());
        model.put("providerType", meta.getProviderType());
        model.put("baseUrl", null);

        List<Object> tools = new ArrayList<>();
        List<Object> knowledge = new ArrayList<>();
        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("inline", Boolean.TRUE);
        workflow.put("dsl", "");

        // secretRefs 按 alias 升序（envelope 内部排序；digest 与数组顺序无关）
        List<Map<String, Object>> secretRefs = new ArrayList<>();
        for (FreezeSecretRefItem it : refs) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("alias", it.getAlias());
            ref.put("source", it.getSource());
            if ("CONTROL_PLANE".equals(it.getSource())) {
                ref.put("secretId", it.getSecretId());
            } else {
                ref.put("localKey", it.getLocalKey());
            }
            ref.put("required", it.isRequired());
            ref.put("injectAs", it.getInjectAs());
            secretRefs.add(ref);
        }
        secretRefs.sort(Comparator.comparing(m -> String.valueOf(m.get("alias"))));

        Map<String, Object> env = new LinkedHashMap<>();
        env.put("snapshotSchemaVersion", (int) SNAPSHOT_SCHEMA_VERSION);
        env.put("config", config);
        env.put("model", model);
        env.put("tools", tools);
        env.put("knowledge", knowledge);
        env.put("workflow", workflow);
        env.put("secretRefs", secretRefs);
        return env;
    }

    private String canonicalizeDraft(AgentMetadata meta, List<FreezeSecretRefItem> refs) {
        return JcsCanonicalizer.canonicalize(toJson(buildEnvelope(meta, refs)));
    }

    /** 测试与审计校验使用；与 freeze 的 request_digest/content_digest 保持同一实现。 */
    String calculateContentDigest(AgentMetadata meta, List<FreezeSecretRefItem> refs) {
        return Sha256Digest.hex(canonicalizeDraft(meta, refs));
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SNAPSHOT_CANONICALIZE_FAILED,
                    "JSON 序列化失败", e);
        }
    }

    private String toSecretRefsSummary(List<AgentVersionSecretRef> refs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < refs.size(); i++) {
            AgentVersionSecretRef r = refs.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"alias\":\"").append(r.getAlias()).append("\",")
                    .append("\"source\":\"").append(r.getSource()).append("\",")
                    .append("\"ref_id\":\"").append(r.getSecretId() != null ? r.getSecretId() : r.getLocalKey()).append("\",")
                    .append("\"injectAs\":\"").append(r.getInjectAs()).append("\"}");
        }
        sb.append(']');
        return sb.toString();
    }
}
