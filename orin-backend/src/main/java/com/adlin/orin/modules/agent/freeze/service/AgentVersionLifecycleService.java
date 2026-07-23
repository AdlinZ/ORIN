package com.adlin.orin.modules.agent.freeze.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.entity.AgentVersion;
import com.adlin.orin.modules.agent.freeze.audit.AgentVersionAuditWriter;
import com.adlin.orin.modules.agent.freeze.dto.AgentVersionDetailResponse;
import com.adlin.orin.modules.agent.freeze.dto.AgentVersionListItem;
import com.adlin.orin.modules.agent.freeze.dto.AgentVersionSecretRefView;
import com.adlin.orin.modules.agent.freeze.entity.AgentVersionSecretRef;
import com.adlin.orin.modules.agent.freeze.repository.AgentVersionSecretRefRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.repository.AgentVersionRepository;
import com.adlin.orin.modules.agent.service.AgentOwnershipResolver;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.repository.GatewaySecretRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * F02 AgentVersion lifecycle：{@code switchActive} + {@code deprecate} +
 * 列表 / 详情读取。
 *
 * <p>ADR-002 v4.1 §D-2.1.1 规则实现：
 * <ul>
 *   <li>switchActive 只切 {@code AgentMetadata.active_version_id}，<b>不</b>触发 deprecate；</li>
 *   <li>旧 active 版本要 deprecate 必须由 Operator 额外 POST {@code /deprecate}；</li>
 *   <li>切到 DEPRECATED version → 抛 {@code RUN_VERSION_RETIRED}（409，资源存在但已退役）；</li>
 *   <li>目标 versionId == 当前 active_version_id → 幂等，不写 audit。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentVersionLifecycleService {

    private final AgentMetadataRepository agentMetadataRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final AgentVersionSecretRefRepository secretRefRepository;
    private final GatewaySecretRepository gatewaySecretRepository;
    private final AgentVersionAuditWriter auditWriter;
    private final EntityManager entityManager;
    private final AgentOwnershipResolver ownershipResolver;

    /**
     * 切 active version（PUT /api/v1/agents/{agentId}/active-version）。
     * 仅切换 {@code AgentMetadata.active_version_id}，不触发 deprecate。
     */
    @Transactional
    public AgentVersionDetailResponse switchActiveVersion(String agentId, String versionId, String actor) {
        // 锁草稿（同一事务防止并发切）
        AgentMetadata meta = entityManager.find(AgentMetadata.class, agentId, LockModeType.PESSIMISTIC_WRITE);
        if (meta == null) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND, "Agent 未找到：" + agentId);
        }
        ownershipResolver.assertCanManage(meta);

        AgentVersion target = agentVersionRepository.findByIdAndAgentId(versionId, agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_VERSION_NOT_FOUND,
                        "AgentVersion " + versionId + " 不属于 agent " + agentId));

        if (target.getStatus() == AgentVersion.Status.DEPRECATED) {
            auditWriter.logActiveSwitchRejected(actor, agentId, versionId,
                    ErrorCode.RUN_VERSION_RETIRED.getCode());
            throw new BusinessException(ErrorCode.RUN_VERSION_RETIRED,
                    "目标 AgentVersion 已退役，无法激活：" + versionId);
        }

        // 幂等：同 versionId 不必写 audit
        boolean isReplay = versionId.equals(meta.getActiveVersionId());
        if (!isReplay) {
            String fromVersionId = meta.getActiveVersionId();
            meta.setActiveVersionId(versionId);
            meta.setSyncTime(LocalDateTime.now());
            agentMetadataRepository.save(meta);
            auditWriter.logActiveSwitched(actor, agentId, fromVersionId, versionId);
            log.info("F02 active switched agent={} {} -> {}", agentId, fromVersionId, versionId);
        } else {
            log.info("F02 active switch noop (already active) agent={} versionId={}", agentId, versionId);
        }

        return toDetailResponse(target, true);
    }

    /**
     * Deprecate 一个 FROZEN version（POST .../versions/{versionId}/deprecate）。
     * <ul>
     *   <li>若 versionId 等于当前 active_version_id → 抛 {@code RUN_VERSION_RETIRED}（409）；</li>
     *   <li>已 DEPRECATED 不可再 deprecate（避免重写 deprecated_at）；</li>
     *   <li>受控字段：status / deprecated_at / deprecated_by / deprecation_reason。</li>
     * </ul>
     */
    @Transactional
    public AgentVersionDetailResponse deprecateVersion(String agentId, String versionId,
                                                       String reason, String actor) {
        AgentMetadata meta = entityManager.find(AgentMetadata.class, agentId, LockModeType.PESSIMISTIC_WRITE);
        if (meta == null) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND, "Agent 未找到：" + agentId);
        }
        ownershipResolver.assertCanManage(meta);

        AgentVersion target = agentVersionRepository.findByIdAndAgentId(versionId, agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_VERSION_NOT_FOUND,
                        "AgentVersion " + versionId + " 不属于 agent " + agentId));

        if (target.getStatus() == AgentVersion.Status.DEPRECATED) {
            auditWriter.logDeprecateRejected(actor, agentId, versionId,
                    ErrorCode.RUN_VERSION_RETIRED.getCode());
            throw new BusinessException(ErrorCode.RUN_VERSION_RETIRED,
                    "AgentVersion 已是 DEPRECATED 状态，无法重复 deprecate：" + versionId);
        }
        if (versionId.equals(meta.getActiveVersionId())) {
            auditWriter.logDeprecateRejected(actor, agentId, versionId,
                    ErrorCode.RUN_VERSION_RETIRED.getCode());
            throw new BusinessException(ErrorCode.RUN_VERSION_RETIRED,
                    "当前 active 版本必须先切到其他 FROZEN 版本才能 deprecate：" + versionId);
        }

        LocalDateTime now = LocalDateTime.now();
        target.setStatus(AgentVersion.Status.DEPRECATED);
        target.setDeprecatedAt(now);
        target.setDeprecatedBy(actor);
        target.setDeprecationReason(reason);
        agentVersionRepository.save(target);
        auditWriter.logDeprecated(actor, agentId, versionId, reason);
        log.info("F02 deprecated agent={} versionId={} reason={}", agentId, versionId, reason);

        return toDetailResponse(target, false);
    }

    @Transactional(readOnly = true)
    public List<AgentVersionListItem> listVersions(String agentId) {
        AgentMetadata meta = loadAgentOrThrow(agentId);
        ownershipResolver.assertCanManage(meta);
        String activeId = meta.getActiveVersionId();

        List<AgentVersion> versions = agentVersionRepository
                .findByAgentIdOrderByVersionNumberDesc(agentId);
        List<AgentVersionListItem> out = new ArrayList<>();
        for (AgentVersion v : versions) {
            out.add(AgentVersionListItem.builder()
                    .agentVersionId(v.getId())
                    .versionNumber(v.getVersionNumber())
                    .versionTag(v.getVersionTag())
                    .status(v.getStatus().name())
                    .contentDigest(v.getContentDigest())
                    .snapshotSchemaVersion(v.getSnapshotSchemaVersion())
                    .frozenAt(v.getFrozenAt())
                    .createdBy(v.getCreatedBy())
                    .isActive(v.getId().equals(activeId))
                    .build());
        }
        return out;
    }

    @Transactional(readOnly = true)
    public AgentVersionDetailResponse getVersion(String agentId, String versionId) {
        AgentMetadata meta = loadAgentOrThrow(agentId);
        ownershipResolver.assertCanManage(meta);
        AgentVersion v = agentVersionRepository.findByIdAndAgentId(versionId, agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_VERSION_NOT_FOUND,
                        "AgentVersion " + versionId + " 不属于 agent " + agentId));
        String activeId = meta.getActiveVersionId();
        return toDetailResponse(v, versionId.equals(activeId));
    }

    private AgentMetadata loadAgentOrThrow(String agentId) {
        return agentMetadataRepository.findById(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND,
                        "Agent 未找到：" + agentId));
    }

    private AgentVersionDetailResponse toDetailResponse(AgentVersion v, boolean isActive) {
        List<AgentVersionSecretRef> refs =
                new ArrayList<>(secretRefRepository.findByAgentVersionIdOrderByAliasAsc(v.getId()));
        List<AgentVersionSecretRefView> refViews = new ArrayList<>();
        // 批量读 gateway_secret 摘要（避免每行单独 SQL）
        List<String> secretIds = refs.stream()
                .map(AgentVersionSecretRef::getSecretId)
                .filter(s -> s != null)
                .distinct()
                .toList();
        Map<String, GatewaySecret> byId = new HashMap<>();
        for (String sid : secretIds) {
            gatewaySecretRepository.findBySecretId(sid).ifPresent(gs -> byId.put(sid, gs));
        }
        // repository 已按 alias 升序返回；此处防御排序兼容测试 mock 出的 immutable list
        refs.sort(Comparator.comparing(AgentVersionSecretRef::getAlias));
        for (AgentVersionSecretRef r : refs) {
            GatewaySecret gs = r.getSecretId() != null ? byId.get(r.getSecretId()) : null;
            refViews.add(AgentVersionSecretRefView.builder()
                    .alias(r.getAlias())
                    .source(r.getSource())
                    .secretId(r.getSecretId())
                    .localKey(r.getLocalKey())
                    .required(r.isRequired())
                    .injectAs(r.getInjectAs())
                    .keyPrefix(gs != null ? gs.getKeyPrefix() : null)
                    .last4(gs != null ? gs.getLast4() : null)
                    .build());
        }

        return AgentVersionDetailResponse.builder()
                .agentVersionId(v.getId())
                .agentId(v.getAgentId())
                .versionNumber(v.getVersionNumber())
                .versionTag(v.getVersionTag())
                .status(v.getStatus().name())
                .contentDigest(v.getContentDigest())
                .snapshotSchemaVersion(v.getSnapshotSchemaVersion())
                .changeDescription(v.getChangeDescription())
                .frozenAt(v.getFrozenAt())
                .frozenBy(v.getFrozenBy())
                .createdBy(v.getCreatedBy())
                .createdAt(v.getCreatedAt())
                .isActive(isActive)
                .secretRefs(refViews)
                .deprecationReason(v.getDeprecationReason())
                .deprecatedAt(v.getDeprecatedAt())
                .deprecatedBy(v.getDeprecatedBy())
                .build();
    }

    /**
     * 列出供前端"添加 SecretReference"下拉框使用的 GatewaySecret 摘要。
     * 来源：apikey 模块 gateway_secrets（status=ACTIVE）。
     */
    @Transactional(readOnly = true)
    public List<com.adlin.orin.modules.agent.freeze.dto.AgentSecretSummary> listActiveGatewaySecrets() {
        List<GatewaySecret> active = gatewaySecretRepository.findAll().stream()
                .filter(GatewaySecret::isActive)
                .sorted(Comparator.comparing(GatewaySecret::getSecretId))
                .toList();
        List<com.adlin.orin.modules.agent.freeze.dto.AgentSecretSummary> out = new ArrayList<>();
        for (GatewaySecret gs : active) {
            out.add(com.adlin.orin.modules.agent.freeze.dto.AgentSecretSummary.builder()
                    .secretId(gs.getSecretId())
                    .secretType(gs.getSecretType() != null ? gs.getSecretType().name() : null)
                    .provider(gs.getProvider())
                    .keyPrefix(gs.getKeyPrefix())
                    .last4(gs.getLast4())
                    .baseUrl(gs.getBaseUrl())
                    .build());
        }
        return out;
    }

    /** 仅内部调试用：检查 idempotency 命中但 version 行不存在（异常路径）。 */
    Optional<AgentVersion> findVersion(String versionId) {
        return agentVersionRepository.findById(versionId);
    }
}
