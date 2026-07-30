package com.adlin.orin.modules.agent.service;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.entity.AgentVersion;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.repository.AgentVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 智能体版本管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentVersionService {

    private final AgentVersionRepository versionRepository;
    private final AgentMetadataRepository agentRepository;
    private final ObjectMapper objectMapper;

    /**
     * 创建新版本（自动保存当前配置）
     */
    @Transactional
    public AgentVersion createVersion(String agentId, String changeDescription, String createdBy) {
        // 获取智能体当前配置
        AgentMetadata agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found: " + agentId));

        // 获取下一个版本号
        Integer nextVersionNumber = versionRepository.findMaxVersionNumber(agentId)
                .map(max -> max + 1)
                .orElse(1);

        // 序列化配置为 JSON
        String configSnapshot;
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("agentId", agent.getAgentId());
            config.put("name", agent.getName());
            config.put("description", agent.getDescription());
            config.put("icon", agent.getIcon());
            config.put("mode", agent.getMode());
            config.put("modelName", agent.getModelName());
            config.put("providerType", agent.getProviderType());

            configSnapshot = objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize agent config", e);
        }

        // 将之前的激活版本设置为非激活
        versionRepository.findByAgentIdAndIsActiveTrue(agentId)
                .ifPresent(activeVersion -> {
                    activeVersion.setIsActive(false);
                    versionRepository.save(activeVersion);
                });

        // 创建新版本
        AgentVersion version = AgentVersion.builder()
                .agentId(agentId)
                .versionNumber(nextVersionNumber)
                .configSnapshot(configSnapshot)
                .changeDescription(changeDescription)
                .createdBy(createdBy != null ? createdBy : "system")
                .isActive(true)
                .build();

        version = versionRepository.save(version);
        log.info("Created version {} for agent {}", nextVersionNumber, agentId);

        return version;
    }

    /**
     * 获取指定智能体的所有版本
     */
    public List<AgentVersion> getVersions(String agentId) {
        return versionRepository.findByAgentIdOrderByVersionNumberDesc(agentId);
    }

    /**
     * 获取特定版本详情
     */
    public AgentVersion getVersion(String agentId, Integer versionNumber) {
        return versionRepository.findByAgentIdAndVersionNumber(agentId, versionNumber)
                .orElseThrow(() -> new RuntimeException(
                        "Version not found: agent=" + agentId + ", version=" + versionNumber));
    }

    /**
     * 获取当前激活版本
     */
    public Optional<AgentVersion> getActiveVersion(String agentId) {
        return versionRepository.findByAgentIdAndIsActiveTrue(agentId);
    }

    /**
     * 回滚到指定版本
     */
    @Transactional
    public AgentMetadata rollbackToVersion(String agentId, String versionId) {
        // 获取目标版本
        AgentVersion targetVersion = versionRepository.findById(versionId)
                .orElseThrow(() -> new RuntimeException("Version not found: " + versionId));

        if (!targetVersion.getAgentId().equals(agentId)) {
            throw new RuntimeException("Version does not belong to agent: " + agentId);
        }

        // 获取智能体
        AgentMetadata agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found: " + agentId));

        // 解析配置快照
        Map<String, Object> config;
        try {
            config = objectMapper.readValue(targetVersion.getConfigSnapshot(), Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse version config", e);
        }

        // 应用配置
        agent.setName((String) config.get("name"));
        agent.setDescription((String) config.get("description"));
        agent.setIcon((String) config.get("icon"));
        agent.setMode((String) config.get("mode"));
        agent.setModelName((String) config.get("modelName"));
        agent.setProviderType((String) config.get("providerType"));

        agent = agentRepository.save(agent);

        // 更新激活版本标记
        versionRepository.findByAgentIdAndIsActiveTrue(agentId)
                .ifPresent(activeVersion -> {
                    activeVersion.setIsActive(false);
                    versionRepository.save(activeVersion);
                });

        targetVersion.setIsActive(true);
        versionRepository.save(targetVersion);

        log.info("Rolled back agent {} to version {}", agentId, targetVersion.getVersionNumber());

        return agent;
    }

    /**
     * 对比两个版本
     */
    public Map<String, Object> compareVersions(String agentId, Integer version1, Integer version2) {
        AgentVersion v1 = getVersion(agentId, version1);
        AgentVersion v2 = getVersion(agentId, version2);

        Map<String, Object> result = new HashMap<>();
        result.put("version1", Map.of(
                "versionNumber", v1.getVersionNumber(),
                "createdAt", v1.getCreatedAt(),
                "config", parseConfig(v1.getConfigSnapshot())));
        result.put("version2", Map.of(
                "versionNumber", v2.getVersionNumber(),
                "createdAt", v2.getCreatedAt(),
                "config", parseConfig(v2.getConfigSnapshot())));
        result.put("differences", findDifferences(
                parseConfig(v1.getConfigSnapshot()),
                parseConfig(v2.getConfigSnapshot())));

        return result;
    }

    /**
     * 删除指定版本
     */
    @Transactional
    public void deleteVersion(String versionId) {
        AgentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new RuntimeException("Version not found: " + versionId));

        if (Boolean.TRUE.equals(version.getIsActive())) {
            throw new RuntimeException("Cannot delete active version");
        }

        versionRepository.delete(version);
        log.info("Deleted version {} for agent {}", version.getVersionNumber(), version.getAgentId());
    }

    /**
     * 解析配置 JSON
     */
    private Map<String, Object> parseConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse config JSON", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 找出两个配置的差异
     */
    private List<Map<String, Object>> findDifferences(Map<String, Object> config1, Map<String, Object> config2) {
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
