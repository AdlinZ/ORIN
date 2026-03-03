package com.adlin.orin.modules.agent.service;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.model.service.SiliconFlowIntegrationService;
import com.adlin.orin.modules.audit.service.AuditLogService;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.entity.AgentStatus;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SiliconFlowAgentManageService implements AgentManageService {

    private static final Logger log = LoggerFactory.getLogger(SiliconFlowAgentManageService.class);

    private final SiliconFlowIntegrationService siliconFlowIntegrationService;
    private final AgentAccessProfileRepository accessProfileRepository;
    private final AgentMetadataRepository metadataRepository;
    private final AgentHealthStatusRepository healthStatusRepository;
    private final com.adlin.orin.modules.model.repository.ModelMetadataRepository modelMetadataRepository;
    private final AuditLogService auditLogService;

    @Autowired
    public SiliconFlowAgentManageService(SiliconFlowIntegrationService siliconFlowIntegrationService,
            AgentAccessProfileRepository accessProfileRepository,
            AgentMetadataRepository metadataRepository,
            AgentHealthStatusRepository healthStatusRepository,
            com.adlin.orin.modules.model.repository.ModelMetadataRepository modelMetadataRepository,
            AuditLogService auditLogService) {
        this.siliconFlowIntegrationService = siliconFlowIntegrationService;
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
        String modelName = model != null && !model.isEmpty() ? model : "Qwen/Qwen2-7B-Instruct";
        siliconFlowIntegrationService.testConnection(endpointUrl, apiKey);

        String generatedId = UUID.randomUUID().toString().substring(0, 8);

        // Ensure unique suffix if name is auto-generated
        String finalName;
        if (agentName != null && !agentName.trim().isEmpty()) {
            finalName = agentName;
        } else {
            finalName = "硅基流动-" + modelName + "-" + generatedId.substring(0, 4);
        }

        AgentAccessProfile profile = AgentAccessProfile.builder()
                .agentId(generatedId)
                .endpointUrl(endpointUrl)
                .apiKey(apiKey)
                .datasetApiKey(null)
                .createdAt(LocalDateTime.now())
                .connectionStatus("VALID")
                .build();
        accessProfileRepository.save(profile);

        String viewType = determineViewType(endpointUrl, apiKey, modelName);
        AgentMetadata metadata = AgentMetadata.builder()
                .agentId(generatedId)
                .name(finalName)
                .description("通过硅基流动API接入的智能体: " + modelName)
                .mode("chat")
                .icon("🤖")
                .modelName(modelName)
                .providerType("SiliconFlow")
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
                .providerType("SiliconFlow")
                .mode("chat")
                .modelName(modelName)
                .viewType(viewType)
                .build();
        healthStatusRepository.save(health);

        return metadata;
    }

    /**
     * Determine viewType using metadata, API discovery, or heuristics
     */
    private String determineViewType(String endpointUrl, String apiKey, String modelName) {
        if (modelName == null)
            return "CHAT";

        // 1. Try Database Lookup (ModelMetadata)
        try {
            java.util.Optional<com.adlin.orin.modules.model.entity.ModelMetadata> modelOpt = modelMetadataRepository
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

        // 2. Try API discovery (Ground Truth from Provider)
        try {
            String apiViewType = siliconFlowIntegrationService.resolveSiliconFlowViewType(endpointUrl, apiKey,
                    modelName);
            if (apiViewType != null && !"CHAT".equals(apiViewType)) {
                return apiViewType;
            }
        } catch (Exception e) {
            log.warn("API discovery failed for model {}: {}", modelName, e.getMessage());
        }

        // 3. Heuristic Fallback (Last resort)
        String modelLower = modelName.toLowerCase();
        if (modelLower.contains("wan") || modelLower.contains("t2v") || modelLower.contains("i2v"))
            return "TTV";
        if (modelLower.contains("tts") || modelLower.contains("speech") || modelLower.contains("cosyvoice"))
            return "TTS";
        if (modelLower.contains("image") || modelLower.contains("flux") || modelLower.contains("stable-diffusion")
                || modelLower.contains("kolors"))
            return "TTI";

        return "CHAT";
    }

    /**
     * 向硅基流动API发送消息
     */
    public Optional<Object> sendMessage(String endpointUrl, String apiKey, String model, String message) {
        return siliconFlowIntegrationService.sendMessage(endpointUrl, apiKey, model, message);
    }

    @Override
    public List<AgentMetadata> getAllAgents() {
        return metadataRepository.findAll();
    }

    /**
     * 使用完整参数向硅基流动API发送消息
     */
    public Optional<Object> sendMessageWithParams(String endpointUrl, String apiKey, String model, String message,
            double temperature, double topP, int maxTokens) {
        try {
            String url = endpointUrl + "/chat/completions";

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            java.util.Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("model", model);
            java.util.List<java.util.Map<String, Object>> messages = java.util.Arrays.asList(
                    java.util.Map.of("role", "user", "content", message));

            // 通过硅基流动集成服务来执行请求
            return siliconFlowIntegrationService.sendMessageWithFullParams(url, apiKey, model, messages, temperature,
                    topP, maxTokens);
        } catch (Exception e) {
            log.warn("Failed to send message to SiliconFlow with params: {}", e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void updateAgent(String agentId, com.adlin.orin.modules.agent.dto.AgentOnboardRequest request) {
        log.info("Updating SiliconFlow agent: {}", agentId);

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

            // Re-determine viewType if model changes
            String newViewType = determineViewType(profile.getEndpointUrl(), profile.getApiKey(), modelName);
            metadata.setViewType(newViewType);

            // Sync modelName & viewType to HealthStatus for list view consistency
            try {
                com.adlin.orin.modules.monitor.entity.AgentHealthStatus health = healthStatusRepository
                        .findById(agentId).orElse(null);
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
        AgentAccessProfile profile = accessProfileRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent access profile not found"));
        AgentMetadata metadata = metadataRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent metadata not found"));

        String externalEndpoint = profile.getEndpointUrl() + "/chat/completions";
        String requestParamsJson = message;

        if (fileId != null) {
            java.util.List<java.util.Map<String, Object>> contentList = new java.util.ArrayList<>();
            contentList.add(java.util.Map.of("type", "text", "text", message));
            contentList.add(java.util.Map.of("type", "image_url", "image_url", java.util.Map.of("url", fileId)));
            requestParamsJson = "multimodal with image";
            long startTime1 = System.currentTimeMillis();
            Optional<Object> result = siliconFlowIntegrationService.sendMultimodalMessage(profile.getEndpointUrl(), profile.getApiKey(),
                    metadata.getModelName(), contentList);
            long duration1 = System.currentTimeMillis() - startTime1;
            // Record audit log after API call
            try {
                String responseText = result.isPresent() ? result.get().toString() : null;
                int statusCode = result.isPresent() ? 200 : 500;
                Map<String, Integer> usage = extractUsageFromResponse(result.orElse(null));
                auditLogService.logApiCall(
                        "SYSTEM", null, agentId, "SiliconFlow", externalEndpoint, "POST",
                        metadata.getModelName(), null, "ORIN", requestParamsJson,
                        responseText, Integer.valueOf(statusCode), duration1,
                        usage.get("prompt"), usage.get("completion"), Double.valueOf(0.0), result.isPresent(), null);
            } catch (Exception e) {
                log.warn("Failed to log audit for SiliconFlow: {}", e.getMessage());
            }
            return result;
        }

        double temperature = metadata.getTemperature() != null ? metadata.getTemperature() : 0.7;
        double topP = metadata.getTopP() != null ? metadata.getTopP() : 0.7;
        int maxTokens = metadata.getMaxTokens() != null ? metadata.getMaxTokens() : 500;

        java.util.List<java.util.Map<String, Object>> messages = new java.util.ArrayList<>();
        if (metadata.getSystemPrompt() != null && !metadata.getSystemPrompt().isEmpty()) {
            messages.add(java.util.Map.of("role", "system", "content", metadata.getSystemPrompt()));
        }
        messages.add(java.util.Map.of("role", "user", "content", message));

        try {
            requestParamsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(messages);
        } catch (Exception e) {
            requestParamsJson = message;
        }

        long startTime = System.currentTimeMillis();
        Optional<Object> result = siliconFlowIntegrationService.sendMessageWithFullParams(
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
            int statusCode = result.isPresent() ? 200 : 500;
            Map<String, Integer> usage = extractUsageFromResponse(result.orElse(null));
            auditLogService.logApiCall(
                    "SYSTEM", null, agentId, "SiliconFlow", externalEndpoint, "POST",
                    metadata.getModelName(), null, "ORIN", requestParamsJson,
                    responseText, Integer.valueOf(statusCode), duration,
                    usage.get("prompt"), usage.get("completion"), Double.valueOf(0.0), result.isPresent(), null);
        } catch (Exception e) {
            log.warn("Failed to log audit for SiliconFlow: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public Optional<Object> chat(String agentId, String message, org.springframework.web.multipart.MultipartFile file) {
        AgentAccessProfile profile = accessProfileRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent access profile not found for ID: " + agentId));

        // 1. Upload file to SiliconFlow
        Optional<Object> uploadResult = siliconFlowIntegrationService.uploadFile(
                profile.getEndpointUrl(),
                profile.getApiKey(),
                file);

        String fileId = null;
        if (uploadResult.isPresent()) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = (java.util.Map<String, Object>) uploadResult.get();
            if (response.containsKey("id")) {
                fileId = response.get("id").toString();
            }
        }

        // 2. If upload success, use multimodal chat, else fallback to text
        if (fileId != null) {
            return chat(agentId, message, fileId);
        } else {
            log.warn("File upload to SiliconFlow failed or returned no ID. Falling back to text-only chat.");
            return chat(agentId, message, (String) null);
        }
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
                        // SiliconFlow/OpenAI format: prompt_tokens, completion_tokens, total_tokens
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
        log.info("Deleting SiliconFlow agent: {}", agentId);
        metadataRepository.deleteById(agentId);
        accessProfileRepository.deleteById(agentId);
        healthStatusRepository.deleteById(agentId);
        log.info("SiliconFlow agent deleted successfully: {}", agentId);
    }
}