package com.adlin.orin.modules.agent.freeze.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.entity.AgentVersion;
import com.adlin.orin.modules.agent.freeze.audit.AgentVersionAuditWriter;
import com.adlin.orin.modules.agent.freeze.dto.AgentDraftResponse;
import com.adlin.orin.modules.agent.freeze.dto.AgentDraftUpsertRequest;
import com.adlin.orin.modules.agent.freeze.dto.FreezeSecretRefItem;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.repository.AgentVersionRepository;
import com.adlin.orin.modules.agent.service.AgentOwnershipResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * F02 Agent 草稿服务。
 *
 * <p>职责隔离：把 {@code AgentMetadata} 的读取 / upsert / 持久化 SecretReference 草稿集中到一处，
 * 避免冻结逻辑与旧 {@code AgentManageService} 互相穿透。
 *
 * <p>关键边界（ADR-002 v4.1 §D-2.1）：
 * <ul>
 *   <li><b>草稿始终可变</b>：FROZEN 版本状态属于 {@code AgentVersion}，不阻塞 {@code AgentMetadata}
 *       编辑；用户可以继续修改草稿生成 v2/v3；</li>
 *   <li><b>SecretReference 草稿可保存</b>：{@code pending_secret_refs} JSON 列直接保存草稿上的 secret refs；
 *       freeze 时 service 从这里读取；允许无 secret refs 的合法 Agent 冻结；</li>
 *   <li><b>真 upsert</b>：upsertDraft 在 agentId 不存在时直接 INSERT；列表来源是真实数据库。F01-era "POST
 *       /onboard → 用前端生成的 agentId 写库"已被本服务取代。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDraftService {

    private final AgentMetadataRepository agentMetadataRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final AgentVersionAuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    private final AgentOwnershipResolver ownershipResolver;

    /**
     * 创建一个全新 Agent（POST /api/v1/agents）。
     * <p>不依赖前端传入的 agentId，由后端生成 UUID；owner 始终来自 JWT principal，
     * 普通用户不能通过请求体覆盖资源归属。
     */
    @Transactional
    public AgentDraftResponse createAgent(String name, String description, String actor) {
        String agentId = "ag_" + UUID.randomUUID();
        Long ownerUserId = ownershipResolver.resolveFromCurrentRequest();
        AgentMetadata meta = AgentMetadata.builder()
                .agentId(agentId)
                .ownerUserId(ownerUserId)
                .name(name != null ? name : agentId)
                .description(description)
                .mode("agent")
                .providerType("OPENAI")
                .viewType("CHAT")
                .mcpExposed(false)
                .temperature(0.7)
                .topP(1.0)
                .maxTokens(2048)
                .syncTime(LocalDateTime.now())
                .build();
        agentMetadataRepository.save(meta);
        auditWriter.logAgentCreated(actor, agentId, name);
        log.info("F02 agent created agentId={} name={} actor={}", agentId, name, actor);
        return toDraftResponse(meta);
    }

    /**
     * Upsert 草稿（PUT /api/v1/agents/{agentId}/draft）。
     *
     * <p>只更新由 {@code POST /api/v1/agents} 创建的既有草稿；不存在时返回 404，防止调用方
     * 通过自选 agentId 绕过 owner 初始化。<b>不</b>因为 {@code active_version_id != null}
     * 拒绝修改（这是上版错误的"冻结后草稿锁死"行为），
     * F02 故事要求用户能多次修改草稿并 freeze 出 v1/v2/v3。
     */
    @Transactional
    public AgentDraftResponse upsertDraft(String agentId, AgentDraftUpsertRequest req,
                                          String pendingSecretRefsJson,
                                          String actor) {
        AgentMetadata meta = loadAgentOrThrow(agentId);
        ownershipResolver.assertCanManage(meta);
        String previousSecretRefs = meta.getPendingSecretRefs();

        meta.setName(req.getName());
        meta.setDescription(req.getDescription());
        meta.setIcon(req.getIcon());
        meta.setMode(req.getMode());
        meta.setModelName(req.getModelName());
        meta.setProviderType(req.getProviderType());
        meta.setViewType(req.getViewType());
        meta.setSystemPrompt(req.getSystemPrompt());
        meta.setParameters(req.getParameters());
        if (req.getTemperature() != null) meta.setTemperature(req.getTemperature());
        if (req.getTopP() != null) meta.setTopP(req.getTopP());
        if (req.getMaxTokens() != null) meta.setMaxTokens(req.getMaxTokens());
        if (req.getToolCallingOverride() != null) meta.setToolCallingOverride(req.getToolCallingOverride());
        if (req.getMcpExposed() != null) meta.setMcpExposed(Boolean.TRUE.equals(req.getMcpExposed()));
        meta.setSyncTime(LocalDateTime.now());

        // pendingSecretRefsJson 已由 controller 在 Bean Validation 后序列化为标准 JSON 字符串；
        // 不在这里 serialize 后再 deserialize，避免反复 jackson 抖动。
        meta.setPendingSecretRefs(pendingSecretRefsJson);

        agentMetadataRepository.save(meta);
        if (!Objects.equals(previousSecretRefs, pendingSecretRefsJson)) {
            auditWriter.logDraftSecretRefChanged(actor, agentId,
                    countSecretRefs(previousSecretRefs), countSecretRefs(pendingSecretRefsJson));
        }
        auditWriter.logDraftUpdated(actor, agentId, req.getChangeDescription());
        log.info("F02 draft updated agent={} actor={}", agentId, actor);
        return toDraftResponse(meta);
    }

    /** 读取草稿详情（GET /api/v1/agents/{agentId}/draft）。 */
    @Transactional(readOnly = true)
    public AgentDraftResponse getDraft(String agentId) {
        AgentMetadata meta = loadAgentOrThrow(agentId);
        ownershipResolver.assertCanManage(meta);
        return toDraftResponse(meta);
    }

    /**
     * 列出 Agent 草稿摘要，并解析 active version 指针。
     *
     * <p>列表和详情必须使用同一投影，否则前端会把已经冻结的 Agent 误判为 DRAFT，
     * 进而无法在 Run 创建流程中选择它。
     */
    @Transactional(readOnly = true)
    public List<AgentDraftResponse> listDrafts() {
        return agentMetadataRepository.findAll().stream()
                .map(this::toDraftResponse)
                .toList();
    }

    /**
     * 读取 pending_secret_refs 反序列化结果，供 freeze / detail 复用。
     * 空 / null → 空列表（合法：草稿上无 secret refs）。
     */
    @Transactional(readOnly = true)
    public List<FreezeSecretRefItem> readPendingSecretRefs(String agentId) {
        AgentMetadata meta = loadAgentOrThrow(agentId);
        ownershipResolver.assertCanManage(meta);
        String json = meta.getPendingSecretRefs();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<FreezeSecretRefItem>>() {
            });
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SNAPSHOT_CANONICALIZE_FAILED,
                    "pendingSecretRefs 反序列化失败：" + e.getMessage(), e);
        }
    }

    private AgentMetadata loadAgentOrThrow(String agentId) {
        return agentMetadataRepository.findById(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND,
                        "Agent 未找到：" + agentId));
    }

    private int countSecretRefs(String json) {
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            return objectMapper.readTree(json).size();
        } catch (Exception e) {
            return 0;
        }
    }

    private AgentDraftResponse toDraftResponse(AgentMetadata meta) {
        AgentDraftResponse.AgentDraftResponseBuilder b = AgentDraftResponse.builder()
                .agentId(meta.getAgentId())
                .ownerUserId(meta.getOwnerUserId())
                .name(meta.getName())
                .description(meta.getDescription())
                .icon(meta.getIcon())
                .mode(meta.getMode())
                .modelName(meta.getModelName())
                .providerType(meta.getProviderType())
                .viewType(meta.getViewType())
                .temperature(meta.getTemperature())
                .topP(meta.getTopP())
                .maxTokens(meta.getMaxTokens())
                .toolCallingOverride(meta.getToolCallingOverride())
                .mcpExposed(meta.isMcpExposed())
                .systemPrompt(meta.getSystemPrompt())
                .parameters(meta.getParameters())
                .activeVersionId(meta.getActiveVersionId())
                .pendingSecretRefs(meta.getPendingSecretRefs())
                .syncTime(meta.getSyncTime());

        if (meta.getActiveVersionId() != null) {
            agentVersionRepository.findById(meta.getActiveVersionId())
                    .ifPresent(v -> b.activeVersionNumber(v.getVersionNumber())
                            .activeVersionDigest(v.getContentDigest())
                            .activeVersionStatus(v.getStatus().name()));
        }
        return b.build();
    }
}
