package com.adlin.orin.modules.agent.service;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.audit.service.AuditLogService;
import com.adlin.orin.modules.model.service.DeepSeekIntegrationService;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.entity.AgentStatus;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * DeepSeek Agent管理服务
 */
@Service
public class DeepSeekAgentManageService implements AgentManageService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekAgentManageService.class);

    private final DeepSeekIntegrationService deepSeekIntegrationService;
    private final AgentAccessProfileRepository accessProfileRepository;
    private final AgentMetadataRepository metadataRepository;
    private final AgentHealthStatusRepository healthStatusRepository;
    private final com.adlin.orin.modules.model.repository.ModelMetadataRepository modelMetadataRepository;
    private final AuditLogService auditLogService;

    @Autowired
    public DeepSeekAgentManageService(
            DeepSeekIntegrationService deepSeekIntegrationService,
            AgentAccessProfileRepository accessProfileRepository,
            AgentMetadataRepository metadataRepository,
            AgentHealthStatusRepository healthStatusRepository,
            com.adlin.orin.modules.model.repository.ModelMetadataRepository modelMetadataRepository,
            AuditLogService auditLogService) {
        this.deepSeekIntegrationService = deepSeekIntegrationService;
        this.accessProfileRepository = accessProfileRepository;
        this.metadataRepository = metadataRepository;
        this.healthStatusRepository = healthStatusRepository;
        this.modelMetadataRepository = modelMetadataRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    public AgentMetadata onboardAgent(String endpointUrl, String apiKey, String model) {
        return onboardAgent(endpointUrl, apiKey, model, null, 1.0);
    }

    /**
     * 接入DeepSeek Agent
     */
    public AgentMetadata onboardAgent(String endpointUrl, String apiKey, String model, String agentName,
            Double temperature) {
        String modelName = model != null && !model.isEmpty() ? model : "deepseek-chat";
        double temp = temperature != null ? temperature : 1.0;

        // 测试连接
        deepSeekIntegrationService.testConnection(endpointUrl, apiKey, modelName);

        String generatedId = UUID.randomUUID().toString().substring(0, 8);

        // 生成Agent名称
        String finalName;
        if (agentName != null && !agentName.trim().isEmpty()) {
            finalName = agentName;
        } else {
            finalName = "DeepSeek-" + modelName + "-" + generatedId.substring(0, 4);
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
                .description("通过DeepSeek接入的智能体: " + modelName)
                .mode("chat")
                .icon("🤖")
                .modelName(modelName)
                .providerType("DeepSeek")
                .syncTime(LocalDateTime.now())
                .build();

        // 设置temperature参数
        metadata.setTemperature(temp);
        String viewType = determineViewType(modelName);
        metadata.setViewType(viewType);
        metadataRepository.save(metadata);

        // 保存健康状态
        AgentHealthStatus health = AgentHealthStatus.builder()
                .agentId(generatedId)
                .agentName(finalName)
                .status(AgentStatus.RUNNING)
                .healthScore(100)
                .lastHeartbeat(System.currentTimeMillis())
                .providerType("DeepSeek")
                .mode("chat")
                .modelName(modelName)
                .viewType(viewType)
                .build();
        healthStatusRepository.save(health);

        log.info("DeepSeek agent onboarded successfully: {}", finalName);
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
        log.info("Updating DeepSeek agent: {}", agentId);

        AgentAccessProfile profile = accessProfileRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent access profile not found for ID: " + agentId));

        if (request.getEndpointUrl() != null && !request.getEndpointUrl().isEmpty()) {
            profile.setEndpointUrl(request.getEndpointUrl());
        }
        if (request.getApiKey() != null && !request.getApiKey().isEmpty()
                && !request.getApiKey().contains("****")) {
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
        return chat(agentId, message, fileId, null);
    }

    public Optional<Object> chat(String agentId, String message, String fileId, String overrideSystemPrompt) {
        return chat(agentId, message, fileId, overrideSystemPrompt, (Integer) null);
    }

    public Optional<Object> chat(String agentId, String message, String fileId, String overrideSystemPrompt, Integer maxTokensOverride) {
        AgentAccessProfile profile = accessProfileRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent access profile not found"));
        AgentMetadata metadata = metadataRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent metadata not found"));

        double temperature = metadata.getTemperature() != null ? metadata.getTemperature() : 1.0;
        double topP = metadata.getTopP() != null ? metadata.getTopP() : 0.7;
        int maxTokens = maxTokensOverride != null && maxTokensOverride > 0
                ? maxTokensOverride
                : (metadata.getMaxTokens() != null ? metadata.getMaxTokens() : 2000);

        java.util.List<java.util.Map<String, Object>> messages = new java.util.ArrayList<>();
        String systemPrompt = (overrideSystemPrompt != null && !overrideSystemPrompt.isBlank())
                ? overrideSystemPrompt
                : metadata.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(java.util.Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(java.util.Map.of("role", "user", "content", message));

        String externalEndpoint = profile.getEndpointUrl() + "/chat/completions";
        String requestParamsJson;
        try {
            Map<String, Object> requestPayloadForAudit = new HashMap<>();
            requestPayloadForAudit.put("model", metadata.getModelName());
            requestPayloadForAudit.put("messages", messages);
            requestPayloadForAudit.put("temperature", temperature);
            requestPayloadForAudit.put("top_p", topP);
            requestPayloadForAudit.put("max_tokens", maxTokens);
            requestPayloadForAudit.put("stream", false);
            requestParamsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(requestPayloadForAudit);
        } catch (Exception e) {
            requestParamsJson = message;
        }

        // Call API first
        long startTime = System.currentTimeMillis();
        Optional<Object> result = deepSeekIntegrationService.sendMessageWithFullParams(
                profile.getEndpointUrl() + "/chat/completions",
                profile.getApiKey(),
                metadata.getModelName(),
                messages,
                temperature,
                topP,
                maxTokens);
        long duration = System.currentTimeMillis() - startTime;

        // Record audit log after API call
        try {
            String responseText = result.isPresent() ? result.get().toString() : null;
            Object responseObj = result.orElse(null);
            int statusCode = extractStatusCode(responseObj, result.isPresent());
            boolean success = statusCode >= 200 && statusCode < 300;
            String errorMessage = success ? null : extractErrorMessage(responseObj);
            Map<String, Integer> usage = extractUsageFromResponse(responseObj);
            auditLogService.logApiCall(
                    "SYSTEM", null, agentId, "DeepSeek", externalEndpoint, "POST",
                    metadata.getModelName(), null, "ORIN", requestParamsJson,
                    responseText, Integer.valueOf(statusCode), duration,
                    usage.get("prompt"), usage.get("completion"), Double.valueOf(0.0), success, errorMessage);
        } catch (Exception e) {
            log.warn("Failed to log audit for DeepSeek: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public Optional<Object> chat(String agentId, String message, MultipartFile file) {
        AgentAccessProfile profile = accessProfileRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent access profile not found for ID: " + agentId));

        AgentMetadata metadata = metadataRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent metadata not found for ID: " + agentId));

        // DeepSeek暂不支持文件上传，仅处理文本
        return deepSeekIntegrationService.sendMessage(
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

    /**
     * 从响应对象中提取 token 使用量
     */
    private Map<String, Integer> extractUsageFromResponse(Object response) {
        Map<String, Integer> usage = new HashMap<>();
        usage.put("prompt", 0);
        usage.put("completion", 0);
        usage.put("total", 0);

        if (response == null) {
            return usage;
        }

        try {
            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> respMap = (Map<String, Object>) response;

                if (respMap.containsKey("usage")) {
                    Object usageObj = respMap.get("usage");
                    if (usageObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> usageMap = (Map<String, Object>) usageObj;
                        if (usageMap.containsKey("prompt_tokens")) {
                            usage.put("prompt", toInt(usageMap.get("prompt_tokens")));
                        }
                        if (usageMap.containsKey("completion_tokens")) {
                            usage.put("completion", toInt(usageMap.get("completion_tokens")));
                        }
                        if (usageMap.containsKey("total_tokens")) {
                            usage.put("total", toInt(usageMap.get("total_tokens")));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract usage from response: {}", e.getMessage());
        }

        return usage;
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int extractStatusCode(Object response, boolean hasResult) {
        if (response instanceof Map<?, ?> respMap) {
            Object explicitStatusCode = respMap.get("statusCode");
            if (explicitStatusCode != null) {
                int parsed = toInt(explicitStatusCode);
                if (parsed > 0) {
                    return parsed;
                }
            }
            Object status = respMap.get("status");
            if (status != null && "ERROR".equalsIgnoreCase(String.valueOf(status))) {
                return 500;
            }
        }
        return hasResult ? 200 : 500;
    }

    private String extractErrorMessage(Object response) {
        if (response instanceof Map<?, ?> respMap) {
            Object errorMessage = respMap.get("errorMessage");
            if (errorMessage != null && !String.valueOf(errorMessage).isBlank()) {
                return String.valueOf(errorMessage);
            }
            Object error = respMap.get("error");
            if (error != null && !String.valueOf(error).isBlank()) {
                return String.valueOf(error);
            }
        }
        return "DeepSeek request failed";
    }

    @Override
    public void deleteAgent(String agentId) {
        log.info("Deleting DeepSeek agent: {}", agentId);
        metadataRepository.deleteById(agentId);
        accessProfileRepository.deleteById(agentId);
        healthStatusRepository.deleteById(agentId);
        log.info("DeepSeek agent deleted successfully: {}", agentId);
    }
}
