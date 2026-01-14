package com.adlin.orin.modules.agent.service.impl;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.service.AgentManageService;
import com.adlin.orin.modules.agent.service.DifyIntegrationService;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import com.adlin.orin.modules.model.service.SiliconFlowIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Primary
public class AgentManageServiceImpl implements AgentManageService {

    private static final Logger log = LoggerFactory.getLogger(AgentManageServiceImpl.class);

    private final DifyIntegrationService difyIntegrationService;
    private final SiliconFlowIntegrationService siliconFlowIntegrationService;
    private final AgentAccessProfileRepository accessProfileRepository;
    private final AgentMetadataRepository metadataRepository;
    private final AgentHealthStatusRepository healthStatusRepository;
    private final com.adlin.orin.modules.audit.service.AuditLogService auditLogService;

    private final com.adlin.orin.modules.multimodal.service.MultimodalFileService multimodalFileService;

    public AgentManageServiceImpl(DifyIntegrationService difyIntegrationService,
            SiliconFlowIntegrationService siliconFlowIntegrationService,
            AgentAccessProfileRepository accessProfileRepository,
            AgentMetadataRepository metadataRepository,
            AgentHealthStatusRepository healthStatusRepository,
            com.adlin.orin.modules.audit.service.AuditLogService auditLogService,
            com.adlin.orin.modules.multimodal.service.MultimodalFileService multimodalFileService) {
        this.difyIntegrationService = difyIntegrationService;
        this.siliconFlowIntegrationService = siliconFlowIntegrationService;
        this.accessProfileRepository = accessProfileRepository;
        this.metadataRepository = metadataRepository;
        this.healthStatusRepository = healthStatusRepository;
        this.auditLogService = auditLogService;
        this.multimodalFileService = multimodalFileService;
    }

    @Override
    public AgentMetadata onboardAgent(String endpointUrl, String apiKey, String datasetApiKey) {
        log.info("Attempting to onboard agent from: {}", endpointUrl);

        // Test Dify connection first
        boolean isConnected = difyIntegrationService.testConnection(endpointUrl, apiKey);
        if (!isConnected) {
            log.error("Failed to connect to Dify at: {}", endpointUrl);
            throw new RuntimeException("无法连接到Dify服务，请检查API端点和密钥是否正确");
        }

        String generatedId = UUID.randomUUID().toString().substring(0, 8);
        String name = "Dify Agent " + generatedId;
        String mode = "chat";

        // 获取Dify应用信息
        var appsResult = difyIntegrationService.getApplications(endpointUrl, apiKey);
        if (appsResult.isPresent()) {
            @SuppressWarnings("unchecked")
            var appsData = (java.util.Map<String, Object>) appsResult.get();
            if (appsData.containsKey("data")) {
                @SuppressWarnings("unchecked")
                var appsList = (java.util.List<java.util.Map<String, Object>>) appsData.get("data");
                if (!appsList.isEmpty()) {
                    var firstApp = appsList.get(0);
                    if (firstApp.containsKey("name")) {
                        name = (String) firstApp.get("name");
                    }
                    if (firstApp.containsKey("mode")) {
                        String difyMode = (String) firstApp.get("mode");
                        // 映射 Dify 的模式到我们的标准模式
                        if ("advanced-chat".equals(difyMode) || "chat".equals(difyMode)) {
                            mode = "chat";
                        } else {
                            mode = difyMode;
                        }
                    }
                }
            }
        }

        // 保存接入凭证
        AgentAccessProfile profile = AgentAccessProfile.builder()
                .agentId(generatedId)
                .endpointUrl(endpointUrl)
                .apiKey(apiKey)
                .datasetApiKey(datasetApiKey)
                .createdAt(LocalDateTime.now())
                .connectionStatus("VALID")
                .build();
        accessProfileRepository.save(profile);

        // 保存元数据
        AgentMetadata metadata = AgentMetadata.builder()
                .agentId(generatedId)
                .name(name)
                .description("Imported from Dify at " + LocalDateTime.now())
                .mode(mode)
                .icon("🤖")
                .providerType("Dify")
                .syncTime(LocalDateTime.now())
                .build();
        metadataRepository.save(metadata);

        // 创建健康状态
        AgentHealthStatus health = AgentHealthStatus.builder()
                .agentId(generatedId)
                .agentName(name)
                .status(AgentHealthStatus.Status.RUNNING)
                .healthScore(100)
                .lastHeartbeat(System.currentTimeMillis())
                .providerType("Dify")
                .mode(mode)
                .modelName(null)
                .build();
        healthStatusRepository.save(health);

        log.info("Agent onboarded successfully: {}", name);
        return metadata;
    }

    @Override
    public void updateAgent(String agentId, com.adlin.orin.modules.agent.dto.AgentOnboardRequest request) {
        log.info("Updating agent: {}", agentId);

        // Update Access Profile
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

        // Update Metadata
        AgentMetadata metadata = metadataRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent metadata not found for ID: " + agentId));

        if (request.getName() != null && !request.getName().isEmpty()) {
            metadata.setName(request.getName());

            // Allow syncing name to HealthStatus for list view consistency
            try {
                com.adlin.orin.modules.monitor.entity.AgentHealthStatus health = healthStatusRepository
                        .findById(agentId).orElse(null);
                if (health != null) {
                    health.setAgentName(request.getName());
                    healthStatusRepository.save(health);
                }
            } catch (Exception e) {
                log.warn("Failed to sync name to health status: {}", e.getMessage());
            }
        }

        String modelName = request.getModel();
        if (modelName != null && !modelName.isEmpty()) {
            metadata.setModelName(modelName);

            // Sync modelName to HealthStatus for list view consistency
            try {
                com.adlin.orin.modules.monitor.entity.AgentHealthStatus health = healthStatusRepository
                        .findById(agentId).orElse(null);
                if (health != null) {
                    health.setModelName(modelName);
                    healthStatusRepository.save(health);
                }
            } catch (Exception e) {
                log.warn("Failed to sync modelName to health status: {}", e.getMessage());
            }
        }

        // Update new params
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

        log.info("Agent updated successfully: {}", agentId);
    }

    @Override
    public java.util.Optional<Object> chat(String agentId, String message, String fileId) {
        log.info("Chatting with agent (fileId): {}", agentId);

        AgentAccessProfile profile = accessProfileRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent access profile not found for ID: " + agentId));

        AgentMetadata metadata = metadataRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent metadata not found for ID: " + agentId));

        long startTime = System.currentTimeMillis();
        java.util.Optional<Object> response;

        String model = metadata.getModelName();
        // If provider is SiliconFlow (based on model name usually)
        if (model != null && !model.isEmpty()) {
            // SiliconFlow Logic
            response = chatWithSiliconFlow(profile, metadata, message, fileId);
        } else {
            // Dify Logic (Fallback: just append fileId to message for now as we don't have
            // Dify file upload proxy yet)
            response = difyIntegrationService.sendMessage(
                    profile.getEndpointUrl(),
                    profile.getApiKey(),
                    null,
                    message + (fileId != null ? "\n[File ID: " + fileId + "]" : ""));
        }

        long duration = System.currentTimeMillis() - startTime;

        // 异步记录审计日志
        if (response.isPresent()) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> respMap = (java.util.Map<String, Object>) response.get();
            log.debug("AI Response Map: {}", respMap);
            Integer promptTokens = 0;
            Integer completionTokens = 0;

            String answer = "No Response Content";
            try {
                // SiliconFlow specific parsing for token usage and answer
                if (model != null && !model.isEmpty()) {
                    if (respMap.containsKey("choices")) {
                        @SuppressWarnings("unchecked")
                        java.util.List<java.util.Map<String, Object>> choices = (java.util.List<java.util.Map<String, Object>>) respMap
                                .get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> messageObj = (java.util.Map<String, Object>) choices.get(0)
                                    .get("message");
                            if (messageObj != null && messageObj.containsKey("content")) {
                                answer = (String) messageObj.get("content");
                            }
                        }
                    }
                    if (respMap.containsKey("usage")) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> usage = (java.util.Map<String, Object>) respMap.get("usage");
                        if (usage != null) {
                            promptTokens = usage.get("prompt_tokens") != null ? (Integer) usage.get("prompt_tokens")
                                    : 0;
                            completionTokens = usage.get("completion_tokens") != null
                                    ? (Integer) usage.get("completion_tokens")
                                    : 0;
                        }
                    }
                } else {
                    // Dify Response logic (fallback)
                    Object ansObj = respMap.get("answer");
                    if (ansObj != null) {
                        answer = ansObj.toString();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to extract token usage or answer: {}", e.getMessage());
            }

            auditLogService.logApiCall(
                    "system-user",
                    null,
                    agentId,
                    model != null && !model.isEmpty() ? "SiliconFlow" : "Dify",
                    profile.getEndpointUrl(),
                    "POST",
                    model,
                    "localhost",
                    "ORIN-Server",
                    message,
                    answer,
                    200,
                    duration,
                    promptTokens,
                    completionTokens,
                    0.0,
                    true,
                    null);
        }

        return response;
    }

    private java.util.Optional<Object> chatWithSiliconFlow(AgentAccessProfile profile, AgentMetadata metadata,
            String message, String fileId) {

        java.util.List<java.util.Map<String, Object>> messages = new java.util.ArrayList<>();
        // 1. Add System Prompt
        if (metadata.getSystemPrompt() != null && !metadata.getSystemPrompt().isEmpty()) {
            messages.add(java.util.Map.of("role", "system", "content", metadata.getSystemPrompt()));
        }

        // 2. Add User Message (Text or Multimodal)
        if (fileId != null) {
            java.util.List<java.util.Map<String, Object>> contentList = new java.util.ArrayList<>();
            contentList.add(java.util.Map.of("type", "text", "text", message));
            contentList.add(java.util.Map.of("type", "image_url", "image_url", java.util.Map.of("url", fileId)));

            messages.add(java.util.Map.of("role", "user", "content", contentList));
        } else {
            messages.add(java.util.Map.of("role", "user", "content", message));
        }

        double temperature = metadata.getTemperature() != null ? metadata.getTemperature() : 0.7;
        double topP = metadata.getTopP() != null ? metadata.getTopP() : 0.7;
        int maxTokens = metadata.getMaxTokens() != null ? metadata.getMaxTokens() : 500;

        return siliconFlowIntegrationService.sendMessageWithFullParams(
                profile.getEndpointUrl() + "/chat/completions",
                profile.getApiKey(),
                metadata.getModelName(),
                messages,
                temperature,
                topP,
                maxTokens);
    }

    @Override
    public java.util.Optional<Object> chat(String agentId, String message,
            org.springframework.web.multipart.MultipartFile file) {
        log.info("Chatting with agent: {}", agentId);

        AgentAccessProfile profile = accessProfileRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent access profile not found for ID: " + agentId));

        AgentMetadata metadata = metadataRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent metadata not found for ID: " + agentId));

        // Handle file upload if present
        String fileNote = "";
        if (file != null && !file.isEmpty()) {
            try {
                var uploadedFile = multimodalFileService.uploadFile(file, "chat-user");
                fileNote = "\n[User uploaded file: " + uploadedFile.getFileName() + "]";
                // In a real multimodal scenario, we would pass the file content/URL to the LLM
                // depending on provider support.
                // For MVP, we just notify the agent about the file existence.
            } catch (Exception e) {
                log.error("Failed to upload file for chat", e);
                // Continue chat without file or throw error?
                // Let's verify:
                // throw new RuntimeException("File upload failed: " + e.getMessage());
                // Or just warning. For better UX, maybe just log warning and append error note
                fileNote = "\n[File upload failed]";
            }
        }

        long startTime = System.currentTimeMillis();
        java.util.Optional<Object> response;
        String model = metadata.getModelName();
        String providerType = (model != null && !model.isEmpty()) ? "SiliconFlow" : "Dify";

        // 执行对话
        if ("SiliconFlow".equals(providerType)) {
            String uploadedFileId = null;

            // Upload to SiliconFlow if file exists
            if (file != null && !file.isEmpty()) {
                try {
                    var sfUploadResult = siliconFlowIntegrationService.uploadFile(
                            profile.getEndpointUrl(),
                            profile.getApiKey(),
                            file);
                    if (sfUploadResult.isPresent()) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> sfMap = (java.util.Map<String, Object>) sfUploadResult.get();
                        if (sfMap.containsKey("id")) {
                            uploadedFileId = (String) sfMap.get("id");
                            fileNote += "\n[SiliconFlow File ID: " + uploadedFileId + "]";
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to upload file to SiliconFlow upstream", e);
                    fileNote += "\n[Upstream Upload Failed]";
                }
            }

            response = chatWithSiliconFlow(profile, metadata, message, uploadedFileId);
        } else {
            String fullMessage = message + fileNote;
            response = difyIntegrationService.sendMessage(
                    profile.getEndpointUrl(),
                    profile.getApiKey(),
                    null,
                    fullMessage);
        }

        long duration = System.currentTimeMillis() - startTime;

        // 异步记录审计日志
        if (response.isPresent()) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> respMap = (java.util.Map<String, Object>) response.get();
            log.debug("AI Response Map: {}", respMap);
            Integer promptTokens = 0;
            Integer completionTokens = 0;

            String answer = "No Response Content";
            try {
                if ("SiliconFlow".equals(providerType)) {
                    if (respMap.containsKey("choices")) {
                        @SuppressWarnings("unchecked")
                        java.util.List<java.util.Map<String, Object>> choices = (java.util.List<java.util.Map<String, Object>>) respMap
                                .get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> messageObj = (java.util.Map<String, Object>) choices.get(0)
                                    .get("message");
                            if (messageObj != null && messageObj.containsKey("content")) {
                                answer = (String) messageObj.get("content");
                            }
                        }
                    }
                    if (respMap.containsKey("usage")) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> usage = (java.util.Map<String, Object>) respMap.get("usage");
                        if (usage != null) {
                            promptTokens = usage.get("prompt_tokens") != null ? (Integer) usage.get("prompt_tokens")
                                    : 0;
                            completionTokens = usage.get("completion_tokens") != null
                                    ? (Integer) usage.get("completion_tokens")
                                    : 0;
                        }
                    }
                } else {
                    // Dify Response
                    Object ansObj = respMap.get("answer");
                    if (ansObj != null) {
                        answer = ansObj.toString();
                    }
                    if (respMap.containsKey("metadata")) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> meta = (java.util.Map<String, Object>) respMap.get("metadata");
                        if (meta != null && meta.containsKey("usage")) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> usage = (java.util.Map<String, Object>) meta.get("usage");
                            if (usage != null) {
                                promptTokens = usage.get("prompt_tokens") != null ? (Integer) usage.get("prompt_tokens")
                                        : 0;
                                completionTokens = usage.get("completion_tokens") != null
                                        ? (Integer) usage.get("completion_tokens")
                                        : 0;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to extract token usage or answer: {}", e.getMessage());
            }

            auditLogService.logApiCall(
                    "system-user",
                    null,
                    agentId,
                    providerType,
                    profile.getEndpointUrl(),
                    "POST",
                    model,
                    "localhost",
                    "ORIN-Server",
                    message,
                    answer,
                    200,
                    duration,
                    promptTokens,
                    completionTokens,
                    0.0,
                    true,
                    null);
        }

        return response;
    }

    @Override
    public List<AgentMetadata> getAllAgents() {
        return metadataRepository.findAll();
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
        log.info("Deleting agent: {}", agentId);
        metadataRepository.deleteById(agentId);
        accessProfileRepository.deleteById(agentId);
        healthStatusRepository.deleteById(agentId);
        log.info("Agent deleted successfully: {}", agentId);
    }
}
