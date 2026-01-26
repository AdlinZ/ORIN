package com.adlin.orin.modules.agent.service.impl;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.service.AgentManageService;
import com.adlin.orin.modules.agent.service.DifyIntegrationService;
import com.adlin.orin.modules.model.service.SiliconFlowIntegrationService;
import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.audit.service.AuditLogService;
import com.adlin.orin.modules.knowledge.service.meta.MetaKnowledgeService;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import com.adlin.orin.modules.multimodal.service.MultimodalFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@Primary
@CacheConfig(cacheNames = "agents")
public class AgentManageServiceImpl implements AgentManageService {

    private final DifyIntegrationService difyIntegrationService;
    private final SiliconFlowIntegrationService siliconFlowIntegrationService;
    private final AgentAccessProfileRepository accessProfileRepository;
    private final AgentMetadataRepository metadataRepository;
    private final AgentHealthStatusRepository healthStatusRepository;
    private final AuditLogService auditLogService;
    private final MultimodalFileService multimodalFileService;
    private final MetaKnowledgeService metaKnowledgeService;

    // Explicit constructor injection to ensure all dependencies are handled
    // correctly
    public AgentManageServiceImpl(
            DifyIntegrationService difyIntegrationService,
            SiliconFlowIntegrationService siliconFlowIntegrationService,
            AgentAccessProfileRepository accessProfileRepository,
            AgentMetadataRepository metadataRepository,
            AgentHealthStatusRepository healthStatusRepository,
            AuditLogService auditLogService,
            MultimodalFileService multimodalFileService,
            MetaKnowledgeService metaKnowledgeService) {
        this.difyIntegrationService = difyIntegrationService;
        this.siliconFlowIntegrationService = siliconFlowIntegrationService;
        this.accessProfileRepository = accessProfileRepository;
        this.metadataRepository = metadataRepository;
        this.healthStatusRepository = healthStatusRepository;
        this.auditLogService = auditLogService;
        this.multimodalFileService = multimodalFileService;
        this.metaKnowledgeService = metaKnowledgeService;
    }

    @jakarta.annotation.PostConstruct
    public void fixLegacyProviderTypes() {
        log.info("Checking for legacy provider type 'SILICONGFLOW'...");
        try {
            // Fix Metadata
            java.util.List<AgentMetadata> metadataList = metadataRepository.findAll();
            boolean fixedMeta = false;
            for (AgentMetadata meta : metadataList) {
                if ("SILICONGFLOW".equals(meta.getProviderType())) {
                    meta.setProviderType("SiliconFlow");
                    metadataRepository.save(meta);
                    fixedMeta = true;
                }
            }
            if (fixedMeta)
                log.info("Fixed legacy provider types in AgentMetadata.");

            // Fix Health Status
            java.util.List<AgentHealthStatus> healthList = healthStatusRepository.findAll();
            boolean fixedHealth = false;
            for (AgentHealthStatus status : healthList) {
                if ("SILICONGFLOW".equals(status.getProviderType())) {
                    status.setProviderType("SiliconFlow");
                    healthStatusRepository.save(status);
                    fixedHealth = true;
                }
            }
            if (fixedHealth)
                log.info("Fixed legacy provider types in AgentHealthStatus.");

        } catch (Exception e) {
            log.warn("Failed to migrate legacy provider types: {}", e.getMessage());
        }
    }

    @Override
    @CacheEvict(value = "agent_list", allEntries = true)
    public AgentMetadata onboardAgent(String endpointUrl, String apiKey, String datasetApiKey) {
        log.info("Attempting to onboard agent from: {}", endpointUrl);

        // 1. Identify Provider
        String provider = identifyProvider(endpointUrl);
        log.info("Identified provider: {}", provider);

        // 2. Validate Connection & Fetch Basic Info (External API Call via Integration
        // Service)
        String agentName;
        String modelName;

        if ("DIFY".equals(provider)) {
            if (!difyIntegrationService.testConnection(endpointUrl, apiKey)) {
                throw new RuntimeException("Failed to connect to Dify agent");
            }
            // Fetch App Info to get Name
            // Note: For Dify, we might need a separate API call to get app info if not
            // standard
            // Mocking name fetch for now or use user input later.
            // Let's assume we can get it or default it.
            try {
                java.util.Map<String, Object> appInfo = (java.util.Map<String, Object>) difyIntegrationService
                        .getApplications(endpointUrl, apiKey).orElse(java.util.Map.of("name", "Unknown Dify Agent"));
                // The getApplications currently returns raw object, might need casting or
                // proper
                // DTO
                agentName = "Dify Agent"; // Simplified for this step
                modelName = "dify-app"; // Dify abstracts the model
            } catch (Exception e) {
                agentName = "Dify Agent (Unreachable)";
                modelName = "unknown";
            }
        } else if ("SiliconFlow".equals(provider)) {
            if (!siliconFlowIntegrationService.testConnection(endpointUrl, apiKey)) {
                throw new RuntimeException("Failed to connect to SiliconFlow agent");
            }
            agentName = "SiliconFlow Model";
            modelName = "deepseek-ai/DeepSeek-V3"; // Default or detect
            // In a real scenario, we might want to list models or let user specify which
            // model to use
        } else {
            throw new RuntimeException("Unsupported provider or unable to identify");
        }

        // 3. Create/Update Agent Access Profile
        // We use a generated ID for internal management
        String agentId = UUID.randomUUID().toString();

        AgentAccessProfile profile = AgentAccessProfile.builder()
                .agentId(agentId)
                .endpointUrl(endpointUrl)
                .apiKey(apiKey)
                .datasetApiKey(datasetApiKey) // Optional
                .createdAt(LocalDateTime.now())
                .connectionStatus("ACTIVE")
                .build();
        profile.setUpdatedAt(LocalDateTime.now());
        accessProfileRepository.save(profile);

        // 4. Create Initial Metadata
        AgentMetadata metadata = AgentMetadata.builder()
                .agentId(agentId)
                .name(agentName)
                .description("Auto-onboarded via " + provider)
                .modelName(modelName)
                .providerType(provider) // Store provider in metadata
                .syncTime(LocalDateTime.now())
                .build();
        metadataRepository.save(metadata);

        // 5. Initialize Health Status
        AgentHealthStatus healthStatus = AgentHealthStatus.builder()
                .agentId(agentId)
                .agentName(agentName)
                .status(AgentHealthStatus.Status.RUNNING)
                .healthScore(100)
                .lastHeartbeat(System.currentTimeMillis())
                .providerType(provider)
                .modelName(modelName)
                .build();
        healthStatusRepository.save(healthStatus);

        log.info("Successfully onboarded agent with ID: {}", agentId);
        return metadata;
    }

    private String identifyProvider(String url) {
        if (url == null)
            return "UNKNOWN";
        if (url.contains("dify.ai") || url.contains("/v1")) {
            // Simple heuristic, can be improved
            return "DIFY";
        } else if (url.contains("siliconflow") || url.contains("deepseek")) {
            return "SiliconFlow";
        }
        return "SiliconFlow"; // Default fallback for now as per requirements
    }

    @Override
    @Cacheable(value = "agent_list")
    public java.util.List<AgentMetadata> getAllAgents() {
        return metadataRepository.findAll();
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(key = "#agentId"),
            @CacheEvict(value = "agent_list", allEntries = true)
    })
    public void updateAgent(String agentId, com.adlin.orin.modules.agent.dto.AgentOnboardRequest request) {
        log.info("Updating agent: {}", agentId);

        // 1. Update Metadata
        metadataRepository.findById(agentId).ifPresent(metadata -> {
            if (request.getName() != null)
                metadata.setName(request.getName());
            if (request.getModel() != null)
                metadata.setModelName(request.getModel());
            if (request.getTemperature() != null)
                metadata.setTemperature(request.getTemperature());
            if (request.getTopP() != null)
                metadata.setTopP(request.getTopP());
            if (request.getMaxTokens() != null)
                metadata.setMaxTokens(request.getMaxTokens());
            if (request.getSystemPrompt() != null)
                metadata.setSystemPrompt(request.getSystemPrompt());

            metadata.setSyncTime(LocalDateTime.now());
            metadataRepository.save(metadata);

            // Update Health Status Name sync
            healthStatusRepository.findById(agentId).ifPresent(status -> {
                status.setAgentName(metadata.getName());
                status.setModelName(metadata.getModelName());
                healthStatusRepository.save(status);
            });
        });

        // 2. Update Access Profile (Endpoint, API Key)
        accessProfileRepository.findById(agentId).ifPresent(profile -> {
            boolean changed = false;
            if (request.getEndpointUrl() != null && !request.getEndpointUrl().isEmpty()) {
                profile.setEndpointUrl(request.getEndpointUrl());
                changed = true;
            }
            if (request.getApiKey() != null && !request.getApiKey().isEmpty()
                    && !request.getApiKey().contains("****")) {
                profile.setApiKey(request.getApiKey());
                changed = true;
            }
            if (changed) {
                profile.setUpdatedAt(LocalDateTime.now());
                accessProfileRepository.save(profile);
            }
        });
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(key = "#agentId"),
            @CacheEvict(value = "agent_list", allEntries = true)
    })
    public java.util.Optional<AgentMetadata> updateAgentConfig(String agentId, AgentMetadata config) {
        return metadataRepository.findById(agentId).map(existing -> {
            boolean metadataChanged = false;
            boolean profileChanged = false;

            // Update Metadata fields
            if (config.getName() != null) {
                existing.setName(config.getName());
                metadataChanged = true;
            }
            if (config.getDescription() != null) {
                existing.setDescription(config.getDescription());
                metadataChanged = true;
            }
            if (config.getModelName() != null) {
                existing.setModelName(config.getModelName());
                metadataChanged = true;
            }
            if (config.getTemperature() != null) {
                existing.setTemperature(config.getTemperature());
                metadataChanged = true;
            }
            if (config.getTopP() != null) {
                existing.setTopP(config.getTopP());
                metadataChanged = true;
            }
            if (config.getMaxTokens() != null) {
                existing.setMaxTokens(config.getMaxTokens());
                metadataChanged = true;
            }
            if (config.getSystemPrompt() != null) {
                existing.setSystemPrompt(config.getSystemPrompt());
                metadataChanged = true;
            }

            // Update associated Profile fields if provided in 'config' (hacky but
            // convenient)
            // Assuming config object might carry these temporarily or we fetch profile
            // separate.
            // For now, let's assume we update profile only if needed via a separate method
            // or
            // extended DTO.
            // But checking the AgentMetadata entity, it doesn't have api key.
            // So we skip profile update here unless we change the signature to accept DTO.
            // Let's stick to Metadata update.

            if (metadataChanged) {
                existing.setSyncTime(LocalDateTime.now());
                AgentMetadata saved = metadataRepository.save(existing);

                // Update Health Status Name sync
                healthStatusRepository.findById(agentId).ifPresent(status -> {
                    status.setAgentName(saved.getName());
                    healthStatusRepository.save(status);
                });
                return saved;
            }
            return existing;
        });
    }

    @Override
    @Caching(evict = {
            @CacheEvict(key = "#agentId"),
            @CacheEvict(value = "agent_list", allEntries = true)
    })
    public void deleteAgent(String agentId) {
        accessProfileRepository.deleteById(agentId);
        metadataRepository.deleteById(agentId);
        healthStatusRepository.deleteById(agentId);
        log.info("Deleted agent: {}", agentId);
    }

    @Override
    public AgentAccessProfile getAgentAccessProfile(String agentId) {
        return accessProfileRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent access profile not found for ID: " + agentId));
    }

    @Override
    @Cacheable(key = "#agentId")
    public AgentMetadata getAgentMetadata(String agentId) {
        return metadataRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent metadata not found for ID: " + agentId));
    }

    private java.util.Optional<Object> chatWithDify(AgentAccessProfile profile, String message, String conversationId) {
        // Dify implementation
        return difyIntegrationService.sendMessage(profile.getEndpointUrl(), profile.getApiKey(), conversationId,
                message);
    }

    private java.util.Optional<Object> chatWithSiliconFlow(AgentAccessProfile profile, AgentMetadata metadata,
            String message, String fileId) {

        java.util.List<java.util.Map<String, Object>> messages = new java.util.ArrayList<>();

        // 1. Dynamic System Prompt Assembly (Includes Memory)
        String dynamicSystemPrompt = metaKnowledgeService.assembleSystemPrompt(metadata.getAgentId());

        // Fallback to static if dynamic is empty
        if (dynamicSystemPrompt.trim().isEmpty()) {
            if (metadata.getSystemPrompt() != null && !metadata.getSystemPrompt().isEmpty()) {
                dynamicSystemPrompt = metadata.getSystemPrompt();
            }
        }

        if (!dynamicSystemPrompt.isEmpty()) {
            messages.add(java.util.Map.of("role", "system", "content", dynamicSystemPrompt));
        }

        // 1.5. Add Short-term History (Context Window)
        // Retrieve last 10 messages for this agent to provide context
        try {
            java.util.List<AuditLog> historyLogs = auditLogService.getRecentAgentLogs(metadata.getAgentId(), 10);
            for (AuditLog logItem : historyLogs) {
                // Add User Question
                if (logItem.getRequestParams() != null && !logItem.getRequestParams().isEmpty()) {
                    messages.add(java.util.Map.of("role", "user", "content", logItem.getRequestParams()));
                }
                // Add AI Answer
                if (logItem.getResponseContent() != null && !logItem.getResponseContent().isEmpty()) {
                    messages.add(java.util.Map.of("role", "assistant", "content", logItem.getResponseContent()));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve chat history for context: {}", e.getMessage());
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
    public java.util.Optional<Object> chat(String agentId, String message, String fileId) {
        // Generate a new conversation ID for this chat session
        String conversationId = UUID.randomUUID().toString();
        return chatWithConversation(agentId, message, fileId, conversationId);
    }

    /**
     * Chat with agent using a specific conversation ID
     */
    public java.util.Optional<Object> chatWithConversation(String agentId, String message, String fileId,
            String conversationId) {
        log.info("Chatting with agent: {} (conversationId: {}, fileId: {})", agentId, conversationId, fileId);

        AgentAccessProfile profile = accessProfileRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent access profile not found for ID: " + agentId));

        AgentMetadata metadata = metadataRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent metadata not found for ID: " + agentId));

        long startTime = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        String responseContent = "";
        int statusCode = 200;
        int promptTokens = 0;
        int completionTokens = 0;
        double cost = 0.0;

        try {
            // Use the fileId directly for SiliconFlow or other providers
            java.util.Optional<Object> response = chatWithSiliconFlow(profile, metadata, message, fileId);

            if (response.isPresent()) {
                success = true;
                // Parse response to extract content and usage
                if (response.get() instanceof java.util.Map) {
                    java.util.Map<?, ?> respMap = (java.util.Map<?, ?>) response.get();
                    try {
                        if (respMap.containsKey("choices")) {
                            java.util.List<?> choices = (java.util.List<?>) respMap.get("choices");
                            if (!choices.isEmpty()) {
                                java.util.Map<?, ?> choice = (java.util.Map<?, ?>) choices.get(0);
                                java.util.Map<?, ?> msg = (java.util.Map<?, ?>) choice.get("message");
                                responseContent = (String) msg.get("content");
                            }
                        }
                        if (respMap.containsKey("usage")) {
                            java.util.Map<?, ?> usage = (java.util.Map<?, ?>) respMap.get("usage");
                            Object promptTokensObj = usage.get("prompt_tokens");
                            Object completionTokensObj = usage.get("completion_tokens");
                            promptTokens = (promptTokensObj instanceof Integer) ? (Integer) promptTokensObj : 0;
                            completionTokens = (completionTokensObj instanceof Integer) ? (Integer) completionTokensObj
                                    : 0;
                            cost = (promptTokens + completionTokens) * 0.000002;
                        }
                    } catch (Exception e) {
                        responseContent = response.get().toString();
                    }
                } else {
                    responseContent = response.get().toString();
                }
                return response;
            } else {
                errorMessage = "No response from agent provider";
                statusCode = 502;
                return java.util.Optional.empty();
            }
        } catch (Exception e) {
            errorMessage = e.getMessage();
            statusCode = 500;
            log.error("Chat error", e);
            throw e;
        } finally {
            // Audit Log
            try {
                long duration = System.currentTimeMillis() - startTime;
                String provider = metadata.getProviderType();
                auditLogService.logApiCall(
                        "admin",
                        profile.getApiKey(),
                        agentId,
                        provider,
                        profile.getEndpointUrl(),
                        "POST",
                        metadata.getModelName(),
                        "127.0.0.1",
                        "ORIN-Backend",
                        message,
                        responseContent,
                        statusCode,
                        duration,
                        promptTokens,
                        completionTokens,
                        cost,
                        success,
                        errorMessage,
                        null,
                        conversationId);
            } catch (Exception e) {
                log.error("Failed to save audit log in finally block", e);
            }
        }
    }

    @Override
    public java.util.Optional<Object> chat(String agentId, String message,
            org.springframework.web.multipart.MultipartFile file) {
        // Generate a new conversation ID for this chat session
        String conversationId = UUID.randomUUID().toString();
        log.info("Chatting with agent: {} (conversationId: {})", agentId, conversationId);

        AgentAccessProfile profile = accessProfileRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent access profile not found for ID: " + agentId));

        AgentMetadata metadata = metadataRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent metadata not found for ID: " + agentId));

        // Handle file upload if present
        String fileNote = "";
        String fileUrl = null;
        if (file != null && !file.isEmpty()) {
            try {
                // Upload to MinIO/Local via MultimodalFileService
                // Assuming userId "admin" for now, or fetch from context
                com.adlin.orin.modules.multimodal.entity.MultimodalFile uploadedFile = multimodalFileService.uploadFile(
                        file, "admin");
                fileUrl = uploadedFile.getStoragePath(); // Use the storage path for LLM
                fileNote = "[File Uploaded: " + file.getOriginalFilename() + "] ";
                log.info("File uploaded for chat: {}", fileUrl);
            } catch (Exception e) {
                log.error("Failed to upload file for chat", e);
                return java.util.Optional.of("Error: Failed to upload file - " + e.getMessage());
            }
        }

        long startTime = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        String fullUserMessage = fileNote + message;
        String responseContent = "";
        int statusCode = 200;
        int promptTokens = 0;
        int completionTokens = 0;
        double cost = 0.0;

        try {
            java.util.Optional<Object> response;
            String provider = metadata.getProviderType();
            if ("DIFY".equalsIgnoreCase(provider)) {
                // For Dify, we need a conversation ID, using empty string for now
                response = chatWithDify(profile, fullUserMessage, "");
            } else {
                response = chatWithSiliconFlow(profile, metadata, fullUserMessage, fileUrl);
            }

            if (response.isPresent()) {
                success = true;
                // Parse response to extract content and usage (Simplified)
                // Assuming response is a Map from Integration Service
                if (response.get() instanceof java.util.Map) {
                    java.util.Map<?, ?> respMap = (java.util.Map<?, ?>) response.get();
                    // Extract logic depends on provider structure. SiliconFlow returns OpenAI
                    // format.
                    try {
                        if (respMap.containsKey("choices")) {
                            java.util.List<?> choices = (java.util.List<?>) respMap.get("choices");
                            if (!choices.isEmpty()) {
                                java.util.Map<?, ?> choice = (java.util.Map<?, ?>) choices.get(0);
                                java.util.Map<?, ?> msg = (java.util.Map<?, ?>) choice.get("message");
                                responseContent = (String) msg.get("content");
                            }
                        }
                        if (respMap.containsKey("usage")) {
                            java.util.Map<?, ?> usage = (java.util.Map<?, ?>) respMap.get("usage");
                            Object promptTokensObj = usage.get("prompt_tokens");
                            Object completionTokensObj = usage.get("completion_tokens");
                            promptTokens = (promptTokensObj instanceof Integer) ? (Integer) promptTokensObj : 0;
                            completionTokens = (completionTokensObj instanceof Integer) ? (Integer) completionTokensObj
                                    : 0;
                            // Simple cost estimation (e.g. $0.002 per 1k input, $0.002 per 1k output) -
                            // Mock
                            cost = (promptTokens + completionTokens) * 0.000002;
                        }
                    } catch (Exception e) {
                        responseContent = response.get().toString(); // Fallback
                    }
                } else {
                    responseContent = response.get().toString();
                }

                return response;
            } else {
                errorMessage = "No response from agent provider";
                statusCode = 502; // Bad Gateway
                return java.util.Optional.empty();
            }

        } catch (Exception e) {
            errorMessage = e.getMessage();
            statusCode = 500;
            log.error("Chat error", e);
            throw e;
        } finally {
            // Audit Log
            try {
                long duration = System.currentTimeMillis() - startTime;
                String provider = metadata.getProviderType();
                auditLogService.logApiCall(
                        "admin", // UserId (mock)
                        profile.getApiKey(), // ApiKeyId (using actual key for tracking)
                        agentId, // ProviderId
                        provider, // ProviderType
                        profile.getEndpointUrl(), // Endpoint
                        "POST", // Method
                        metadata.getModelName(), // Model
                        "127.0.0.1", // IP
                        "ORIN-Backend", // UserAgent
                        fullUserMessage, // Request
                        responseContent, // Response
                        statusCode,
                        duration,
                        promptTokens,
                        completionTokens,
                        cost,
                        success,
                        errorMessage,
                        null, // workflowId
                        conversationId); // conversationId
            } catch (Exception e) {
                log.error("Failed to save audit log in finally block", e);
            }
        }
    }
}
