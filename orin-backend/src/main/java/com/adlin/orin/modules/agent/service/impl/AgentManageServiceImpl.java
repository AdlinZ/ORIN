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
import com.adlin.orin.modules.agent.service.provider.MultiModalProvider;
import com.adlin.orin.modules.agent.service.provider.MultiModalProvider.InteractionRequest;
import com.adlin.orin.modules.agent.service.provider.MultiModalProvider.InteractionResult;
import java.util.Map;
import java.util.HashMap;
import com.adlin.orin.modules.model.service.SiliconFlowIntegrationService;
import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.audit.service.AuditLogService;
import com.adlin.orin.modules.knowledge.service.meta.MetaKnowledgeService;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.entity.AgentStatus;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import com.adlin.orin.modules.model.service.MinimaxIntegrationService;
import com.adlin.orin.modules.model.service.OllamaIntegrationService;
import com.adlin.orin.modules.multimodal.service.MultimodalFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.util.List;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@Primary
@CacheConfig(cacheNames = "agents")
public class AgentManageServiceImpl implements AgentManageService {

    private final DifyIntegrationService difyIntegrationService;
    private final SiliconFlowIntegrationService siliconFlowIntegrationService;
    private final MinimaxIntegrationService minimaxIntegrationService;
    private final OllamaIntegrationService ollamaIntegrationService;
    private final AgentAccessProfileRepository accessProfileRepository;
    private final AgentMetadataRepository metadataRepository;
    private final AgentHealthStatusRepository healthStatusRepository;
    private final AuditLogService auditLogService;
    private final com.adlin.orin.modules.conversation.service.ConversationLogService conversationLogService;
    private final MultimodalFileService multimodalFileService;
    private final MetaKnowledgeService metaKnowledgeService;
    private final com.adlin.orin.modules.model.repository.ModelMetadataRepository modelMetadataRepository;
    private final Map<String, MultiModalProvider> providerMap = new HashMap<>();

    // Explicit constructor injection to ensure all dependencies are handled
    // correctly
    public AgentManageServiceImpl(
            DifyIntegrationService difyIntegrationService,
            SiliconFlowIntegrationService siliconFlowIntegrationService,
            AgentAccessProfileRepository accessProfileRepository,
            AgentMetadataRepository metadataRepository,
            AgentHealthStatusRepository healthStatusRepository,
            AuditLogService auditLogService,
            com.adlin.orin.modules.conversation.service.ConversationLogService conversationLogService,
            MultimodalFileService multimodalFileService,
            MetaKnowledgeService metaKnowledgeService,
            com.adlin.orin.modules.model.repository.ModelMetadataRepository modelMetadataRepository,
            MinimaxIntegrationService minimaxIntegrationService,
            OllamaIntegrationService ollamaIntegrationService,
            List<MultiModalProvider> providers) {
        this.difyIntegrationService = difyIntegrationService;
        this.siliconFlowIntegrationService = siliconFlowIntegrationService;
        this.minimaxIntegrationService = minimaxIntegrationService;
        this.ollamaIntegrationService = ollamaIntegrationService;
        this.accessProfileRepository = accessProfileRepository;
        this.metadataRepository = metadataRepository;
        this.healthStatusRepository = healthStatusRepository;
        this.auditLogService = auditLogService;
        this.conversationLogService = conversationLogService;
        this.multimodalFileService = multimodalFileService;
        this.metaKnowledgeService = metaKnowledgeService;
        this.modelMetadataRepository = modelMetadataRepository;

        for (MultiModalProvider p : providers) {
            this.providerMap.put(p.getProviderName().toUpperCase(), p);
        }
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
    public AgentMetadata onboardAgent(com.adlin.orin.modules.agent.dto.AgentOnboardRequest request) {
        String endpointUrl = request.getEndpointUrl();
        String apiKey = request.getApiKey();
        String datasetApiKey = request.getDatasetApiKey();
        String model = request.getModel();
        String providerType = request.getProviderType();

        log.info("Attempting to onboard agent from: {} (Provider explicitly set to: {})", endpointUrl, providerType);

        // 1. Identify Provider
        String provider = identifyProvider(endpointUrl, providerType);
        log.info("Identified provider: {}", provider);

        // 2. Validate Connection & Fetch Basic Info
        String agentName = request.getName();
        String modelName = model;

        if ("DIFY".equals(provider)) {
            if (!difyIntegrationService.testConnection(endpointUrl, apiKey)) {
                throw new RuntimeException("Failed to connect to Dify agent");
            }
            // Fetch App Info to get Name
            // Note: For Dify, we might need a separate API call to get app info if not
            // standard
            // Mocking name fetch for now or use user input later.
            // Let's assume we can get it or default it.
            // Try to fetch App Meta (App API)
            try {
                var appMetaOpt = difyIntegrationService.fetchAppMeta(endpointUrl, apiKey);
                if (appMetaOpt.isPresent()) {
                    var metaMap = appMetaOpt.get();
                    // Typically returns { "tool_icon": ..., "tool_description": ... } or general
                    // info
                    // Dify API response structure for /meta:
                    // { "tool": { "icon": "...", "name": "..." } } or similar.
                    // Actually the response for GET /meta on App API is:
                    // { "tool_icon": "...", "tool_name": "...", "tool_description": "..." }
                    // directly map.
                    // Let's safe get.
                    agentName = (String) metaMap.getOrDefault("tool_name", "Dify Agent");
                    modelName = "dify-app";
                } else {
                    // Fallback mechanism or throw
                    agentName = "Dify Agent";
                    modelName = "dify-app";
                }
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
        } else if ("MiniMax".equals(provider)) {
            if (!minimaxIntegrationService.testConnection(endpointUrl, apiKey, "abab6.5g-chat")) {
                throw new RuntimeException("Failed to connect to MiniMax agent");
            }
            agentName = "MiniMax Agent";
            modelName = "abab6.5g-chat";
        } else if ("Ollama".equals(provider)) {
            String targetModel = (modelName != null && !modelName.isEmpty()) ? modelName : "llama3";
            if (!ollamaIntegrationService.testConnection(endpointUrl, apiKey, targetModel)) {
                throw new RuntimeException("Failed to connect to Ollama agent (make sure Ollama is running)");
            }
            if (agentName == null || agentName.equals("新智能体")) {
                agentName = "Ollama Local Agent (" + targetModel + ")";
            }
            modelName = targetModel;
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
        String viewType = determineViewType(modelName, provider, endpointUrl, apiKey);
        AgentMetadata metadata = AgentMetadata.builder()
                .agentId(agentId)
                .name(agentName)
                .description("Auto-onboarded via " + provider)
                .modelName(modelName)
                .providerType(provider) // Store provider in metadata
                .mode("chat") // Default to chat, refresh will update if detectable or manual
                .viewType(viewType) // Set viewType based on model type
                .syncTime(LocalDateTime.now())
                .build();

        // Sync Parameters immediately
        if ("DIFY".equals(provider)) {
            try {
                difyIntegrationService.fetchAppParameters(endpointUrl, apiKey).ifPresent(params -> {
                    try {
                        String paramsJson = new ObjectMapper().writeValueAsString(params);
                        metadata.setParameters(paramsJson);
                    } catch (Exception e) {
                        log.warn("Failed to serialize parameters", e);
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to fetch parameters during onboard", e);
            }
        }

        metadataRepository.save(metadata);

        // 5. Initialize Health Status
        AgentHealthStatus healthStatus = AgentHealthStatus.builder()
                .agentId(agentId)
                .agentName(agentName)
                .status(AgentStatus.RUNNING)
                .healthScore(100)
                .lastHeartbeat(System.currentTimeMillis())
                .providerType(provider)
                .modelName(modelName)
                .viewType(viewType) // Set viewType to match metadata
                .build();
        healthStatusRepository.save(healthStatus);

        log.info("Successfully onboarded agent with ID: {}", agentId);
        return metadata;
    }

    @Override
    public AgentMetadata onboardAgent(String endpointUrl, String apiKey, String datasetApiKey) {
        com.adlin.orin.modules.agent.dto.AgentOnboardRequest request = new com.adlin.orin.modules.agent.dto.AgentOnboardRequest();
        request.setEndpointUrl(endpointUrl);
        request.setApiKey(apiKey);
        request.setDatasetApiKey(datasetApiKey);
        return onboardAgent(request);
    }

    private String identifyProvider(String url) {
        return identifyProvider(url, null);
    }

    private String identifyProvider(String url, String explicitProvider) {
        if (explicitProvider != null) {
            if (explicitProvider.equalsIgnoreCase("Ollama") || explicitProvider.equalsIgnoreCase("local")) {
                return "Ollama";
            }
            if (explicitProvider.equalsIgnoreCase("Dify")) {
                return "DIFY";
            }
            if (explicitProvider.equalsIgnoreCase("SiliconFlow")) {
                return "SiliconFlow";
            }
            if (explicitProvider.equalsIgnoreCase("MiniMax")) {
                return "MiniMax";
            }
        }

        if (url == null)
            return "UNKNOWN";
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains("ollama") || lowerUrl.contains("11434"))
            return "Ollama";
        if (lowerUrl.contains("siliconflow"))
            return "SiliconFlow";
        if (lowerUrl.contains("deepseek"))
            return "DeepSeek";
        if (lowerUrl.contains("minimax"))
            return "MiniMax";
        return "DIFY"; // Default fallback
    }

    /**
     * Determine viewType using metadata or API lookups
     */
    private String determineViewType(String modelName, String provider, String endpoint, String apiKey) {
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
            log.warn("Failed to get viewType for model {}: {}", modelName, e.getMessage());
        }

        // 2. Try Provider specific API Lookups
        if ("SiliconFlow".equalsIgnoreCase(provider) || "SiliconFlow".equals(provider)) {
            try {
                String apiType = siliconFlowIntegrationService.resolveSiliconFlowViewType(endpoint, apiKey, modelName);
                if (apiType != null && !"CHAT".equals(apiType)) {
                    return apiType;
                }
            } catch (Exception e) {
                log.warn("API type resolution failed for SiliconFlow: {}", e.getMessage());
            }
        }

        // 3. Heuristic Fallback (Optional, but user prefers API/Data)
        return inferTypeFromModelName(modelName);
    }

    private String inferTypeFromModelName(String modelName) {
        if (modelName == null)
            return "CHAT";
        String modelLower = modelName.toLowerCase();

        // Use very specific markers if any, otherwise return CHAT
        if (modelLower.contains("wan-") || modelLower.contains("-t2v") || modelLower.contains("-i2v"))
            return "TTV";
        if (modelLower.contains("-tts") || modelLower.contains("cosyvoice"))
            return "TTS";
        if (modelLower.contains("flux") || modelLower.contains("stable-diffusion"))
            return "TTI";

        return "CHAT";
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
            if (request.getModel() != null) {
                metadata.setModelName(request.getModel());
                // Sync viewType when model changes
                String newViewType = determineViewType(request.getModel(), metadata.getProviderType(),
                        accessProfileRepository.findById(agentId).map(AgentAccessProfile::getEndpointUrl).orElse(null),
                        accessProfileRepository.findById(agentId).map(AgentAccessProfile::getApiKey).orElse(null));
                metadata.setViewType(newViewType);
            }
            if (request.getTemperature() != null)
                metadata.setTemperature(request.getTemperature());
            if (request.getTopP() != null)
                metadata.setTopP(request.getTopP());
            if (request.getMaxTokens() != null)
                metadata.setMaxTokens(request.getMaxTokens());
            if (request.getSystemPrompt() != null)
                metadata.setSystemPrompt(request.getSystemPrompt());

            // Update TTS & Image Parameters in the parameters JSON field
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> params = new HashMap<>();
                if (metadata.getParameters() != null && !metadata.getParameters().isEmpty()) {
                    params = mapper.readValue(metadata.getParameters(), new TypeReference<Map<String, Object>>() {
                    });
                }

                if (request.getVoice() != null)
                    params.put("voice", request.getVoice());
                if (request.getSpeed() != null)
                    params.put("speed", request.getSpeed());
                if (request.getGain() != null)
                    params.put("gain", request.getGain());
                if (request.getImageSize() != null)
                    params.put("imageSize", request.getImageSize());
                if (request.getSeed() != null)
                    params.put("seed", request.getSeed());
                if (request.getGuidanceScale() != null)
                    params.put("guidanceScale", request.getGuidanceScale());
                if (request.getInferenceSteps() != null)
                    params.put("inferenceSteps", request.getInferenceSteps());
                if (request.getNegativePrompt() != null)
                    params.put("negativePrompt", request.getNegativePrompt());
                if (request.getVideoSize() != null)
                    params.put("videoSize", request.getVideoSize());
                if (request.getVideoDuration() != null)
                    params.put("videoDuration", request.getVideoDuration());

                metadata.setParameters(mapper.writeValueAsString(params));
            } catch (Exception e) {
                log.warn("Failed to update extra parameters for agent {}: {}", agentId, e.getMessage());
            }

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
            // boolean profileChanged = false; // Unused

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

                // Update Health Status Name/ViewType/Model sync for list view consistency
                healthStatusRepository.findById(agentId).ifPresent(status -> {
                    status.setAgentName(saved.getName());
                    status.setViewType(saved.getViewType());
                    status.setModelName(saved.getModelName());
                    status.setProviderType(saved.getProviderType());
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

    private java.util.Optional<Object> chatWithSiliconFlow(AgentAccessProfile profile, AgentMetadata metadata,
            String message, String fileId, String overrideSystemPrompt, String conversationId,
            Boolean enableThinking, Integer thinkingBudget) {

        java.util.List<java.util.Map<String, Object>> messages = new java.util.ArrayList<>();

        // 1. Dynamic System Prompt Assembly (Includes Memory)
        String dynamicSystemPrompt;

        if (overrideSystemPrompt != null && !overrideSystemPrompt.trim().isEmpty()) {
            // Using Override Prompt for Sandbox testing
            dynamicSystemPrompt = overrideSystemPrompt;
        } else {
            // Normal flow: Assemble from DB
            dynamicSystemPrompt = metaKnowledgeService.assembleSystemPrompt(metadata.getAgentId());

            // Fallback to static if dynamic is empty
            if (dynamicSystemPrompt.trim().isEmpty()) {
                if (metadata.getSystemPrompt() != null && !metadata.getSystemPrompt().isEmpty()) {
                    dynamicSystemPrompt = metadata.getSystemPrompt();
                }
            }
        }

        if (!dynamicSystemPrompt.isEmpty()) {
            messages.add(java.util.Map.of("role", "system", "content", dynamicSystemPrompt));
        }

        // 1.5. Add Short-term History (Context Window)
        // Retrieve last 10 messages for this agent AND conversation to provide context
        try {
            java.util.List<AuditLog> historyLogs;
            if (conversationId != null && !conversationId.isEmpty()) {
                historyLogs = auditLogService.getRecentConversationLogs(conversationId, 10);
            } else {
                historyLogs = auditLogService.getRecentAgentLogs(metadata.getAgentId(), 10);
            }

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
                maxTokens,
                enableThinking,
                thinkingBudget);
    }

    /**
     * Generate image with SiliconFlow
     */
    private java.util.Optional<Object> generateImageWithSiliconFlow(AgentAccessProfile profile,
            AgentMetadata metadata, String message) {
        try {
            // 解析消息，提取参数
            // 消息可能是JSON格式，包含prompt和其他参数
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            String prompt = message;

            // 尝试解析JSON消息
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> messageMap = mapper.readValue(message, java.util.Map.class);

                if (messageMap.containsKey("prompt")) {
                    prompt = (String) messageMap.get("prompt");
                }
                if (messageMap.containsKey("query")) {
                    prompt = (String) messageMap.get("query");
                }

                // 提取其他参数
                if (messageMap.containsKey("image_size")) {
                    params.put("image_size", messageMap.get("image_size"));
                }
                if (messageMap.containsKey("aspect_ratio")) {
                    // 将 aspect_ratio 转换为 image_size
                    String aspectRatio = (String) messageMap.get("aspect_ratio");
                    if ("1:1".equals(aspectRatio)) {
                        params.put("image_size", "1024x1024");
                    } else if ("16:9".equals(aspectRatio)) {
                        params.put("image_size", "1344x768");
                    } else if ("9:16".equals(aspectRatio)) {
                        params.put("image_size", "768x1344");
                    }
                }
                if (messageMap.containsKey("negative_prompt")) {
                    params.put("negative_prompt", messageMap.get("negative_prompt"));
                }
                if (messageMap.containsKey("seed")) {
                    params.put("seed", messageMap.get("seed"));
                }
            } catch (Exception e) {
                // 如果不是JSON，直接使用message作为prompt
                log.debug("Message is not JSON, using as plain prompt");
            }

            java.util.Optional<Object> res = siliconFlowIntegrationService.generateImage(
                    profile.getEndpointUrl(),
                    profile.getApiKey(),
                    metadata.getModelName(),
                    prompt,
                    params);

            if (res.isPresent()) {
                Map<String, Object> result = new HashMap<>();
                result.put("status", "SUCCESS");
                result.put("data", res.get());
                result.put("dataType", "IMAGE");
                return java.util.Optional.of(result);
            }
            return java.util.Optional.empty();
        } catch (Exception e) {
            log.error("Failed to generate image", e);
            return java.util.Optional.empty();
        }
    }

    /**
     * Transcribe audio with SiliconFlow
     */
    private java.util.Optional<Object> transcribeAudioWithSiliconFlow(AgentAccessProfile profile,
            AgentMetadata metadata, String fileId) {
        try {
            if (fileId == null || fileId.isEmpty()) {
                log.error("No file ID provided for transcription");
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("status", "FAILED");
                errorResult.put("errorMessage", "Missing file ID for audio transcription. Please upload a file first.");
                return java.util.Optional.of(errorResult);
            }

            com.adlin.orin.modules.multimodal.entity.MultimodalFile fileEntity = multimodalFileService.getFile(fileId);
            byte[] audioData = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(fileEntity.getStoragePath()));

            java.util.Optional<Object> res = siliconFlowIntegrationService.transcribeAudio(
                    profile.getEndpointUrl(),
                    profile.getApiKey(),
                    metadata.getModelName(),
                    audioData,
                    fileEntity.getFileName());

            if (res.isPresent()) {
                Map<String, Object> result = new HashMap<>();
                result.put("status", "SUCCESS");
                result.put("data", res.get());
                result.put("dataType", "TEXT");
                return java.util.Optional.of(result);
            }
            return java.util.Optional.empty();
        } catch (Exception e) {
            log.error("Failed to transcribe audio", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "FAILED");
            errorResult.put("errorMessage", "Transcription failed: " + e.getMessage());
            return java.util.Optional.of(errorResult);
        }
    }

    private java.util.Optional<Object> generateAudioWithSiliconFlow(AgentAccessProfile profile, AgentMetadata metadata,
            String message) {
        try {
            // Extract parameters (input is message string, or JSON?)
            // Front-end AudioGenerator sends JSON payload, but
            // `AgentManageServiceImpl.chat` receives `message` string.
            // If message is JSON, we parse it. Otherwise treat as input text.

            String prompt = message;
            Map<String, Object> params = new HashMap<>();

            try {
                // Try to parse message as JSON if it looks like one
                if (message != null && message.trim().startsWith("{")) {
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> payload = mapper.readValue(message, new TypeReference<Map<String, Object>>() {
                    });
                    if (payload.containsKey("input"))
                        prompt = (String) payload.get("input");
                    params.putAll(payload);
                }
            } catch (Exception e) {
                // Not JSON, use message as prompt
                log.debug("Message is not JSON, using as raw input");
            }

            if (metadata.getParameters() != null) {
                // Merge stored metadata params as defaults if not present
                // params.putAll(metadata.getParameters()); // Be careful with overwriting
            }

            // Clean parameters: remove empty strings and nulls
            params.entrySet().removeIf(
                    e -> e.getValue() == null || (e.getValue() instanceof String && ((String) e.getValue()).isEmpty()));

            log.info("Generating audio for agent: {} with model: {} and params: {}", metadata.getAgentId(),
                    metadata.getModelName(), params);

            // Call SiliconFlow
            java.util.Optional<byte[]> audioDataOpt = siliconFlowIntegrationService.generateAudio(
                    profile.getEndpointUrl(),
                    profile.getApiKey(),
                    (String) params.getOrDefault("model", metadata.getModelName()),
                    prompt,
                    params);

            if (audioDataOpt.isPresent()) {
                byte[] audioData = audioDataOpt.get();
                // Save to file
                String filename = "tts_" + UUID.randomUUID().toString() + ".mp3";
                com.adlin.orin.modules.multimodal.entity.MultimodalFile savedFile = multimodalFileService
                        .uploadFile(audioData, filename, "audio/mpeg", "agent:" + metadata.getAgentId());

                // Return response in format expected by AudioGenerator and
                // useAgentInteraction.js
                Map<String, Object> data = new HashMap<>();
                data.put("audio_url", "/api/v1/multimodal/files/" + savedFile.getId() + "/download");
                data.put("file_id", savedFile.getId());
                data.put("text", prompt);

                Map<String, Object> result = new HashMap<>();
                result.put("status", "SUCCESS");
                result.put("data", data);
                result.put("dataType", "AUDIO");

                return java.util.Optional.of(result);
            }

            return java.util.Optional.empty();
        } catch (Exception e) {
            log.error("Failed to generate audio", e);
            // Return error response so frontend shows the message
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "FAILED");
            errorResult.put("errorMessage", e.getMessage());
            return java.util.Optional.of(errorResult);
        }
    }

    private java.util.Optional<Object> chatWithOllama(AgentAccessProfile profile, AgentMetadata metadata,
            String message, String fileId, String overrideSystemPrompt, String conversationId,
            Boolean enableThinking, Integer thinkingBudget) {

        java.util.List<java.util.Map<String, Object>> messages = new java.util.ArrayList<>();

        // 1. Dynamic System Prompt Assembly
        String dynamicSystemPrompt;
        if (overrideSystemPrompt != null && !overrideSystemPrompt.trim().isEmpty()) {
            dynamicSystemPrompt = overrideSystemPrompt;
        } else {
            dynamicSystemPrompt = metaKnowledgeService.assembleSystemPrompt(metadata.getAgentId());
            if (dynamicSystemPrompt.trim().isEmpty() && metadata.getSystemPrompt() != null) {
                dynamicSystemPrompt = metadata.getSystemPrompt();
            }
        }

        if (dynamicSystemPrompt != null && !dynamicSystemPrompt.isEmpty()) {
            StringBuilder finalPrompt = new StringBuilder(dynamicSystemPrompt);
            // If thinking is disabled, explicitly tell the model not to use reasoning tags
            if (enableThinking != null && !enableThinking) {
                finalPrompt.append(
                        "\nImportant: Do not output your reasoning or thinking process. Go straight to the final answer. Do not use <thought> or <reasoning> tags.");
            }
            messages.add(java.util.Map.of("role", "system", "content", finalPrompt.toString()));
        } else if (enableThinking != null && !enableThinking) {
            messages.add(java.util.Map.of("role", "system", "content",
                    "Do not output your reasoning or thinking process. Go straight to the final answer. Do not use <thought> or <reasoning> tags."));
        }

        // 2. Add History
        try {
            java.util.List<AuditLog> historyLogs = (conversationId != null && !conversationId.isEmpty())
                    ? auditLogService.getRecentConversationLogs(conversationId, 10)
                    : auditLogService.getRecentAgentLogs(metadata.getAgentId(), 10);

            for (AuditLog logItem : historyLogs) {
                if (logItem.getRequestParams() != null && !logItem.getRequestParams().isEmpty()) {
                    messages.add(java.util.Map.of("role", "user", "content", logItem.getRequestParams()));
                }
                if (logItem.getResponseContent() != null && !logItem.getResponseContent().isEmpty()) {
                    messages.add(java.util.Map.of("role", "assistant", "content", logItem.getResponseContent()));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve chat history for Ollama context: {}", e.getMessage());
        }

        // 3. Add User Message
        messages.add(java.util.Map.of("role", "user", "content", message));

        double temperature = metadata.getTemperature() != null ? metadata.getTemperature() : 0.7;
        double topP = metadata.getTopP() != null ? metadata.getTopP() : 0.9;
        int maxTokens = metadata.getMaxTokens() != null ? metadata.getMaxTokens() : 512;

        return ollamaIntegrationService.sendMessageWithFullParams(
                profile.getEndpointUrl(),
                profile.getApiKey(),
                metadata.getModelName(),
                messages,
                temperature,
                topP,
                maxTokens);
    }

    @Override
    public java.util.Optional<Object> chat(String agentId, String message, String fileId) {
        return chat(agentId, message, fileId, null);
    }

    @Override
    public java.util.Optional<Object> chat(String agentId, String message, String fileId, String overrideSystemPrompt,
            String conversationId) {
        return chat(agentId, message, fileId, overrideSystemPrompt, conversationId, null, null);
    }

    @Override
    public java.util.Optional<Object> chat(String agentId, String message, String fileId, String overrideSystemPrompt,
            String conversationId, Boolean enableThinking, Integer thinkingBudget) {
        // Use provided conversation ID or generate a new one
        String effectiveConversationId = (conversationId != null && !conversationId.isEmpty())
                ? conversationId
                : UUID.randomUUID().toString();

        return chatWithConversation(agentId, message, fileId, effectiveConversationId, overrideSystemPrompt,
                enableThinking, thinkingBudget);
    }

    /**
     * Chat with agent using a specific conversation ID
     */
    public java.util.Optional<Object> chatWithConversation(String agentId, String message, String fileId,
            String conversationId) {
        return chatWithConversation(agentId, message, fileId, conversationId, null, null, null);
    }

    public java.util.Optional<Object> chatWithConversation(String agentId, String message, String fileId,
            String conversationId, String overrideSystemPrompt) {
        return chatWithConversation(agentId, message, fileId, conversationId, overrideSystemPrompt, null, null);
    }

    public java.util.Optional<Object> chatWithConversation(String agentId, String message, String fileId,
            String conversationId, String overrideSystemPrompt, Boolean enableThinking, Integer thinkingBudget) {
        log.info("Chatting with agent: {} (conversationId: {}, fileId: {}, hasOverride: {}, thinking: {})",
                agentId, conversationId, fileId, overrideSystemPrompt != null, enableThinking);

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
            // 根据 providerType 和 viewType 路由到不同的处理逻辑
            String providerType = metadata.getProviderType();
            String viewType = metadata.getViewType();
            java.util.Optional<Object> response;

            if ("DIFY".equalsIgnoreCase(providerType)) {
                log.info("Routing to Dify interaction for agent {} (viewType: {})", agentId, viewType);
                MultiModalProvider provider = providerMap.get("DIFY");
                if (provider != null) {
                    Map<String, Object> context = new HashMap<>();
                    context.put("conversationId", conversationId);
                    MultiModalProvider.InteractionRequest req = new MultiModalProvider.InteractionRequest(
                            fileId != null ? "IMAGE" : "TEXT",
                            message,
                            null,
                            context);
                    MultiModalProvider.InteractionResult result = provider.process(metadata, req);
                    Map<String, Object> responseMap = new HashMap<>();
                    responseMap.put("status", result.getStatus());
                    responseMap.put("data", result.getData());
                    responseMap.put("dataType", result.getDataType());
                    responseMap.put("viewType", metadata.getViewType());

                    if (result.getData() instanceof java.util.Map) {
                        java.util.Map<?, ?> dataMap = (java.util.Map<?, ?>) result.getData();
                        if (dataMap.containsKey("conversation_id")) {
                            responseMap.put("conversation_id", dataMap.get("conversation_id"));
                        } else {
                            responseMap.put("conversation_id", conversationId);
                        }
                    } else {
                        responseMap.put("conversation_id", conversationId);
                    }
                    response = java.util.Optional.of(responseMap);
                } else {
                    response = difyIntegrationService.sendMessage(profile.getEndpointUrl(), profile.getApiKey(),
                            conversationId, message);
                    if (response.isPresent() && response.get() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> castedResponse = (Map<String, Object>) response.get();
                        castedResponse.put("conversation_id", conversationId);
                    }
                }
            } else if ("SiliconFlow".equalsIgnoreCase(providerType) || "SiliconCloud".equalsIgnoreCase(providerType)) {
                if ("TEXT_TO_IMAGE".equals(viewType) || "IMAGE_TO_IMAGE".equals(viewType) || "TTI".equals(viewType)) {
                    response = generateImageWithSiliconFlow(profile, metadata, message);
                } else if ("SPEECH_TO_TEXT".equals(viewType) || "STT".equals(viewType)) {
                    response = transcribeAudioWithSiliconFlow(profile, metadata, fileId);
                } else if ("TEXT_TO_SPEECH".equals(viewType) || "TTS".equals(viewType)) {
                    response = generateAudioWithSiliconFlow(profile, metadata, message);
                } else if ("TEXT_TO_VIDEO".equals(viewType) || "TTV".equals(viewType) || "VIDEO".equals(viewType)) {
                    response = generateVideoWithSiliconFlow(profile, metadata, message);
                } else {
                    response = chatWithSiliconFlow(profile, metadata, message, fileId, overrideSystemPrompt,
                            conversationId, enableThinking, thinkingBudget);
                }
            } else if ("Ollama".equalsIgnoreCase(providerType)) {
                log.info("Routing to Ollama interaction for agent {}", agentId);
                response = chatWithOllama(profile, metadata, message, fileId, overrideSystemPrompt, conversationId,
                        enableThinking, thinkingBudget);
            } else {
                MultiModalProvider provider = providerMap.get(providerType != null ? providerType.toUpperCase() : "");
                if (provider != null) {
                    Map<String, Object> context = new HashMap<>();
                    context.put("conversationId", conversationId);
                    MultiModalProvider.InteractionRequest req = new MultiModalProvider.InteractionRequest(
                            "TEXT", message, null, context);
                    MultiModalProvider.InteractionResult result = provider.process(metadata, req);
                    Map<String, Object> resp = new java.util.HashMap<>();
                    resp.put("data", result.getData());
                    resp.put("status", result.getStatus());
                    resp.put("conversation_id", conversationId);
                    response = java.util.Optional.of(resp);
                } else {
                    response = chatWithSiliconFlow(profile, metadata, message, fileId, overrideSystemPrompt,
                            conversationId, enableThinking, thinkingBudget);
                }
            }

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
                        } else {
                            // Check for wrapped data format first
                            java.util.Map<?, ?> dataMap = respMap.containsKey("data")
                                    && respMap.get("data") instanceof java.util.Map
                                            ? (java.util.Map<?, ?>) respMap.get("data")
                                            : respMap;

                            if (dataMap.containsKey("images") || "IMAGE".equals(respMap.get("dataType"))) {
                                responseContent = "[图像生成成功]";
                            } else if (dataMap.containsKey("audio_url") || "AUDIO".equals(respMap.get("dataType"))) {
                                responseContent = "[语音合成成功]";
                            } else if (dataMap.containsKey("video_url") || dataMap.containsKey("requestId")
                                    || "VIDEO".equals(respMap.get("dataType"))) {
                                responseContent = "[视频生成任务已提交]";
                            } else if (dataMap.containsKey("text")) {
                                responseContent = (String) dataMap.get("text");
                            } else if (dataMap.containsKey("message")
                                    && dataMap.get("message") instanceof java.util.Map) {
                                // Handle Ollama native format (/api/chat)
                                java.util.Map<?, ?> msgMap = (java.util.Map<?, ?>) dataMap.get("message");
                                responseContent = (String) msgMap.get("content");

                                // Extract thinking process from either message or root
                                String thinking = null;
                                if (msgMap.containsKey("thinking")) {
                                    thinking = (String) msgMap.get("thinking");
                                } else if (dataMap.containsKey("thinking")) {
                                    thinking = (String) dataMap.get("thinking");
                                }

                                // Always create a structured response for the frontend
                                java.util.Map<String, Object> enrichedResponse = new java.util.HashMap<>(
                                        (java.util.Map<String, Object>) respMap);
                                java.util.List<java.util.Map<String, Object>> choices = new java.util.ArrayList<>();
                                java.util.Map<String, Object> choice = new java.util.HashMap<>();
                                java.util.Map<String, Object> messageObj = new java.util.HashMap<>();
                                messageObj.put("role", "assistant");
                                messageObj.put("content", responseContent);
                                if (thinking != null && (enableThinking == null || enableThinking)) {
                                    messageObj.put("thinking", thinking);
                                }
                                choice.put("message", messageObj);
                                choices.add(choice);
                                enrichedResponse.put("choices", choices);
                                response = java.util.Optional.of(enrichedResponse);
                            } else if (dataMap.containsKey("response")) {
                                // Handle Ollama /api/generate format
                                responseContent = (String) dataMap.get("response");
                                String thinking = null;
                                if (dataMap.containsKey("thinking")) {
                                    thinking = (String) dataMap.get("thinking");
                                }

                                java.util.Map<String, Object> enrichedResponse = new java.util.HashMap<>(
                                        (java.util.Map<String, Object>) respMap);
                                java.util.List<java.util.Map<String, Object>> choices = new java.util.ArrayList<>();
                                java.util.Map<String, Object> choice = new java.util.HashMap<>();
                                java.util.Map<String, Object> messageObj = new java.util.HashMap<>();
                                messageObj.put("role", "assistant");
                                messageObj.put("content", responseContent);
                                if (thinking != null) {
                                    messageObj.put("thinking", thinking);
                                }
                                choice.put("message", messageObj);
                                choices.add(choice);
                                enrichedResponse.put("choices", choices);
                                response = java.util.Optional.of(enrichedResponse);
                            } else {
                                responseContent = respMap.toString();
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

                // Include system prompt override in logged message for visibility
                String loggedMessage = message;
                if (overrideSystemPrompt != null && !overrideSystemPrompt.trim().isEmpty()) {
                    loggedMessage = "[System Prompt Override: " + overrideSystemPrompt + "] " + message;
                }

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
                        loggedMessage,
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

                // Independent Conversation Log
                conversationLogService.log(com.adlin.orin.modules.conversation.entity.ConversationLog.builder()
                        .userId("admin")
                        .agentId(agentId)
                        .conversationId(conversationId)
                        .model(metadata.getModelName())
                        .query(message)
                        .response(responseContent)
                        .promptTokens(promptTokens)
                        .completionTokens(completionTokens)
                        .totalTokens(promptTokens + completionTokens)
                        .responseTime(duration)
                        .success(success)
                        .errorMessage(errorMessage)
                        .build());
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
        log.info("Interacting with agent: {} (conversationId: {})", agentId, conversationId);

        AgentMetadata metadata = metadataRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent metadata not found for ID: " + agentId));

        MultiModalProvider provider = providerMap
                .get(metadata.getProviderType() != null ? metadata.getProviderType().toUpperCase() : "DIFY");

        // Fallback for non-managed providers (e.g. SiliconFlow legacy logic inside this
        // service)
        // If provider is not in map, maybe stick to old logic?
        // Current Architecture goal: unify. But SiliconFlow logic is complex inside
        // here.
        // Let's wrap Dify first. If provider is detected as DIFY, use DifyProvider.

        if (provider != null && "DIFY".equalsIgnoreCase(metadata.getProviderType())) {
            Map<String, Object> context = new HashMap<>();
            context.put("conversationId", conversationId);

            InteractionRequest req = new InteractionRequest(
                    file != null ? "IMAGE" : "TEXT",
                    message,
                    file,
                    context);

            long startTime = System.currentTimeMillis();
            boolean success = false;
            String errorMessage = null;
            InteractionResult result = null;

            try {
                result = provider.process(metadata, req);
                success = "SUCCESS".equals(result.getStatus()) || "PROCESSING".equals(result.getStatus());
                if ("FAILED".equals(result.getStatus()))
                    errorMessage = result.getErrorMessage();

                // Wrap result for standardized frontend consumption
                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("status", result.getStatus());
                responseMap.put("data", result.getData());
                responseMap.put("dataType", result.getDataType());
                responseMap.put("jobId", result.getJobId());
                responseMap.put("viewType", metadata.getViewType());

                return java.util.Optional.of(responseMap);
            } catch (Exception e) {
                errorMessage = e.getMessage();
                throw e;
            } finally {
                // Simplified Audit Log for now
                try {
                    long duration = System.currentTimeMillis() - startTime;
                    AgentAccessProfile profile = accessProfileRepository.findById(agentId).orElse(null);
                    if (profile != null) {
                        auditLogService.logApiCall(
                                "admin",
                                profile.getApiKey(),
                                agentId,
                                metadata.getProviderType(),
                                profile.getEndpointUrl(),
                                "Interact",
                                metadata.getModelName(),
                                "127.0.0.1",
                                "ORIN-Backend",
                                message,
                                result != null ? String.valueOf(result.getData()) : null,
                                success ? 200 : 500,
                                duration,
                                0, 0, 0.0,
                                success, errorMessage, null, conversationId);
                    }
                } catch (Exception e) {
                    /* ignore */}
            }
        }

        // Legacy/Direct SiliconFlow Logic below...
        // For now, if not DIFY, we fall back to existing big block logic or create
        // SiliconFlowProvider later.
        // Keeping existing logic for safety if provider is not DIFY.
        if (!"DIFY".equalsIgnoreCase(metadata.getProviderType())) {
            // ... existing chat logic ...
            // We return existing logic implementation here by delegating to old method or
            // keeping code.
            // But replace_file_content overwrites. So I must preserve or re-implement.
            // Given the task, I should probably rely on existing logic for non-Dify.
            // Re-inserting the old logic for non-Dify:
            return chatLegacy(agentId, message, file, conversationId);
        }
        return java.util.Optional.empty();
    }

    // Helper for legacy SiliconFlow chat
    private java.util.Optional<Object> chatLegacy(String agentId, String message,
            org.springframework.web.multipart.MultipartFile file, String conversationId) {
        AgentAccessProfile profile = accessProfileRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent access profile not found for ID: " + agentId));
        AgentMetadata metadata = metadataRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent metadata not found for ID: " + agentId));

        // Handle file upload if present
        String fileNote = "";
        String fileUrl = null;
        if (file != null && !file.isEmpty()) {
            try {
                com.adlin.orin.modules.multimodal.entity.MultimodalFile uploadedFile = multimodalFileService.uploadFile(
                        file, "admin");
                fileUrl = uploadedFile.getStoragePath();
                fileNote = "[File Uploaded: " + file.getOriginalFilename() + "] ";
            } catch (Exception e) {
                return java.util.Optional.of("Error: Failed to upload file - " + e.getMessage());
            }
        }

        // ... (Call chatWithSiliconFlow) ...
        // Simplification: just call the existing private method
        return chatWithSiliconFlow(profile, metadata, fileNote + message, fileUrl, null, conversationId, null, null);
    }

    @Override
    @Transactional
    public void refreshAllAgentsMetadata() {
        log.info("Refreshing metadata for all agents...");
        List<AgentMetadata> agents = metadataRepository.findAll();
        for (AgentMetadata meta : agents) {
            String agentId = meta.getAgentId();
            if ("DIFY".equalsIgnoreCase(meta.getProviderType())) {
                accessProfileRepository.findById(agentId).ifPresent(profile -> {
                    try {
                        // 1. Sync Params
                        difyIntegrationService.fetchAppParameters(profile.getEndpointUrl(), profile.getApiKey())
                                .ifPresent(params -> {
                                    try {
                                        String paramsJson = new ObjectMapper().writeValueAsString(params);
                                        meta.setParameters(paramsJson);
                                        // Could try to infer mode here?
                                        // e.g. if params has "user_input_form" with type "workflow"? (No such thing)
                                    } catch (Exception e) {
                                        log.warn("Failed to update params for agent {}", meta.getName());
                                    }
                                });

                        // 2. Sync Meta (Name, Desc)
                        difyIntegrationService.fetchAppMeta(profile.getEndpointUrl(), profile.getApiKey())
                                .ifPresent(appMeta -> {
                                    if (appMeta.containsKey("tool_name"))
                                        meta.setName((String) appMeta.get("tool_name"));
                                    if (appMeta.containsKey("tool_description")) {
                                        String desc = (String) appMeta.get("tool_description");
                                        meta.setDescription(desc);
                                        // Infer View Type from Description Tags
                                        if (desc != null) {
                                            if (desc.contains("[ORIN:STT]"))
                                                meta.setViewType("STT");
                                            else if (desc.contains("[ORIN:TTI]"))
                                                meta.setViewType("TTI");
                                        }
                                    }
                                    if (appMeta.containsKey("tool_icon"))
                                        meta.setIcon((String) appMeta.get("tool_icon"));
                                });

                        meta.setSyncTime(LocalDateTime.now());
                        metadataRepository.save(meta);
                    } catch (Exception e) {
                        log.warn("Failed to sync agent {}: {}", meta.getName(), e.getMessage());
                    }
                });
            }
        }
        log.info("Finished refreshing agent metadata");
    }

    @Override
    public byte[] batchExportAgents(List<String> agentIds) {
        log.info("Batch exporting agents: {}", agentIds);
        List<AgentExportDTO> exportList = new java.util.ArrayList<>();
        List<AgentMetadata> metadataList;

        if (agentIds == null || agentIds.isEmpty()) {
            metadataList = metadataRepository.findAll();
        } else {
            metadataList = metadataRepository.findAllById(agentIds);
        }

        for (AgentMetadata meta : metadataList) {
            AgentAccessProfile profile = accessProfileRepository.findById(meta.getAgentId()).orElse(null);
            AgentHealthStatus status = healthStatusRepository.findById(meta.getAgentId()).orElse(null);
            exportList.add(new AgentExportDTO(meta, profile, status));
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(exportList);
        } catch (IOException e) {
            log.error("Failed to export agents", e);
            throw new RuntimeException("Export failed", e);
        }
    }

    @Override
    @Transactional
    public void batchImportAgents(MultipartFile file) {
        log.info("Batch importing agents from file: {}", file.getOriginalFilename());
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            List<AgentExportDTO> importList = mapper.readValue(file.getInputStream(),
                    new TypeReference<List<AgentExportDTO>>() {
                    });

            for (AgentExportDTO dto : importList) {
                if (dto.getMetadata() == null)
                    continue;

                String agentId = dto.getMetadata().getAgentId();
                if (agentId == null) {
                    agentId = UUID.randomUUID().toString();
                    dto.getMetadata().setAgentId(agentId);
                }

                // Metadata
                AgentMetadata meta = dto.getMetadata();
                meta.setSyncTime(LocalDateTime.now());
                metadataRepository.save(meta);

                // Profile
                if (dto.getProfile() != null) {
                    AgentAccessProfile profile = dto.getProfile();
                    profile.setAgentId(agentId); // Ensure consistency
                    profile.setUpdatedAt(LocalDateTime.now());
                    accessProfileRepository.save(profile);
                }

                // Health Status (Optional, maybe reset status)
                if (dto.getStatus() != null) {
                    AgentHealthStatus status = dto.getStatus();
                    status.setAgentId(agentId);
                    healthStatusRepository.save(status);
                }
            }
            log.info("Successfully imported {} agents", importList.size());
        } catch (IOException e) {
            log.error("Failed to import agents", e);
            throw new RuntimeException("Import failed", e);
        }
    }

    /**
     * Generate Video with SiliconFlow
     */
    private java.util.Optional<Object> generateVideoWithSiliconFlow(AgentAccessProfile profile,
            AgentMetadata metadata, String message) {
        try {
            String prompt = message;
            Map<String, Object> params = new HashMap<>();

            try {
                if (message != null && message.trim().startsWith("{")) {
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> payload = mapper.readValue(message, new TypeReference<Map<String, Object>>() {
                    });
                    if (payload.containsKey("prompt"))
                        prompt = (String) payload.get("prompt");
                    params.putAll(payload);
                }
            } catch (Exception e) {
                log.debug("Message is not JSON, using as raw input for video");
            }

            log.info("Submitting video generation task for model: {}", metadata.getModelName());

            java.util.Optional<Object> sfRes = siliconFlowIntegrationService.generateVideo(
                    profile.getEndpointUrl(), profile.getApiKey(), metadata.getModelName(), prompt, params);

            if (sfRes.isPresent()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) sfRes.get();
                log.info("SiliconFlow Video Submission Response: {}", body);

                // Defensive ID extraction
                String requestId = null;
                if (body.containsKey("requestId"))
                    requestId = String.valueOf(body.get("requestId"));
                else if (body.containsKey("request_id"))
                    requestId = String.valueOf(body.get("request_id"));
                else if (body.containsKey("data") && body.get("data") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    if (data.containsKey("requestId"))
                        requestId = String.valueOf(data.get("requestId"));
                    else if (data.containsKey("request_id"))
                        requestId = String.valueOf(data.get("request_id"));
                    else if (data.containsKey("id"))
                        requestId = String.valueOf(data.get("id"));
                } else if (body.containsKey("id"))
                    requestId = String.valueOf(body.get("id"));

                if (requestId != null && !requestId.trim().isEmpty()) {
                    requestId = requestId.trim();
                    log.info("Extracted SiliconFlow task ID: {}", requestId);
                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "PROCESSING");
                    result.put("jobId", requestId);
                    result.put("dataType", "VIDEO");
                    return java.util.Optional.of(result);
                }
                return sfRes;
            }
            return java.util.Optional.empty();
        } catch (Exception e) {
            log.error("Failed to submit video generation", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "FAILED");
            errorResult.put("errorMessage", e.getMessage());
            return java.util.Optional.of(errorResult);
        }
    }

    @Override
    public Object getJobStatus(String agentId, String jobId) {
        AgentAccessProfile profile = getAgentAccessProfile(agentId);
        AgentMetadata metadata = getAgentMetadata(agentId);

        String provider = metadata.getProviderType() != null ? metadata.getProviderType().trim().toUpperCase() : "";
        String viewType = metadata.getViewType() != null ? metadata.getViewType().trim().toUpperCase() : "";

        log.info("Checking job status for agent: {}, jobId: {}, provider: {}, viewType: {}",
                agentId, jobId, provider, viewType);

        if (jobId == null || "null".equals(jobId) || jobId.isEmpty()) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "FAILED");
            errorResult.put("errorMessage", "Invalid Job ID (null). Submission might have failed.");
            return errorResult;
        }

        // 识别视频生成任务：Provider 是 SiliconFlow，或者 ViewType 包含 VIDEO / TTV / WAN
        if ("SILICONFLOW".equals(provider) ||
                "SILICONCLOUD".equals(provider) ||
                viewType.contains("VIDEO") ||
                viewType.contains("TTV") ||
                (metadata.getModelName() != null && metadata.getModelName().contains("Wan"))) {

            var sfRes = siliconFlowIntegrationService.getVideoJobStatus(profile.getEndpointUrl(), profile.getApiKey(),
                    jobId);
            if (sfRes.isPresent()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) sfRes.get();
                Map<String, Object> result = new HashMap<>();

                // SiliconFlow 状态响应格式通常包含 status 字段: 'Succeed', 'InQueue', 'InProgress',
                // 'Failed'
                String sfStatus = (String) body.get("status");

                if ("Succeed".equalsIgnoreCase(sfStatus)) {
                    result.put("status", "SUCCESS");
                    result.put("dataType", "VIDEO");

                    // 提取视频结果 (SiliconFlow 结构: results.videos[0].url)
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> resultsContent = (java.util.Map<String, Object>) body.get("results");
                    if (resultsContent != null) {
                        @SuppressWarnings("unchecked")
                        java.util.List<java.util.Map<String, Object>> videos = (java.util.List<java.util.Map<String, Object>>) resultsContent
                                .get("videos");
                        if (videos != null && !videos.isEmpty()) {
                            java.util.Map<String, Object> videoResult = videos.get(0);
                            result.put("data", videoResult); // 包含 url, seed 等
                        }
                    }
                } else if ("Failed".equalsIgnoreCase(sfStatus) || "FAILED".equalsIgnoreCase(sfStatus)) {
                    result.put("status", "FAILED");
                    result.put("errorMessage",
                            body.get("reason") != null ? body.get("reason") : "SiliconFlow job failed");
                } else if ("TaskNotFound".equalsIgnoreCase(sfStatus)) {
                    // 特殊处理：任务尚未在 SiliconFlow 系统中同步，继续轮询
                    result.put("status", "PROCESSING");
                    result.put("message", "任务同步中...");
                } else if ("InQueue".equalsIgnoreCase(sfStatus) || "Queuing".equalsIgnoreCase(sfStatus)) {
                    result.put("status", "PROCESSING");
                    result.put("message", "任务排队中...");
                } else if ("InProgress".equalsIgnoreCase(sfStatus) || "Processing".equalsIgnoreCase(sfStatus)) {
                    result.put("status", "PROCESSING");
                    result.put("message", "视频生成中...");
                } else {
                    result.put("status", "PROCESSING");
                }
                log.info("Returning status for job {}: {}", jobId, result.get("status"));
                return result;
            } else {
                // 如果尝试请求状态但失败了并返回空（通常不应发生，因为已在 Service 中拦截了异常）
                log.warn("SiliconFlow status check returned empty for job: {}", jobId);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("status", "FAILED");
                errorResult.put("errorMessage", "Unexpected empty response from status check.");
                return errorResult;
            }
        }

        Map<String, Object> fallback = new HashMap<>();
        fallback.put("status", "FAILED");
        fallback.put("errorMessage",
                String.format("Job status tracking not supported for provider [%s] and type [%s]", provider, viewType));
        return fallback;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class AgentExportDTO {
        private AgentMetadata metadata;
        private AgentAccessProfile profile;
        private AgentHealthStatus status;
    }
}
