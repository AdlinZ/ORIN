package com.adlin.orin.modules.agent.service;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.model.service.ZhipuIntegrationService;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 智谱AI Agent管理服务
 */
@Service
public class ZhipuAgentManageService implements AgentManageService {

    private static final Logger log = LoggerFactory.getLogger(ZhipuAgentManageService.class);

    private final ZhipuIntegrationService zhipuIntegrationService;
    private final AgentAccessProfileRepository accessProfileRepository;
    private final AgentMetadataRepository metadataRepository;
    private final AgentHealthStatusRepository healthStatusRepository;
    private final com.adlin.orin.modules.model.repository.ModelMetadataRepository modelMetadataRepository;

    @Autowired
    public ZhipuAgentManageService(
            ZhipuIntegrationService zhipuIntegrationService,
            AgentAccessProfileRepository accessProfileRepository,
            AgentMetadataRepository metadataRepository,
            AgentHealthStatusRepository healthStatusRepository,
            com.adlin.orin.modules.model.repository.ModelMetadataRepository modelMetadataRepository) {
        this.zhipuIntegrationService = zhipuIntegrationService;
        this.accessProfileRepository = accessProfileRepository;
        this.metadataRepository = metadataRepository;
        this.healthStatusRepository = healthStatusRepository;
        this.modelMetadataRepository = modelMetadataRepository;
    }

    @Override
    public AgentMetadata onboardAgent(String endpointUrl, String apiKey, String model) {
        return onboardAgent(endpointUrl, apiKey, model, null, 1.0);
    }

    /**
     * 接入智谱AI Agent
     */
    public AgentMetadata onboardAgent(String endpointUrl, String apiKey, String model, String agentName,
            Double temperature) {
        String modelName = model != null && !model.isEmpty() ? model : "glm-4";
        double temp = temperature != null ? temperature : 1.0;

        // 测试连接
        zhipuIntegrationService.testConnection(endpointUrl, apiKey, modelName);

        String generatedId = UUID.randomUUID().toString().substring(0, 8);

        // 生成Agent名称
        String finalName;
        if (agentName != null && !agentName.trim().isEmpty()) {
            finalName = agentName;
        } else {
            finalName = "智谱AI-" + modelName + "-" + generatedId.substring(0, 4);
        }

        // 保存访问配置
        AgentAccessProfile profile = AgentAccessProfile.builder()
                .agentId(generatedId)
                .endpointUrl(endpointUrl)
                .apiKey(apiKey)
                .datasetApiKey(null)
                .createdAt(LocalDateTime.now())
                .connectionStatus("VALID")
                .build();
        accessProfileRepository.save(profile);

        // 保存元数据
        AgentMetadata metadata = AgentMetadata.builder()
                .agentId(generatedId)
                .name(finalName)
                .description("通过智谱AI接入的智能体: " + modelName)
                .mode("chat")
                .icon("🤖")
                .modelName(modelName)
                .providerType("Zhipu")
                .syncTime(LocalDateTime.now())
                .build();

        // 设置temperature参数（builder不支持，需要单独设置）
        metadata.setTemperature(temp);
        String viewType = determineViewType(modelName);
        metadata.setViewType(viewType);
        metadataRepository.save(metadata);

        // 保存健康状态
        AgentHealthStatus health = AgentHealthStatus.builder()
                .agentId(generatedId)
                .agentName(finalName)
                .status(AgentHealthStatus.Status.RUNNING)
                .healthScore(100)
                .lastHeartbeat(System.currentTimeMillis())
                .providerType("Zhipu")
                .mode("chat")
                .modelName(modelName)
                .viewType(viewType)
                .build();
        healthStatusRepository.save(health);

        log.info("Zhipu AI agent onboarded successfully: {}", finalName);
        return metadata;
    }

    /**
     * Get viewType directly from model type in ModelMetadata
     */
    private String determineViewType(String modelName) {
        if (modelName == null)
            return "CHAT";

        try {
            java.util.Optional<com.adlin.orin.modules.model.entity.ModelMetadata> modelOpt = modelMetadataRepository
                    .findByModelId(modelName);

            if (modelOpt.isPresent()) {
                String modelType = modelOpt.get().getType();
                if (modelType != null && !modelType.isEmpty()) {
                    return modelType; // 直接返回模型类型，不做映射
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get viewType for model {}: {}", modelName, e.getMessage());
        }

        return "CHAT";
    }

    @Override
    public List<AgentMetadata> getAllAgents() {
        return metadataRepository.findAll();
    }

    @Override
    public void updateAgent(String agentId, com.adlin.orin.modules.agent.dto.AgentOnboardRequest request) {
        log.info("Updating Zhipu AI agent: {}", agentId);

        AgentAccessProfile profile = accessProfileRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent access profile not found for ID: " + agentId));

        if (request.getEndpointUrl() != null && !request.getEndpointUrl().isEmpty()) {
            profile.setEndpointUrl(request.getEndpointUrl());
        }
        if (request.getApiKey() != null && !request.getApiKey().isEmpty()) {
            profile.setApiKey(request.getApiKey());
        }
        profile.setUpdatedAt(LocalDateTime.now());
        accessProfileRepository.save(profile);

        AgentMetadata metadata = metadataRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent metadata not found for ID: " + agentId));

        if (request.getName() != null && !request.getName().isEmpty()) {
            metadata.setName(request.getName());
        }

        String modelName = request.getModel();
        if (modelName != null && !modelName.isEmpty()) {
            metadata.setModelName(modelName);

            // 同步模型名称到健康状态
            try {
                AgentHealthStatus health = healthStatusRepository.findById(agentId).orElse(null);
                if (health != null) {
                    health.setModelName(modelName);
                    healthStatusRepository.save(health);
                }
            } catch (Exception e) {
                log.warn("Failed to update health status model name: {}", e.getMessage());
            }
        }

        if (request.getTemperature() != null) {
            metadata.setTemperature(request.getTemperature());
        }
        if (request.getTopP() != null) {
            metadata.setTopP(request.getTopP());
        }
        if (request.getMaxTokens() != null) {
            metadata.setMaxTokens(request.getMaxTokens());
        }
        if (request.getSystemPrompt() != null) {
            metadata.setSystemPrompt(request.getSystemPrompt());
        }

        metadata.setSyncTime(LocalDateTime.now());
        metadataRepository.save(metadata);
    }

    @Override
    public Optional<Object> chat(String agentId, String message, String fileId) {
        AgentAccessProfile profile = accessProfileRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent access profile not found"));
        AgentMetadata metadata = metadataRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent metadata not found"));

        double temperature = metadata.getTemperature() != null ? metadata.getTemperature() : 1.0;
        double topP = metadata.getTopP() != null ? metadata.getTopP() : 0.7;
        int maxTokens = metadata.getMaxTokens() != null ? metadata.getMaxTokens() : 2000;

        java.util.List<java.util.Map<String, Object>> messages = new java.util.ArrayList<>();
        if (metadata.getSystemPrompt() != null && !metadata.getSystemPrompt().isEmpty()) {
            messages.add(java.util.Map.of("role", "system", "content", metadata.getSystemPrompt()));
        }
        messages.add(java.util.Map.of("role", "user", "content", message));

        return zhipuIntegrationService.sendMessageWithFullParams(
                profile.getEndpointUrl() + "/chat/completions",
                profile.getApiKey(),
                metadata.getModelName(),
                messages,
                temperature,
                topP,
                maxTokens);
    }

    @Override
    public Optional<Object> chat(String agentId, String message, MultipartFile file) {
        AgentAccessProfile profile = accessProfileRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent access profile not found for ID: " + agentId));

        AgentMetadata metadata = metadataRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent metadata not found for ID: " + agentId));

        // 智谱AI暂不支持文件上传，仅处理文本
        return zhipuIntegrationService.sendMessage(
                profile.getEndpointUrl(),
                profile.getApiKey(),
                metadata.getModelName(),
                message);
    }

    @Override
    public AgentAccessProfile getAgentAccessProfile(String agentId) {
        return accessProfileRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent access profile not found for ID: " + agentId));
    }

    @Override
    public AgentMetadata getAgentMetadata(String agentId) {
        return metadataRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent metadata not found for ID: " + agentId));
    }

    @Override
    public void deleteAgent(String agentId) {
        log.info("Deleting Zhipu AI agent: {}", agentId);
        metadataRepository.deleteById(agentId);
        accessProfileRepository.deleteById(agentId);
        healthStatusRepository.deleteById(agentId);
        log.info("Zhipu AI agent deleted successfully: {}", agentId);
    }
}
