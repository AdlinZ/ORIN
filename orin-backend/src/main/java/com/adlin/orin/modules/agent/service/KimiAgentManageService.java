package com.adlin.orin.modules.agent.service;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.audit.service.AuditLogService;
import com.adlin.orin.modules.model.service.KimiIntegrationService;
import com.adlin.orin.modules.model.repository.ModelMetadataRepository;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.entity.AgentStatus;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class KimiAgentManageService implements AgentManageService {

    private static final Logger log = LoggerFactory.getLogger(KimiAgentManageService.class);

    private final KimiIntegrationService kimiIntegrationService;
    private final AgentAccessProfileRepository accessProfileRepository;
    private final AgentMetadataRepository metadataRepository;
    private final AgentHealthStatusRepository healthStatusRepository;
    private final ModelMetadataRepository modelMetadataRepository;
    private final AuditLogService auditLogService;

    @Autowired
    public KimiAgentManageService(KimiIntegrationService kimiIntegrationService,
            AgentAccessProfileRepository accessProfileRepository,
            AgentMetadataRepository metadataRepository,
            AgentHealthStatusRepository healthStatusRepository,
            ModelMetadataRepository modelMetadataRepository,
            AuditLogService auditLogService) {
        this.kimiIntegrationService = kimiIntegrationService;
        this.accessProfileRepository = accessProfileRepository;
        this.metadataRepository = metadataRepository;
        this.healthStatusRepository = healthStatusRepository;
        this.modelMetadataRepository = modelMetadataRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    public AgentMetadata onboardAgent(String endpointUrl, String apiKey, String model) {
        return onboardAgent(endpointUrl, apiKey, model, null);
    }

    public AgentMetadata onboardAgent(String endpointUrl, String apiKey, String model, String agentName) {
        String modelName = model != null && !model.isEmpty() ? model : "moonshot-v1-8k-chat";
        boolean connectionValid = kimiIntegrationService.testConnection(endpointUrl, apiKey);
        log.info("Kimi connection test result for onboarding: {}", connectionValid);

        String generatedId = UUID.randomUUID().toString().substring(0, 8);

        // Ensure unique suffix if name is auto-generated
        String finalName;
        if (agentName != null && !agentName.trim().isEmpty()) {
            finalName = agentName;
        } else {
            finalName = "Kimi-" + modelName + "-" + generatedId.substring(0, 4);
        }

        String connectionStatus = connectionValid ? "VALID" : "INVALID";
        AgentAccessProfile profile = AgentAccessProfile.builder()
                .agentId(generatedId)
                .endpointUrl(endpointUrl)
                .apiKey(apiKey)
                .datasetApiKey(null)
                .createdAt(LocalDateTime.now())
                .connectionStatus(connectionStatus)
                .build();
        accessProfileRepository.save(profile);

        String viewType = determineViewType(endpointUrl, apiKey, modelName);
        AgentMetadata metadata = AgentMetadata.builder()
                .agentId(generatedId)
                .name(finalName)
                .description("通过 Kimi (Moonshot) API 接入的智能体: " + modelName)
                .mode("chat")
                .icon("🤖")
                .modelName(modelName)
                .providerType("Kimi")
                .viewType(viewType)
                .syncTime(LocalDateTime.now())
                .build();
        metadataRepository.save(metadata);

        AgentHealthStatus health = AgentHealthStatus.builder()
                .agentId(generatedId)
                .agentName(finalName)
                .status(AgentStatus.RUNNING)
                .healthScore(100)
                .lastHeartbeat(System.currentTimeMillis())
                .providerType("Kimi")
                .mode("chat")
                .modelName(modelName)
                .viewType(viewType)
                .build();
        healthStatusRepository.save(health);

        return metadata;
    }

    /**
     * Determine viewType using metadata or heuristics
     */
    private String determineViewType(String endpointUrl, String apiKey, String modelName) {
        if (modelName == null)
            return "CHAT";

        // 1. Try Database Lookup (ModelMetadata)
        try {
            Optional<com.adlin.orin.modules.model.entity.ModelMetadata> modelOpt = modelMetadataRepository
                    .findByModelId(modelName);

            if (modelOpt.isPresent()) {
                String modelType = modelOpt.get().getType();
                if (modelType != null && !modelType.isEmpty()) {
                    return modelType;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get viewType from metadata for model {}: {}", modelName, e.getMessage());
        }

        // 2. Heuristic Fallback
        String modelLower = modelName.toLowerCase();
        if (modelLower.contains("vision") || modelLower.contains("vl"))
            return "VISION";
        if (modelLower.contains("tts") || modelLower.contains("speech"))
            return "TTS";

        return "CHAT";
    }

    /**
     * 向 Kimi API 发送消息
     */
    public Optional<Object> sendMessage(String endpointUrl, String apiKey, String model, String message) {
        return kimiIntegrationService.sendMessage(endpointUrl, apiKey, model, message);
    }

    @Override
    public List<AgentMetadata> getAllAgents() {
        return metadataRepository.findAll();
    }

    /**
     * 使用完整参数向 Kimi API 发送消息
     */
    public Optional<Object> sendMessageWithParams(String endpointUrl, String apiKey, String model, String message,
            double temperature, double topP, int maxTokens) {
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> userMessage = new java.util.HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", message);
            messages.add(userMessage);

            return kimiIntegrationService.sendMessageWithFullParams(endpointUrl, apiKey, model, messages,
                    temperature, topP, maxTokens);
        } catch (Exception e) {
            log.warn("Failed to send message to Kimi with params: {}", e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void updateAgent(String agentId, com.adlin.orin.modules.agent.dto.AgentOnboardRequest request) {
        log.info("Updating Kimi agent: {}", agentId);

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

            // Re-determine viewType if model changes
            String newViewType = determineViewType(profile.getEndpointUrl(), profile.getApiKey(), modelName);
            metadata.setViewType(newViewType);

            // Sync modelName & viewType to HealthStatus for list view consistency
            try {
                AgentHealthStatus health = healthStatusRepository.findById(agentId).orElse(null);
                if (health != null) {
                    health.setModelName(modelName);
                    health.setViewType(newViewType);
                    healthStatusRepository.save(health);
                }
            } catch (Exception e) {
                // Silent fail
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

        double temperature = metadata.getTemperature() != null ? metadata.getTemperature() : 0.7;
        double topP = metadata.getTopP() != null ? metadata.getTopP() : 0.7;
        int maxTokens = maxTokensOverride != null && maxTokensOverride > 0
                ? maxTokensOverride
                : (metadata.getMaxTokens() != null ? metadata.getMaxTokens() : 2000);

        // Kimi K2.5 and some other models only support temperature=1 and top_p=0.95
        String modelName = metadata.getModelName();
        if (modelName != null && (modelName.toLowerCase().contains("k2.5") || modelName.toLowerCase().contains("kimi-k2"))) {
            temperature = 1.0;
            topP = 0.95;
            log.info("Kimi model {} requires temperature=1, top_p=0.95, overriding", modelName);
        }

        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        String systemPrompt = (overrideSystemPrompt != null && !overrideSystemPrompt.isBlank())
                ? overrideSystemPrompt
                : metadata.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            Map<String, Object> systemMsg = new java.util.HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.add(systemMsg);
        }
        Map<String, Object> userMsg = new java.util.HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", message);
        messages.add(userMsg);

        String externalEndpoint = profile.getEndpointUrl() + "/chat/completions";
        String requestParamsJson;
        try {
            requestParamsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(messages);
        } catch (Exception e) {
            requestParamsJson = message;
        }

        // Call API first
        long startTime = System.currentTimeMillis();
        Optional<Object> result = kimiIntegrationService.sendMessageWithFullParams(
                profile.getEndpointUrl(),
                profile.getApiKey(),
                metadata.getModelName(),
                messages,
                temperature,
                topP,
                maxTokens);
        long duration = System.currentTimeMillis() - startTime;

        // Record audit log after API call, with actual result status
        try {
            String responseText = result.isPresent() ? result.get().toString() : null;
            int statusCode = result.isPresent() ? 200 : 500;
            Map<String, Integer> usage = extractUsageFromResponse(result.orElse(null));
            auditLogService.logApiCall(
                    "SYSTEM", null, agentId, "Kimi", externalEndpoint, "POST",
                    metadata.getModelName(), null, "ORIN", requestParamsJson,
                    responseText, Integer.valueOf(statusCode), duration,
                    usage.get("prompt"), usage.get("completion"), Double.valueOf(0.0), result.isPresent(), null);
        } catch (Exception e) {
            log.warn("Failed to log audit for Kimi: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public Optional<Object> chat(String agentId, String message, org.springframework.web.multipart.MultipartFile file) {
        // Kimi 当前不支持文件上传，返回错误
        log.warn("Kimi does not support file upload");
        return Optional.empty();
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

    @Override
    public void deleteAgent(String agentId) {
        log.info("Deleting Kimi agent: {}", agentId);
        metadataRepository.deleteById(agentId);
        accessProfileRepository.deleteById(agentId);
        healthStatusRepository.deleteById(agentId);
        log.info("Kimi agent deleted successfully: {}", agentId);
    }
}
