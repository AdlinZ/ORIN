package com.adlin.orin.modules.agent.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.entity.AgentVersion;
import com.adlin.orin.modules.agent.freeze.service.AgentVersionLifecycleService;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.repository.AgentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 智能体版本管理服务（F02 R3 适配版）。
 *
 * <p>R3 行为变更：
 * <ul>
 *   <li>{@link #createVersion}：保留入口为向后兼容；不再创建"未冻结"版本——改抛 {@code AGENT_VERSION_FROZEN}。
 *       旧调用方应迁移到 {@code POST /api/v1/agents/{id}/versions}（F02 freeze）；</li>
 *   <li>{@link #rollbackToVersion}：<b>重定义语义</b>为"切换 active_version_id 指针"，
 *       不再回写 {@code AgentMetadata} 字段；委托给 {@code AgentVersionLifecycleService.switchActiveVersion}；</li>
 *   <li>{@link #getActiveVersion}：从 {@code AgentMetadata.active_version_id} 解析；</li>
 *   <li>{@link #compareVersions}：保留为简单只读对比（无 UI 联动），仍可用；</li>
 *   <li>{@link #deleteVersion}：无条件抛 {@code AGENT_VERSION_DELETE_FORBIDDEN}（ADR-002 §D-2.2）。</li>
 * </ul>
 *
 * <p>本服务**仅**为兼容旧 controller endpoint 与旧测试 bean 留存；新代码请使用 {@code AgentFreezeService}。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentVersionService {

    private final AgentVersionRepository versionRepository;
    private final AgentMetadataRepository agentRepository;
    private final AgentVersionLifecycleService lifecycleService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * @deprecated since F02 R3：仅 controller 兼容路由调用；新代码用 {@code AgentFreezeService.freeze}。
     */
    @Deprecated
    @Transactional
    public AgentVersion createVersion(String agentId, String changeDescription, String createdBy) {
        throw new BusinessException(ErrorCode.AGENT_VERSION_FROZEN,
                "createVersion 已被 F02 R3 替换；请调用 POST /api/v1/agents/" + agentId
                        + "/versions (freeze)；提交前必须带 Idempotency-Key HTTP 头");
    }

    /**
     * 列出指定 agent 的所有版本（按版本号降序）。
     */
    public List<AgentVersion> getVersions(String agentId) {
        return versionRepository.findByAgentIdOrderByVersionNumberDesc(agentId);
    }

    /**
     * 获取特定版本号；不存在抛 {@code AGENT_VERSION_NOT_FOUND}。
     */
    public AgentVersion getVersion(String agentId, Integer versionNumber) {
        return versionRepository.findByAgentIdAndVersionNumber(agentId, versionNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_VERSION_NOT_FOUND,
                        "Version not found: agent=" + agentId + ", version=" + versionNumber));
    }

    /**
     * 读取当前 active version：从 {@code AgentMetadata.active_version_id} 解析。
     */
    public Optional<AgentVersion> getActiveVersion(String agentId) {
        return agentRepository.findById(agentId)
                .flatMap(meta -> meta.getActiveVersionId() == null
                        ? Optional.empty()
                        : versionRepository.findById(meta.getActiveVersionId()));
    }

    /**
     * 重定义为切指针：委托 {@code AgentVersionLifecycleService.switchActiveVersion}。
     * 旧行为（回写 AgentMetadata 字段 + is_active=true）已删除。
     */
    @Transactional
    public AgentMetadata rollbackToVersion(String agentId, String versionId) {
        lifecycleService.switchActiveVersion(agentId, versionId, "legacy-rollback-endpoint");
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND,
                        "Agent 未找到：" + agentId));
    }

    /**
     * 对比两个版本（保留只读对比；不再参与 freeze 流程）。
     */
    public Map<String, Object> compareVersions(String agentId, Integer version1, Integer version2) {
        AgentVersion v1 = getVersion(agentId, version1);
        AgentVersion v2 = getVersion(agentId, version2);

        Map<String, Object> result = new HashMap<>();
        result.put("version1", Map.of(
                "versionNumber", v1.getVersionNumber(),
                "createdAt", v1.getCreatedAt(),
                "status", v1.getStatus(),
                "contentDigest", v1.getContentDigest(),
                "config", parseConfig(v1.getConfigSnapshot())));
        result.put("version2", Map.of(
                "versionNumber", v2.getVersionNumber(),
                "createdAt", v2.getCreatedAt(),
                "status", v2.getStatus(),
                "contentDigest", v2.getContentDigest(),
                "config", parseConfig(v2.getConfigSnapshot())));
        result.put("differences", findDifferences(
                parseConfig(v1.getConfigSnapshot()),
                parseConfig(v2.getConfigSnapshot())));
        return result;
    }

    /**
     * 删除版本永远禁止（ADR-002 §D-2.2）。F02 R3 无条件抛错。
     */
    @Transactional
    public void deleteVersion(String versionId) {
        throw new BusinessException(ErrorCode.AGENT_VERSION_DELETE_FORBIDDEN,
                "AgentVersion 不可删除；请使用 POST /api/v1/agents/{agentId}/versions/"
                        + versionId + "/deprecate 标记 DEPRECATED");
    }

    private Map<String, Object> parseConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse config JSON", e);
            return Collections.emptyMap();
        }
    }

    private List<Map<String, Object>> findDifferences(Map<String, Object> config1,
                                                      Map<String, Object> config2) {
        List<Map<String, Object>> differences = new ArrayList<>();

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(config1.keySet());
        allKeys.addAll(config2.keySet());

        for (String key : allKeys) {
            Object value1 = config1.get(key);
            Object value2 = config2.get(key);

            if (!Objects.equals(value1, value2)) {
                differences.add(Map.of(
                        "field", key,
                        "oldValue", value1 != null ? value1 : "null",
                        "newValue", value2 != null ? value2 : "null"));
            }
        }

        return differences;
    }
}
