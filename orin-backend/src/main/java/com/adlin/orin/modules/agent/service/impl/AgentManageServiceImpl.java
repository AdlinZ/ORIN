package com.adlin.orin.modules.agent.service.impl;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentJobEntity;
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
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.knowledge.service.meta.MetaKnowledgeService;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.entity.AgentStatus;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import com.adlin.orin.modules.model.service.MinimaxIntegrationService;
import com.adlin.orin.modules.model.service.OllamaIntegrationService;
import com.adlin.orin.modules.multimodal.service.MultimodalFileService;
import com.adlin.orin.modules.conversation.entity.ConversationLog;
import com.adlin.orin.modules.conversation.service.ConversationLogService;
import org.slf4j.MDC;
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
    private final com.adlin.orin.modules.agent.repository.AgentJobRepository agentJobRepository;
    private final AuditLogService auditLogService;
    private final AuditHelper auditHelper;
    private final com.adlin.orin.modules.conversation.service.ConversationLogService conversationLogService;
    private final MultimodalFileService multimodalFileService;
    private final MetaKnowledgeService metaKnowledgeService;
    private final com.adlin.orin.modules.model.repository.ModelMetadataRepository modelMetadataRepository;
    private final com.adlin.orin.modules.model.service.ModelConfigService modelConfigService;
    private final Map<String, MultiModalProvider> providerMap = new HashMap<>();

    // Provider-specific AgentManageService instances
    private final com.adlin.orin.modules.agent.service.SiliconFlowAgentManageService siliconFlowAgentManageService;
    private final com.adlin.orin.modules.agent.service.ZhipuAgentManageService zhipuAgentManageService;
    private final com.adlin.orin.modules.agent.service.KimiAgentManageService kimiAgentManageService;
    private final com.adlin.orin.modules.agent.service.DeepSeekAgentManageService deepSeekAgentManageService;
    private final com.adlin.orin.modules.agent.service.MinimaxAgentManageService minimaxAgentManageService;

    // Explicit constructor injection to ensure all dependencies are handled
    // correctly
    public AgentManageServiceImpl(
            DifyIntegrationService difyIntegrationService,
            SiliconFlowIntegrationService siliconFlowIntegrationService,
            AgentAccessProfileRepository accessProfileRepository,
            AgentMetadataRepository metadataRepository,
            AgentHealthStatusRepository healthStatusRepository,
            AuditLogService auditLogService,
            AuditHelper auditHelper,
            com.adlin.orin.modules.conversation.service.ConversationLogService conversationLogService,
            MultimodalFileService multimodalFileService,
            MetaKnowledgeService metaKnowledgeService,
            com.adlin.orin.modules.model.repository.ModelMetadataRepository modelMetadataRepository,
            MinimaxIntegrationService minimaxIntegrationService,
            OllamaIntegrationService ollamaIntegrationService,
            com.adlin.orin.modules.model.service.ModelConfigService modelConfigService,
            List<MultiModalProvider> providers,
            com.adlin.orin.modules.agent.service.SiliconFlowAgentManageService siliconFlowAgentManageService,
            com.adlin.orin.modules.agent.service.ZhipuAgentManageService zhipuAgentManageService,
            com.adlin.orin.modules.agent.service.KimiAgentManageService kimiAgentManageService,
            com.adlin.orin.modules.agent.service.DeepSeekAgentManageService deepSeekAgentManageService,
            com.adlin.orin.modules.agent.service.MinimaxAgentManageService minimaxAgentManageService,
            com.adlin.orin.modules.agent.repository.AgentJobRepository agentJobRepository) {
        this.difyIntegrationService = difyIntegrationService;
        this.siliconFlowIntegrationService = siliconFlowIntegrationService;
        this.minimaxIntegrationService = minimaxIntegrationService;
        this.ollamaIntegrationService = ollamaIntegrationService;
        this.accessProfileRepository = accessProfileRepository;
        this.metadataRepository = metadataRepository;
        this.healthStatusRepository = healthStatusRepository;
        this.agentJobRepository = agentJobRepository;
        this.auditLogService = auditLogService;
        this.auditHelper = auditHelper;
        this.conversationLogService = conversationLogService;
        this.multimodalFileService = multimodalFileService;
        this.metaKnowledgeService = metaKnowledgeService;
        this.modelMetadataRepository = modelMetadataRepository;
        this.modelConfigService = modelConfigService;
        this.siliconFlowAgentManageService = siliconFlowAgentManageService;
        this.zhipuAgentManageService = zhipuAgentManageService;
        this.kimiAgentManageService = kimiAgentManageService;
        this.deepSeekAgentManageService = deepSeekAgentManageService;
        this.minimaxAgentManageService = minimaxAgentManageService;

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
            metadata.setToolCallingOverride(request.getToolCallingOverride());

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
            if (config.getToolCallingOverride() != null) {
                existing.setToolCallingOverride(config.getToolCallingOverride());
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
            Boolean enableThinking, Integer thinkingBudget, Integer maxTokensOverride) {

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
        int maxTokens = resolveMaxTokens(metadata.getMaxTokens(), 2000, maxTokensOverride);

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
                if (messageMap.containsKey("imageSize")) {
                    params.put("image_size", messageMap.get("imageSize"));
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
                if (messageMap.containsKey("negativePrompt")) {
                    params.put("negative_prompt", messageMap.get("negativePrompt"));
                }
                if (messageMap.containsKey("seed")) {
                    params.put("seed", messageMap.get("seed"));
                }
                if (messageMap.containsKey("guidance_scale")) {
                    params.put("guidance_scale", messageMap.get("guidance_scale"));
                }
                if (messageMap.containsKey("guidanceScale")) {
                    params.put("guidance_scale", messageMap.get("guidanceScale"));
                }
                if (messageMap.containsKey("num_inference_steps")) {
                    params.put("num_inference_steps", messageMap.get("num_inference_steps"));
                }
                if (messageMap.containsKey("inferenceSteps")) {
                    params.put("num_inference_steps", messageMap.get("inferenceSteps"));
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
                Map<String, Object> dataObj = (Map<String, Object>) res.get();

                // 用于存储最终返回的数据
                Map<String, Object> data = new HashMap<>();
                String savedFileId = null;

                // 尝试处理 images 数组格式（旧格式）
                if (dataObj.containsKey("images")) {
                    try {
                        java.util.List<Map<String, Object>> images = (java.util.List<Map<String, Object>>) dataObj
                                .get("images");
                        log.info("Processing images array, count: {}", images != null ? images.size() : 0);
                        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
                        for (Map<String, Object> img : images) {
                            log.info("Image object keys: {}", img.keySet());

                            // 优先检查 base64 格式
                            String b64Json = (String) img.get("b64_json");
                            if (b64Json != null && !b64Json.isEmpty()) {
                                try {
                                    log.info("Processing base64 image data");
                                    byte[] imgData = java.util.Base64.getDecoder().decode(b64Json);
                                    if (imgData != null && imgData.length > 0) {
                                        String filename = "genimg_" + UUID.randomUUID().toString() + ".png";
                                        com.adlin.orin.modules.multimodal.entity.MultimodalFile savedFile = multimodalFileService
                                                .uploadFile(imgData, filename, "image/png",
                                                        "agent:" + metadata.getAgentId());
                                        String downloadUrl = "/api/v1/multimodal/files/" + savedFile.getId() + "/download";
                                        img.put("url", downloadUrl);
                                        img.put("image_url", downloadUrl);
                                        img.put("file_id", savedFile.getId());
                                        savedFileId = savedFile.getId();
                                        data.put("image_url", downloadUrl);
                                        data.put("file_id", savedFileId);
                                        log.info("Successfully saved base64 image, size: {} bytes", imgData.length);
                                    }
                                } catch (Exception ex) {
                                    log.error("Failed to process base64 image: {}", ex.getMessage());
                                }
                                continue;
                            }

                            // 如果没有 base64，尝试 URL 格式
                            String url = (String) img.get("url");
                            // 也尝试从 image_url 字段获取
                            if (url == null) {
                                url = (String) img.get("image_url");
                            }
                            log.info("Extracted image URL from images array: {}", url);
                            if (url != null && url.startsWith("http")) {
                                try {
                                    // 解码 URL
                                    String decodedUrl = java.net.URLDecoder.decode(url, "UTF-8");
                                    // 设置 HTTP 头
                                    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                                    headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
                                    org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
                                    org.springframework.http.ResponseEntity<byte[]> response = restTemplate.exchange(
                                            decodedUrl,
                                            org.springframework.http.HttpMethod.GET,
                                            entity,
                                            byte[].class);
                                    int statusCode = response.getStatusCode().value();
                                    log.info("Download response status from images array: {}", statusCode);
                                    if (statusCode == 200) {
                                        byte[] imgData = response.getBody();
                                        if (imgData != null && imgData.length > 0) {
                                            String filename = "genimg_" + UUID.randomUUID().toString() + ".png";
                                            com.adlin.orin.modules.multimodal.entity.MultimodalFile savedFile = multimodalFileService
                                                    .uploadFile(imgData, filename, "image/png",
                                                            "agent:" + metadata.getAgentId());
                                            String downloadUrl = "/api/v1/multimodal/files/" + savedFile.getId() + "/download";
                                            img.put("url", downloadUrl);
                                            img.put("image_url", downloadUrl);
                                            img.put("file_id", savedFile.getId());
                                            if (img.containsKey("url")) {
                                                img.put("original_url", url);
                                            }
                                            savedFileId = savedFile.getId();
                                            data.put("image_url", downloadUrl);
                                            data.put("file_id", savedFileId);
                                            log.info("Successfully downloaded and saved image from images array, size: {} bytes", imgData.length);
                                        }
                                    } else {
                                        log.warn("Failed to download image, HTTP status: {}, URL may be expired", statusCode);
                                        data.put("image_url", url);
                                        data.put("original_url", url);
                                    }
                                } catch (Exception ex) {
                                    // 下载失败时，保存原始 URL 以便前端直接显示
                                    log.error("Failed to download image from images array: {}", url, ex);
                                    data.put("image_url", url);
                                    data.put("original_url", url);
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("Could not process images field", ex);
                    }
                }

                // 如果没有从 images 数组中获取到 file_id，尝试处理新格式（data.image_url）
                // API 返回格式: { data: { image_url: "..." }, dataType: "IMAGE", status: "SUCCESS" }
                if (savedFileId == null) {
                    log.info("Processing image from data.image_url format, dataObj keys: {}", dataObj.keySet());
                    try {
                        // 先检查 data 字段
                        Object dataField = dataObj.get("data");
                        log.info("data field value: {}, type: {}", dataField, dataField != null ? dataField.getClass().getName() : "null");
                        String imageUrl = null;
                        if (dataField instanceof java.util.List) {
                            // data 是 ArrayList 格式: [{url: "..."}]
                            java.util.List<?> dataList = (java.util.List<?>) dataField;
                            if (!dataList.isEmpty()) {
                                Object firstItem = dataList.get(0);
                                if (firstItem instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> dataMap = (Map<String, Object>) firstItem;
                                    // 优先检查 base64
                                    String b64Json = (String) dataMap.get("b64_json");
                                    if (b64Json != null && !b64Json.isEmpty()) {
                                        try {
                                            log.info("Processing base64 image from data list");
                                            byte[] imgData = java.util.Base64.getDecoder().decode(b64Json);
                                            if (imgData != null && imgData.length > 0) {
                                                String filename = "genimg_" + UUID.randomUUID().toString() + ".png";
                                                com.adlin.orin.modules.multimodal.entity.MultimodalFile savedFile = multimodalFileService
                                                        .uploadFile(imgData, filename, "image/png",
                                                                "agent:" + metadata.getAgentId());
                                                savedFileId = savedFile.getId();
                                                data.put("file_id", savedFileId);
                                                data.put("image_url", "/api/v1/multimodal/files/" + savedFileId + "/download");
                                                log.info("Successfully saved base64 image from data list, size: {} bytes", imgData.length);
                                            }
                                        } catch (Exception ex) {
                                            log.error("Failed to process base64 image from data list: {}", ex.getMessage());
                                        }
                                    } else {
                                        imageUrl = (String) dataMap.get("url");
                                        // 也尝试 image_url
                                        if (imageUrl == null) {
                                            imageUrl = (String) dataMap.get("image_url");
                                        }
                                        log.info("Found image_url in data list: {}", imageUrl);
                                    }
                                }
                            }
                        } else if (dataField instanceof Map) {
                            // data 是 Map 格式: {image_url: "..."} 或 {b64_json: "..."}
                            @SuppressWarnings("unchecked")
                            Map<String, Object> dataMap = (Map<String, Object>) dataField;
                            // 优先检查 base64
                            String b64Json = (String) dataMap.get("b64_json");
                            if (b64Json != null && !b64Json.isEmpty()) {
                                try {
                                    log.info("Processing base64 image from data field");
                                    byte[] imgData = java.util.Base64.getDecoder().decode(b64Json);
                                    if (imgData != null && imgData.length > 0) {
                                        String filename = "genimg_" + UUID.randomUUID().toString() + ".png";
                                        com.adlin.orin.modules.multimodal.entity.MultimodalFile savedFile = multimodalFileService
                                                .uploadFile(imgData, filename, "image/png",
                                                        "agent:" + metadata.getAgentId());
                                        savedFileId = savedFile.getId();
                                        data.put("file_id", savedFileId);
                                        data.put("image_url", "/api/v1/multimodal/files/" + savedFileId + "/download");
                                        log.info("Successfully saved base64 image from data field, size: {} bytes", imgData.length);
                                    }
                                } catch (Exception ex) {
                                    log.error("Failed to process base64 image from data field: {}", ex.getMessage());
                                }
                            } else {
                                imageUrl = (String) dataMap.get("image_url");
                                log.info("Found image_url in data field: {}", imageUrl);
                            }
                        }
                        // 如果 data 字段没有，再尝试直接从 dataObj 获取
                        if (imageUrl == null) {
                            imageUrl = (String) dataObj.get("image_url");
                        }
                        log.info("Extracted image_url: {}", imageUrl);
                        if (imageUrl != null && imageUrl.startsWith("http")) {
                            try {
                                // 解码 URL（处理 %2F -> / 等编码问题）
                                String decodedUrl = java.net.URLDecoder.decode(imageUrl, "UTF-8");
                                log.info("Attempting to download image from: {}", decodedUrl);

                                org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
                                // 设置 HTTP 头
                                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                                headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
                                org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
                                org.springframework.http.ResponseEntity<byte[]> response = restTemplate.exchange(
                                        decodedUrl,
                                        org.springframework.http.HttpMethod.GET,
                                        entity,
                                        byte[].class);
                                int statusCode = response.getStatusCode().value();
                                log.info("Download response status: {}", statusCode);
                                if (statusCode == 200) {
                                    byte[] imgData = response.getBody();
                                    if (imgData != null && imgData.length > 0) {
                                        String filename = "genimg_" + UUID.randomUUID().toString() + ".png";
                                        com.adlin.orin.modules.multimodal.entity.MultimodalFile savedFile = multimodalFileService
                                                .uploadFile(imgData, filename, "image/png",
                                                        "agent:" + metadata.getAgentId());
                                        savedFileId = savedFile.getId();
                                        data.put("file_id", savedFileId);
                                        data.put("image_url", "/api/v1/multimodal/files/" + savedFileId + "/download");
                                        data.put("original_url", imageUrl);
                                        log.info("Downloaded and saved image from image_url: {}, size: {} bytes", imageUrl, imgData.length);
                                    }
                                } else {
                                    log.warn("Failed to download image, HTTP status: {}, URL may be expired", statusCode);
                                    data.put("image_url", imageUrl);
                                    data.put("original_url", imageUrl);
                                }
                            } catch (Exception ex) {
                                // 下载失败时，保存原始 URL 以便前端直接显示
                                log.error("Failed to download image from image_url: {}", imageUrl, ex);
                                data.put("image_url", imageUrl);
                                data.put("original_url", imageUrl);
                            }
                        } else {
                            log.warn("No valid image_url found in response");
                        }
                    } catch (Exception e) {
                        log.warn("Failed to process image_url format: {}", e.getMessage());
                    }
                }

                // 如果成功保存了图片，构建响应
                if (savedFileId != null) {
                    if (!data.containsKey("file_id")) {
                        data.put("file_id", savedFileId);
                    }
                    if (!data.containsKey("image_url")) {
                        data.put("image_url", "/api/v1/multimodal/files/" + savedFileId + "/download");
                    }
                } else {
                    // 如果没有保存成功，也要把原始 URL 返回
                    try {
                        // 尝试从 images 数组获取原始 URL
                        if (dataObj.containsKey("images")) {
                            java.util.List<Map<String, Object>> images = (java.util.List<Map<String, Object>>) dataObj.get("images");
                            if (images != null && !images.isEmpty()) {
                                Object url = images.get(0).get("url");
                                if (url != null) {
                                    data.put("image_url", url);
                                }
                            }
                        }
                        // 尝试从 data.image_url 获取
                        if (!data.containsKey("image_url") && dataObj.containsKey("data")) {
                            Object dataObjInner = dataObj.get("data");
                            if (dataObjInner instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> dataMap = (Map<String, Object>) dataObjInner;
                                if (dataMap.containsKey("image_url")) {
                                    data.put("image_url", dataMap.get("image_url"));
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to extract image URL", e);
                    }
                }

                Map<String, Object> result = new HashMap<>();
                result.put("status", "SUCCESS");
                result.put("data", data);
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

    /**
     * Generate video with SiliconFlow
     */
    private java.util.Optional<Object> generateVideoWithSiliconFlow(AgentAccessProfile profile,
            AgentMetadata metadata, String message) {
        try {
            // 解析消息，提取参数
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            String prompt = message;

            // 尝试解析JSON消息
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> messageMap = mapper.readValue(message, java.util.Map.class);

                if (messageMap.containsKey("prompt")) {
                    prompt = (String) messageMap.get("prompt");
                } else if (messageMap.containsKey("query")) {
                    prompt = (String) messageMap.get("query");
                }

                // 提取其他参数
                if (messageMap.containsKey("videoSize") || messageMap.containsKey("video_size")) {
                    params.put("videoSize", messageMap.get("videoSize") != null ?
                            messageMap.get("videoSize") : messageMap.get("video_size"));
                }
                if (messageMap.containsKey("negative_prompt")) {
                    params.put("negative_prompt", messageMap.get("negative_prompt"));
                }
                if (messageMap.containsKey("seed")) {
                    params.put("seed", messageMap.get("seed"));
                }
                // 支持参考图片（I2V）
                if (messageMap.containsKey("reference_image") || messageMap.containsKey("image")) {
                    params.put("reference_image", messageMap.get("reference_image") != null ?
                            messageMap.get("reference_image") : messageMap.get("image"));
                }
            } catch (Exception e) {
                log.debug("Message is not JSON, using as plain prompt");
            }

            // 1. 提交视频生成任务
            java.util.Optional<Object> submitResult = siliconFlowIntegrationService.generateVideo(
                    profile.getEndpointUrl(),
                    profile.getApiKey(),
                    metadata.getModelName(),
                    prompt,
                    params);

            if (!submitResult.isPresent()) {
                log.error("Video generation submit failed");
                return java.util.Optional.empty();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> submitData = (Map<String, Object>) submitResult.get();
            // SiliconFlow API 返回的是 requestId（驼峰），也可能是 request_id
            log.info("Video submit response: {}", submitData);
            String requestId = null;

            // Try different key variations
            Object requestIdObj = submitData.get("request_id");
            if (requestIdObj == null) {
                requestIdObj = submitData.get("requestId");
            }
            if (requestIdObj == null) {
                requestIdObj = submitData.get("id");
            }

            if (requestIdObj != null) {
                requestId = requestIdObj.toString();
            }

            if (requestId == null || requestId.isEmpty()) {
                log.error("No request_id in video submit response: {}", submitData);
                return java.util.Optional.empty();
            }

            log.info("Video generation task submitted: {}", requestId);

            // 2. 轮询任务状态，直到完成
            int maxRetries = 180; // 最多等待180次 (约15分钟)
            int retryInterval = 30000; // 30秒间隔
            String videoUrl = null;

            // 初始等待，让任务有时间启动
            try {
                Thread.sleep(10000); // 初始等待10秒
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            for (int i = 0; i < maxRetries; i++) {
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }

                java.util.Optional<Object> statusResult = siliconFlowIntegrationService.getVideoJobStatus(
                        profile.getEndpointUrl(),
                        profile.getApiKey(),
                        requestId);

                if (statusResult.isPresent()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> statusData = (Map<String, Object>) statusResult.get();
                    String status = (String) statusData.get("status");

                    log.info("Video job status: {}, response: {}", status, statusData);

                    if ("SUCCEEDED".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status) || "Succeed".equalsIgnoreCase(status)) {
                        // 获取视频URL - SiliconFlow 格式: results.videos[0].url
                        Object resultsObj = statusData.get("results");
                        if (resultsObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> resultsMap = (Map<String, Object>) resultsObj;
                            Object videosObj = resultsMap.get("videos");
                            if (videosObj instanceof java.util.List && !((java.util.List<?>) videosObj).isEmpty()) {
                                Object firstVideo = ((java.util.List<?>) videosObj).get(0);
                                if (firstVideo instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> videoMap = (Map<String, Object>) firstVideo;
                                    videoUrl = (String) videoMap.get("url");
                                }
                            }
                        }
                        // 兼容旧格式 video.url
                        if (videoUrl == null) {
                            Object videoObj = statusData.get("video");
                            if (videoObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> videoMap = (Map<String, Object>) videoObj;
                                videoUrl = (String) videoMap.get("url");
                            } else if (videoObj instanceof String) {
                                videoUrl = (String) videoObj;
                            }
                        }
                        log.info("Video generated successfully, url: {}", videoUrl);
                        break;
                    } else if ("FAILED".equals(status) || "FAILED".equalsIgnoreCase(status)) {
                        String errorMsg = (String) statusData.get("message");
                        log.error("Video generation failed: {}", errorMsg);
                        Map<String, Object> errorResult = new java.util.HashMap<>();
                        errorResult.put("status", "FAILED");
                        errorResult.put("errorMessage", errorMsg != null ? errorMsg : "Video generation failed");
                        return java.util.Optional.of(errorResult);
                    }
                    // PENDING, PROCESSING, RUNNING 等继续等待
                }
            }

            if (videoUrl == null) {
                log.error("Video generation timeout, no video URL obtained");
                Map<String, Object> errorResult = new java.util.HashMap<>();
                errorResult.put("status", "FAILED");
                errorResult.put("errorMessage", "Video generation timeout");
                return java.util.Optional.of(errorResult);
            }

            // 3. 下载视频并保存到本地
            try {
                org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
                byte[] videoData = restTemplate.getForObject(videoUrl, byte[].class);

                if (videoData != null) {
                    String filename = "genvideo_" + UUID.randomUUID().toString() + ".mp4";
                    com.adlin.orin.modules.multimodal.entity.MultimodalFile savedFile = multimodalFileService
                            .uploadFile(videoData, filename, "video/mp4", "agent:" + metadata.getAgentId());

                    // 返回响应，包含 file_id
                    Map<String, Object> data = new java.util.HashMap<>();
                    data.put("video_url", "/api/v1/multimodal/files/" + savedFile.getId() + "/download");
                    data.put("file_id", savedFile.getId());
                    data.put("text", prompt);
                    // 保留原始URL供参考
                    data.put("original_url", videoUrl);

                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("status", "SUCCESS");
                    result.put("data", data);
                    result.put("dataType", "VIDEO");

                    return java.util.Optional.of(result);
                }
            } catch (Exception ex) {
                log.error("Failed to download or save video: {}", ex.getMessage(), ex);
                // 如果下载失败，返回原始URL
                Map<String, Object> data = new java.util.HashMap<>();
                data.put("video_url", videoUrl);
                data.put("text", prompt);

                Map<String, Object> result = new java.util.HashMap<>();
                result.put("status", "SUCCESS");
                result.put("data", data);
                result.put("dataType", "VIDEO");

                return java.util.Optional.of(result);
            }

            return java.util.Optional.empty();
        } catch (Exception e) {
            log.error("Failed to generate video", e);
            Map<String, Object> errorResult = new java.util.HashMap<>();
            errorResult.put("status", "FAILED");
            errorResult.put("errorMessage", e.getMessage());
            return java.util.Optional.of(errorResult);
        }
    }

    private java.util.Optional<Object> chatWithOllama(AgentAccessProfile profile, AgentMetadata metadata,
            String message, String fileId, String overrideSystemPrompt, String conversationId,
            Boolean enableThinking, Integer thinkingBudget, Integer maxTokensOverride) {

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
        int maxTokens = resolveMaxTokens(metadata.getMaxTokens(), 2000, maxTokensOverride);

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
    public java.util.Optional<Object> chat(String agentId, String message,
            org.springframework.web.multipart.MultipartFile file) {
        // Convert MultipartFile to fileId (for now, treat as null)
        return chat(agentId, message, (String) null);
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
        return chat(agentId, message, fileId, overrideSystemPrompt, conversationId, enableThinking, thinkingBudget, null);
    }

    @Override
    public java.util.Optional<Object> chat(String agentId, String message, String fileId, String overrideSystemPrompt,
            String conversationId, Boolean enableThinking, Integer thinkingBudget, Integer maxTokensOverride) {
        // Use provided conversation ID or generate a new one
        String effectiveConversationId = (conversationId != null && !conversationId.isEmpty())
                ? conversationId
                : UUID.randomUUID().toString();

        return chatWithConversation(agentId, message, fileId, effectiveConversationId, overrideSystemPrompt,
                enableThinking, thinkingBudget, maxTokensOverride);
    }

    /**
     * Chat with agent using a specific conversation ID
     */
    public java.util.Optional<Object> chatWithConversation(String agentId, String message, String fileId,
            String conversationId) {
        return chatWithConversation(agentId, message, fileId, conversationId, null, null, null, null);
    }

    public java.util.Optional<Object> chatWithConversation(String agentId, String message, String fileId,
            String conversationId, String overrideSystemPrompt) {
        return chatWithConversation(agentId, message, fileId, conversationId, overrideSystemPrompt, null, null, null);
    }

    public java.util.Optional<Object> chatWithConversation(String agentId, String message, String fileId,
            String conversationId, String overrideSystemPrompt, Boolean enableThinking, Integer thinkingBudget) {
        return chatWithConversation(agentId, message, fileId, conversationId, overrideSystemPrompt, enableThinking, thinkingBudget, null);
    }

    public java.util.Optional<Object> chatWithConversation(String agentId, String message, String fileId,
            String conversationId, String overrideSystemPrompt, Boolean enableThinking, Integer thinkingBudget,
            Integer maxTokensOverride) {
        log.info("Chatting with agent: {} (conversationId: {}, fileId: {}, hasOverride: {}, thinking: {})",
                agentId, conversationId, fileId, overrideSystemPrompt != null, enableThinking);

        // 1. Get agent profile and metadata
        AgentAccessProfile profile = accessProfileRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent access profile not found for ID: " + agentId));

        AgentMetadata metadata = metadataRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent metadata not found for ID: " + agentId));

        // 2. Dynamic System Prompt Assembly (Includes Memory and Skills)
        String dynamicSystemPrompt;
        if (overrideSystemPrompt != null && !overrideSystemPrompt.trim().isEmpty()) {
            // Using Override Prompt for Sandbox testing
            dynamicSystemPrompt = overrideSystemPrompt;
        } else {
            // Normal flow: Assemble from DB (PromptTemplate, Memory, Skills)
            dynamicSystemPrompt = metaKnowledgeService.assembleSystemPrompt(agentId);

            // Fallback to static if dynamic is empty
            if (dynamicSystemPrompt.trim().isEmpty()) {
                if (metadata.getSystemPrompt() != null && !metadata.getSystemPrompt().isEmpty()) {
                    dynamicSystemPrompt = metadata.getSystemPrompt();
                }
            }
        }

        // 3. Route based on providerType - wrapped in try-catch to ensure logging
        String providerType = metadata.getProviderType();
        String viewType = metadata.getViewType();
        log.info("Agent {} providerType: {}, viewType: {}", agentId, providerType, viewType);

        // Record start time for response time calculation
        long startTime = System.currentTimeMillis();

        java.util.Optional<Object> response;
        String errorMessage = null;
        String actualEndpoint = "/chat/completions"; // default endpoint

        try {
            if ("DIFY".equalsIgnoreCase(providerType)) {
                log.info("Routing to Dify interaction for agent {} (viewType: {})", agentId, viewType);
                actualEndpoint = profile.getEndpointUrl() + "/v1/chat-messages";
                MultiModalProvider provider = providerMap.get("DIFY");
                if (provider != null) {
                    Map<String, Object> context = new HashMap<>();
                    context.put("conversationId", conversationId);
                    InteractionRequest req = new InteractionRequest(
                            fileId != null ? "IMAGE" : "TEXT",
                            message,
                            null,
                            context);
                    InteractionResult result = provider.process(metadata, req);
                    Map<String, Object> responseMap = new HashMap<>();
                    responseMap.put("status", result.getStatus());
                    responseMap.put("data", result.getData());
                    responseMap.put("dataType", result.getDataType());
                    responseMap.put("viewType", metadata.getViewType());

                    if (result.getData() instanceof Map) {
                        Map<?, ?> dataMap = (Map<?, ?>) result.getData();
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
                actualEndpoint = profile.getEndpointUrl() + "/chat/completions";
                if ("TEXT_TO_IMAGE".equals(viewType) || "IMAGE_TO_IMAGE".equals(viewType) || "TTI".equals(viewType)) {
                    response = generateImageWithSiliconFlow(profile, metadata, message);
                } else if ("SPEECH_TO_TEXT".equals(viewType) || "STT".equals(viewType)) {
                    response = transcribeAudioWithSiliconFlow(profile, metadata, fileId);
                } else if ("TEXT_TO_SPEECH".equals(viewType) || "TTS".equals(viewType)) {
                    response = generateAudioWithSiliconFlow(profile, metadata, message);
                } else if ("TEXT_TO_VIDEO".equals(viewType) || "VIDEO".equals(viewType) || "TTV".equals(viewType)) {
                    response = generateVideoWithSiliconFlow(profile, metadata, message);
                } else {
                    // Other types fall back to chat
                    // Use dynamic prompt aware path so retrieval context can be injected.
                    response = chatWithSiliconFlow(
                            profile, metadata, message, fileId, dynamicSystemPrompt, conversationId,
                            enableThinking, thinkingBudget, maxTokensOverride);
                }
            } else if ("Ollama".equalsIgnoreCase(providerType)) {
                log.info("Routing to Ollama interaction for agent {}", agentId);
                actualEndpoint = profile.getEndpointUrl() + "/api/chat";
                response = chatWithOllama(profile, metadata, message, fileId, dynamicSystemPrompt, conversationId,
                        enableThinking, thinkingBudget, maxTokensOverride);
            } else if ("KIMI".equalsIgnoreCase(providerType) || "Moonshot".equalsIgnoreCase(providerType)) {
                log.info("Routing to Kimi (Moonshot) service for agent {}", agentId);
                actualEndpoint = profile.getEndpointUrl() + "/v1/chat/completions";
                response = kimiAgentManageService.chat(agentId, message, fileId, dynamicSystemPrompt, maxTokensOverride);
            } else if ("Zhipu".equalsIgnoreCase(providerType) || "GLM".equalsIgnoreCase(providerType)) {
                log.info("Routing to Zhipu (GLM) service for agent {}", agentId);
                actualEndpoint = profile.getEndpointUrl() + "/api/paas/v4/chat/completions";
                response = zhipuAgentManageService.chat(agentId, message, fileId, dynamicSystemPrompt, maxTokensOverride);
            } else if ("DeepSeek".equalsIgnoreCase(providerType)) {
                log.info("Routing to DeepSeek service for agent {}", agentId);
                actualEndpoint = profile.getEndpointUrl() + "/v1/chat/completions";
                response = deepSeekAgentManageService.chat(agentId, message, fileId, dynamicSystemPrompt, maxTokensOverride);
            } else if ("Minimax".equalsIgnoreCase(providerType)) {
                log.info("Routing to Minimax service for agent {}", agentId);
                actualEndpoint = profile.getEndpointUrl() + "/v1/text/chatcompletion_v2";
                response = minimaxAgentManageService.chat(agentId, message, fileId);
            } else {
                // Default: try MultiModalProvider first, then fallback to SiliconFlow
                MultiModalProvider provider = providerMap.get(providerType != null ? providerType.toUpperCase() : "");
                if (provider != null) {
                    Map<String, Object> context = new HashMap<>();
                    context.put("conversationId", conversationId);
                    InteractionRequest req = new InteractionRequest(
                            "TEXT", message, null, context);
                    InteractionResult result = provider.process(metadata, req);
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("data", result.getData());
                    resp.put("status", result.getStatus());
                    resp.put("conversation_id", conversationId);
                    response = java.util.Optional.of(resp);
                } else {
                    // Fallback to SiliconFlow global config
                    log.warn("Unknown provider type: {}, falling back to SiliconFlow global config", providerType);
                    response = chatWithSiliconFlowFallback(message, dynamicSystemPrompt, enableThinking, thinkingBudget, conversationId);
                }
            }
        } catch (Exception e) {
            log.error("Error during agent chat processing: {}", e.getMessage(), e);
            errorMessage = e.getMessage();
            response = java.util.Optional.empty();
        }

        // 4. Process and return response - always log regardless of success/failure
        try {
            // Calculate response time
            long responseTime = System.currentTimeMillis() - startTime;

            if (response != null && response.isPresent()) {
                Object respObj = response.get();
                if (!isProviderResponseSuccessful(respObj)) {
                    errorMessage = extractProviderErrorMessage(respObj);
                    log.warn("Provider returned failure payload: providerType={}, error={}", providerType, errorMessage);
                    response = java.util.Optional.empty();
                } else {
                log.info("SiliconFlow response received, type: {}, keys: {}",
                    respObj.getClass().getSimpleName(),
                    respObj instanceof Map ? ((Map<?, ?>) respObj).keySet() : "N/A");

                // 将原始响应转换为 JSON 字符串保存到审计日志
                String responseContentJson;
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    responseContentJson = mapper.writeValueAsString(respObj);
                } catch (Exception e) {
                    responseContentJson = respObj.toString();
                }

                String responseText = extractResponseText(respObj);
                log.info("Extracted response text length: {}, preview: {}",
                    responseText != null ? responseText.length() : 0,
                    responseText != null ? responseText.substring(0, Math.min(100, responseText.length())) : "null");

                // Extract usage info from response
                java.util.Map<String, Integer> usage = extractUsageFromResponse(respObj);
                if (respObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseMap = (Map<String, Object>) respObj;
                    responseMap.put("promptTokens", usage.get("prompt"));
                    responseMap.put("completionTokens", usage.get("completion"));
                    responseMap.put("totalTokens", usage.get("total"));
                }

                // Extract file info (fileId, downloadUrl) for audio/image/video
                java.util.Map<String, String> fileInfo = extractFileInfo(respObj);
                String generatedFileId = fileInfo.get("fileId");
                String generatedDownloadUrl = fileInfo.get("downloadUrl");

                ConversationLog conversationLog = ConversationLog.builder()
                        .conversationId(conversationId)
                        .agentId(agentId)
                        .userId(null)
                        .model(metadata.getModelName())
                        .query(message)
                        .response(responseText)
                        .success(true)
                        .responseTime(responseTime)
                        .promptTokens(usage.get("prompt"))
                        .completionTokens(usage.get("completion"))
                        .totalTokens(usage.get("total"))
                        .fileId(generatedFileId != null && !generatedFileId.isEmpty() ? generatedFileId : null)
                        .downloadUrl(generatedDownloadUrl != null && !generatedDownloadUrl.isEmpty() ? generatedDownloadUrl : null)
                        .build();
                conversationLogService.log(conversationLog);
                log.debug("Conversation log saved for agent: {}, conversation: {}", agentId, conversationId);

                // Record audit log for API call - 保存原始响应 JSON
                try {
                    int statusCode = 200;
                    String endpoint = actualEndpoint;
                    log.info("Saving audit log for agent: {}, providerType: {}, conversationId: {}", agentId, providerType, conversationId);
                    auditLogService.logApiCall(
                            "SYSTEM", null, agentId, providerType, endpoint, "POST",
                            metadata.getModelName(), null, "ORIN",
                            message, responseContentJson, statusCode, responseTime,
                            usage.get("prompt"), usage.get("completion"), 0.0, true, null, null, conversationId,
                            generatedFileId != null && !generatedFileId.isEmpty() ? generatedFileId : null,
                            generatedDownloadUrl != null && !generatedDownloadUrl.isEmpty() ? generatedDownloadUrl : null,
                            MDC.get("traceId"));
                } catch (Exception e) {
                    log.warn("Failed to save audit log: {}", e.getMessage());
                }
                }
            } else {
                // Failed or no response
                ConversationLog conversationLog = ConversationLog.builder()
                        .conversationId(conversationId)
                        .agentId(agentId)
                        .userId(null)
                        .model(metadata.getModelName())
                        .query(message)
                        .response(null)
                        .success(false)
                        .responseTime(responseTime)
                        .errorMessage(errorMessage != null ? errorMessage : "Failed to get response from provider: " + providerType)
                        .build();
                conversationLogService.log(conversationLog);
                log.warn("Failed to get response from provider: {}, errorMessage: {}", providerType, errorMessage);

                // Record audit log for failed API call
                try {
                    int statusCode = 500;
                    String endpoint = actualEndpoint;
                    auditLogService.logApiCall(
                            "SYSTEM", null, agentId, providerType, endpoint, "POST",
                            metadata.getModelName(), null, "ORIN",
                            message, null, statusCode, responseTime,
                            0, 0, 0.0, false, errorMessage, null, conversationId, null, null,
                            MDC.get("traceId"));
                } catch (Exception e) {
                    log.warn("Failed to save audit log for error: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to save conversation log: {}", e.getMessage());
        }

        // 5. Return response or error
        if (response != null && response.isPresent()) {
            return enrichResponse(response.get(), metadata, conversationId);
        }

        // 6. If no response, return error
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("status", "ERROR");
        errorResult.put("error", errorMessage != null ? errorMessage : "Failed to get response from provider: " + providerType);

        return java.util.Optional.of(errorResult);
    }

    private boolean isProviderResponseSuccessful(Object respObj) {
        if (!(respObj instanceof Map<?, ?> map)) {
            return true;
        }
        Object statusObj = map.get("status");
        if (statusObj == null) {
            return true;
        }
        String status = String.valueOf(statusObj).trim().toUpperCase();
        return !("FAILED".equals(status) || "ERROR".equals(status));
    }

    private String extractProviderErrorMessage(Object respObj) {
        if (!(respObj instanceof Map<?, ?> map)) {
            return "Unknown provider failure";
        }
        Object errorMessageObj = map.get("errorMessage");
        if (errorMessageObj != null && !String.valueOf(errorMessageObj).isBlank()) {
            return String.valueOf(errorMessageObj);
        }
        Object errorObj = map.get("error");
        if (errorObj != null && !String.valueOf(errorObj).isBlank()) {
            return String.valueOf(errorObj);
        }
        Object messageObj = map.get("message");
        if (messageObj != null && !String.valueOf(messageObj).isBlank()) {
            return String.valueOf(messageObj);
        }
        Object statusObj = map.get("status");
        return "Provider returned non-success status: " + String.valueOf(statusObj);
    }

    /**
     * Fallback to SiliconFlow global config when agent has no specific provider
     */
    private java.util.Optional<Object> chatWithSiliconFlowFallback(String message, String dynamicSystemPrompt,
            Boolean enableThinking, Integer thinkingBudget, String conversationId) {
        log.info("Attempting chat with SiliconFlow global config");
        try {
            com.adlin.orin.modules.model.entity.ModelConfig modelConfig = modelConfigService.getConfig();
            if (modelConfig != null && modelConfig.getSiliconFlowApiKey() != null
                    && !modelConfig.getSiliconFlowApiKey().isEmpty()) {

                String sfEndpoint = modelConfig.getSiliconFlowEndpoint();
                String sfApiKey = modelConfig.getSiliconFlowApiKey();
                String sfModel = modelConfig.getSiliconFlowModel();

                java.util.List<Map<String, Object>> sfMessages = new java.util.ArrayList<>();
                if (dynamicSystemPrompt != null && !dynamicSystemPrompt.isEmpty()) {
                    sfMessages.add(java.util.Map.of("role", "system", "content", dynamicSystemPrompt));
                }
                sfMessages.add(java.util.Map.of("role", "user", "content", message));

                return siliconFlowIntegrationService.sendMessageWithFullParams(
                        sfEndpoint + "/chat/completions", sfApiKey, sfModel, sfMessages, 0.7, 0.9, 2000, enableThinking, thinkingBudget);
            }
        } catch (Exception e) {
            log.error("SiliconFlow fallback failed: {}", e.getMessage());
        }
        return java.util.Optional.empty();
    }

    /**
     * Enrich response with standard fields
     */
    private java.util.Optional<Object> enrichResponse(Object response, AgentMetadata metadata, String conversationId) {
        if (response instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> respMap = (Map<String, Object>) response;
            respMap.put("conversation_id", conversationId);
            if (!respMap.containsKey("viewType")) {
                respMap.put("viewType", metadata.getViewType());
            }
            if (!respMap.containsKey("provider")) {
                respMap.put("provider", metadata.getProviderType());
            }
            if (!respMap.containsKey("model")) {
                respMap.put("model", metadata.getModelName());
            }
            if (!respMap.containsKey("status")) {
                respMap.put("status", "SUCCESS");
            }
            return java.util.Optional.of(respMap);
        }
        return java.util.Optional.of(response);
    }

    /**
     * 从响应对象中提取文本内容
     */
    private String extractResponseText(Object response) {
        if (response == null) {
            return null;
        }
        try {
            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> respMap = (Map<String, Object>) response;

                // 1. 尝试 OpenAI 格式: choices[0].message.content
                if (respMap.containsKey("choices")) {
                    Object choices = respMap.get("choices");
                    if (choices instanceof java.util.List && !((java.util.List<?>) choices).isEmpty()) {
                        Object firstChoice = ((java.util.List<?>) choices).get(0);
                        if (firstChoice instanceof Map) {
                            Map<?, ?> choiceMap = (Map<?, ?>) firstChoice;
                            if (choiceMap.containsKey("message")) {
                                Object msg = choiceMap.get("message");
                                if (msg instanceof Map) {
                                    Map<?, ?> msgMap = (Map<?, ?>) msg;
                                    // 先尝试 content
                                    Object content = msgMap.get("content");
                                    if (content != null) {
                                        // 如果是字符串，直接返回
                                        if (content instanceof String) {
                                            return (String) content;
                                        }
                                        // 如果是列表 (多模态内容)，尝试提取文本
                                        if (content instanceof java.util.List) {
                                            return extractTextFromContentList((java.util.List<?>) content);
                                        }
                                        // 其他类型转为字符串
                                        return String.valueOf(content);
                                    }
                                    // 兼容深度思考模型 (如 Kimi-K2.5): reasoning_content
                                    Object reasoningContent = msgMap.get("reasoning_content");
                                    if (reasoningContent != null) {
                                        return String.valueOf(reasoningContent);
                                    }
                                }
                            }
                            // 兼容 delta 格式 (流式响应)
                            if (choiceMap.containsKey("delta")) {
                                Object delta = choiceMap.get("delta");
                                if (delta instanceof Map) {
                                    Map<?, ?> deltaMap = (Map<?, ?>) delta;
                                    Object content = deltaMap.get("content");
                                    if (content != null) {
                                        if (content instanceof String) {
                                            return (String) content;
                                        }
                                        if (content instanceof java.util.List) {
                                            return extractTextFromContentList((java.util.List<?>) content);
                                        }
                                        return String.valueOf(content);
                                    }
                                    // 兼容深度思考模型的 delta
                                    Object reasoningContent = deltaMap.get("reasoning_content");
                                    if (reasoningContent != null) {
                                        return String.valueOf(reasoningContent);
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. 检查 dataType 处理特殊类型（AUDIO, IMAGE 等）
                if (respMap.containsKey("dataType")) {
                    String dataType = String.valueOf(respMap.get("dataType"));
                    Object data = respMap.get("data");
                    if (data instanceof Map) {
                        Map<String, Object> dataMap = (Map<String, Object>) data;
                        if ("AUDIO".equals(dataType) && dataMap.containsKey("audio_url")) {
                            // 只保存 file_id，让前端自己构建 URL
                            Object fileIdObj = dataMap.get("file_id");
                            if (fileIdObj != null) {
                                return "[音频文件] file_id=" + fileIdObj.toString();
                            } else {
                                // 下载失败时，返回原始URL
                                String audioUrl = (String) dataMap.get("audio_url");
                                if (audioUrl != null) {
                                    return "[音频文件] " + audioUrl;
                                }
                            }
                        } else if ("IMAGE".equals(dataType) && dataMap.containsKey("image_url")) {
                            Object fileIdObj = dataMap.get("file_id");
                            if (fileIdObj != null) {
                                return "[图片文件] file_id=" + fileIdObj.toString();
                            } else {
                                // 下载失败时，返回原始URL
                                String imageUrl = (String) dataMap.get("image_url");
                                if (imageUrl != null) {
                                    return "[图片文件] " + imageUrl;
                                }
                            }
                        } else if ("VIDEO".equals(dataType) && dataMap.containsKey("video_url")) {
                            Object fileIdObj = dataMap.get("file_id");
                            if (fileIdObj != null) {
                                return "[视频文件] file_id=" + fileIdObj.toString();
                            } else {
                                // 下载失败时，返回原始URL
                                String videoUrl = (String) dataMap.get("video_url");
                                if (videoUrl != null) {
                                    return "[视频文件] " + videoUrl;
                                }
                            }
                        }
                        // 如果有 text 字段，也返回
                        if (dataMap.containsKey("text")) {
                            return String.valueOf(dataMap.get("text"));
                        }
                    }
                }

                // 3. 尝试提取 data 字段
                if (respMap.containsKey("data")) {
                    Object data = respMap.get("data");
                    if (data instanceof String) {
                        return (String) data;
                    } else if (data instanceof Map) {
                        // 递归提取 data 中的文本
                        return extractTextFromMap((Map<String, Object>) data);
                    }
                }

                // 4. 尝试直接字段
                if (respMap.containsKey("content")) {
                    return String.valueOf(respMap.get("content"));
                }
                if (respMap.containsKey("text")) {
                    return String.valueOf(respMap.get("text"));
                }

                // 4. 尝试 message 字段
                if (respMap.containsKey("message")) {
                    Object msg = respMap.get("message");
                    if (msg instanceof Map) {
                        Object content = ((Map<?, ?>) msg).get("content");
                        if (content != null) {
                            return String.valueOf(content);
                        }
                    }
                }

                // 5. 如果都不匹配，返回提示而非整个JSON
                return "[复杂响应对象，请查看审计日志]";
            }
            if (response instanceof String) {
                return (String) response;
            }
            return "[复杂响应对象，请查看审计日志]";
        } catch (Exception e) {
            log.warn("Failed to extract response text: {}", e.getMessage());
            return String.valueOf(response);
        }
    }

    private int resolveMaxTokens(Integer configuredMaxTokens, int fallbackDefault, Integer overrideMaxTokens) {
        if (overrideMaxTokens != null && overrideMaxTokens > 0) {
            return overrideMaxTokens;
        }
        if (configuredMaxTokens != null && configuredMaxTokens > 0) {
            return configuredMaxTokens;
        }
        return fallbackDefault;
    }

    /**
     * 从响应对象中提取文件信息 (fileId 和 downloadUrl)
     * 用于音频、图片、视频等文件类型
     */
    private java.util.Map<String, String> extractFileInfo(Object response) {
        java.util.Map<String, String> fileInfo = new java.util.HashMap<>();
        fileInfo.put("fileId", "");
        fileInfo.put("downloadUrl", "");

        if (response == null) {
            return fileInfo;
        }

        try {
            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> respMap = (Map<String, Object>) response;

                // 检查 dataType 处理特殊类型（AUDIO, IMAGE, VIDEO）
                if (respMap.containsKey("dataType")) {
                    String dataType = String.valueOf(respMap.get("dataType"));
                    Object data = respMap.get("data");
                    if (data instanceof Map) {
                        Map<String, Object> dataMap = (Map<String, Object>) data;

                        if ("AUDIO".equals(dataType) && dataMap.containsKey("audio_url")) {
                            Object fileIdObj = dataMap.get("file_id");
                            if (fileIdObj != null) {
                                String fId = String.valueOf(fileIdObj);
                                String dUrl = "/api/v1/multimodal/files/" + fId + "/download";
                                fileInfo.put("fileId", fId);
                                fileInfo.put("downloadUrl", dUrl);
                            } else {
                                // 下载失败时，保存原始URL
                                String audioUrl = (String) dataMap.get("audio_url");
                                if (audioUrl != null) {
                                    fileInfo.put("downloadUrl", audioUrl);
                                }
                            }
                        } else if ("IMAGE".equals(dataType) && dataMap.containsKey("image_url")) {
                            Object fileIdObj = dataMap.get("file_id");
                            if (fileIdObj != null) {
                                String fId = String.valueOf(fileIdObj);
                                String dUrl = "/api/v1/multimodal/files/" + fId + "/download";
                                fileInfo.put("fileId", fId);
                                fileInfo.put("downloadUrl", dUrl);
                            } else {
                                // 下载失败时，保存原始URL
                                String imageUrl = (String) dataMap.get("image_url");
                                if (imageUrl != null) {
                                    fileInfo.put("downloadUrl", imageUrl);
                                }
                            }
                        } else if ("VIDEO".equals(dataType) && dataMap.containsKey("video_url")) {
                            Object fileIdObj = dataMap.get("file_id");
                            if (fileIdObj != null) {
                                String fId = String.valueOf(fileIdObj);
                                String dUrl = "/api/v1/multimodal/files/" + fId + "/download";
                                fileInfo.put("fileId", fId);
                                fileInfo.put("downloadUrl", dUrl);
                            } else {
                                // 下载失败时，保存原始URL
                                String videoUrl = (String) dataMap.get("video_url");
                                if (videoUrl != null) {
                                    fileInfo.put("downloadUrl", videoUrl);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract file info: {}", e.getMessage());
        }

        return fileInfo;
    }

    /**
     * 从多模态内容列表中提取文本
     */
    private String extractTextFromContentList(java.util.List<?> contentList) {
        if (contentList == null || contentList.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object item : contentList) {
            if (item instanceof Map) {
                Map<?, ?> itemMap = (Map<?, ?>) item;
                // 文本类型
                if ("text".equals(itemMap.get("type"))) {
                    Object text = itemMap.get("text");
                    if (text != null) {
                        sb.append(text.toString());
                    }
                }
                // 图片类型等忽略
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 从 Map 中递归提取文本内容
     */
    private String extractTextFromMap(Map<String, Object> map) {
        // 优先查找文本字段
        if (map.containsKey("text")) {
            return String.valueOf(map.get("text"));
        }
        if (map.containsKey("content")) {
            return String.valueOf(map.get("content"));
        }
        if (map.containsKey("message")) {
            Object msg = map.get("message");
            if (msg instanceof Map) {
                Object content = ((Map<?, ?>) msg).get("content");
                if (content != null) {
                    return String.valueOf(content);
                }
            }
        }
        // 如果没有找到文本字段，返回提示
        return "[复杂响应对象，请查看审计日志]";
    }

    /**
     * 从响应对象中提取 token 使用量
     * @return Map 包含 prompt, completion, total 三个值
     */
    private java.util.Map<String, Integer> extractUsageFromResponse(Object response) {
        java.util.Map<String, Integer> usage = new java.util.HashMap<>();
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

                mergeUsageFromMap(respMap, usage);
                Object dataObj = respMap.get("data");
                if (dataObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dataMap = (Map<String, Object>) dataObj;
                    mergeUsageFromMap(dataMap, usage);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract usage from response: {}", e.getMessage());
        }

        if (usage.get("total") <= 0) {
            usage.put("total", usage.get("prompt") + usage.get("completion"));
        }

        return usage;
    }

    @SuppressWarnings("unchecked")
    private void mergeUsageFromMap(Map<String, Object> source, Map<String, Integer> usage) {
        if (source == null || usage == null) return;

        Object usageObj = source.get("usage");
        if (usageObj instanceof Map) {
            Map<String, Object> usageMap = (Map<String, Object>) usageObj;
            usage.put("prompt", firstNonZero(
                    usage.get("prompt"),
                    toInt(usageMap.get("prompt_tokens")),
                    toInt(usageMap.get("promptTokens")),
                    toInt(usageMap.get("prompt_eval_count"))));
            usage.put("completion", firstNonZero(
                    usage.get("completion"),
                    toInt(usageMap.get("completion_tokens")),
                    toInt(usageMap.get("completionTokens")),
                    toInt(usageMap.get("eval_count"))));
            usage.put("total", firstNonZero(
                    usage.get("total"),
                    toInt(usageMap.get("total_tokens")),
                    toInt(usageMap.get("totalTokens")),
                    toInt(usageMap.get("tokens"))));
        }

        usage.put("prompt", firstNonZero(
                usage.get("prompt"),
                toInt(source.get("promptTokens")),
                toInt(source.get("prompt_tokens")),
                toInt(source.get("prompt_eval_count"))));
        usage.put("completion", firstNonZero(
                usage.get("completion"),
                toInt(source.get("completionTokens")),
                toInt(source.get("completion_tokens")),
                toInt(source.get("eval_count"))));
        usage.put("total", firstNonZero(
                usage.get("total"),
                toInt(source.get("totalTokens")),
                toInt(source.get("total_tokens")),
                toInt(source.get("tokens"))));
    }

    private int firstNonZero(int... values) {
        for (int value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    /**
     * 安全地将 Object 转换为 Integer
     */
    private int toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public byte[] batchExportAgents(List<String> agentIds) {
        log.info("Starting batch export for agentIds: {}", agentIds);

        List<AgentMetadata> agentsToExport;
        if (agentIds == null || agentIds.isEmpty()) {
            // Export all agents
            agentsToExport = metadataRepository.findAll();
        } else {
            agentsToExport = metadataRepository.findAllById(agentIds);
        }

        int exportCount = agentsToExport.size();
        String detail = agentIds == null || agentIds.isEmpty()
                ? "导出所有智能体"
                : "导出指定 " + agentIds.size() + " 个智能体";

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());

            List<Map<String, Object>> exportData = agentsToExport.stream().map(agent -> {
                Map<String, Object> data = new HashMap<>();
                data.put("agentId", agent.getAgentId());
                data.put("name", agent.getName());
                data.put("description", agent.getDescription());
                data.put("icon", agent.getIcon());
                data.put("mode", agent.getMode());
                data.put("modelName", agent.getModelName());
                data.put("providerType", agent.getProviderType());
                data.put("viewType", agent.getViewType());
                data.put("temperature", agent.getTemperature());
                data.put("topP", agent.getTopP());
                data.put("maxTokens", agent.getMaxTokens());
                data.put("systemPrompt", agent.getSystemPrompt());
                data.put("parameters", agent.getParameters());

                // Get access profile (mask API key for security)
                accessProfileRepository.findById(agent.getAgentId()).ifPresent(profile -> {
                    Map<String, Object> accessData = new HashMap<>();
                    accessData.put("endpointUrl", profile.getEndpointUrl());
                    accessData.put("apiKey", "***MASKED***"); // Never export raw API key
                    data.put("accessProfile", accessData);
                });

                return data;
            }).toList();

            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportData);

            // Audit logging for successful export
            auditHelper.logAgentBatchExport("SYSTEM", "EXPORT", exportCount, detail, true, null);

            return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Failed to export agents", e);
            // Audit logging for failed export
            auditHelper.logAgentBatchExport("SYSTEM", "EXPORT", 0, detail, false, e.getMessage());
            throw new RuntimeException("Failed to export agents: " + e.getMessage(), e);
        }
    }

    @Override
    public void batchImportAgents(MultipartFile file) {
        log.info("Starting batch import from file: {}", file.getOriginalFilename());

        int importedCount = 0;
        int skippedCount = 0;
        String detail = "从文件 " + file.getOriginalFilename() + " 导入智能体";

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> importData = mapper.readValue(file.getInputStream(),
                    new TypeReference<List<Map<String, Object>>>() {});

            for (Map<String, Object> data : importData) {
                String agentId = (String) data.get("agentId");
                String name = (String) data.get("name");

                if (agentId == null || name == null) {
                    log.warn("Skipping invalid agent entry: {}", data);
                    skippedCount++;
                    continue;
                }

                // Check if agent already exists
                if (metadataRepository.existsById(agentId)) {
                    log.warn("Agent {} already exists, skipping", agentId);
                    skippedCount++;
                    continue;
                }

                // Create new agent metadata
                AgentMetadata metadata = new AgentMetadata();
                metadata.setAgentId(agentId);
                metadata.setName(name);
                metadata.setDescription((String) data.get("description"));
                metadata.setIcon((String) data.get("icon"));
                metadata.setMode((String) data.get("mode"));
                metadata.setModelName((String) data.get("modelName"));
                metadata.setProviderType((String) data.get("providerType"));
                metadata.setViewType((String) data.get("viewType"));
                metadata.setTemperature(toDouble(data.get("temperature")));
                metadata.setTopP(toDouble(data.get("topP")));
                metadata.setMaxTokens(toInteger(data.get("maxTokens")));
                metadata.setSystemPrompt((String) data.get("systemPrompt"));
                metadata.setParameters((String) data.get("parameters"));
                metadata.setSyncTime(LocalDateTime.now());

                metadataRepository.save(metadata);

                // Create access profile (if provided)
                @SuppressWarnings("unchecked")
                Map<String, Object> accessData = (Map<String, Object>) data.get("accessProfile");
                if (accessData != null) {
                    AgentAccessProfile profile = new AgentAccessProfile();
                    profile.setAgentId(agentId);
                    profile.setEndpointUrl((String) accessData.get("endpointUrl"));
                    // Note: API key is masked during export, so we skip importing it
                    profile.setCreatedAt(LocalDateTime.now());
                    profile.setUpdatedAt(LocalDateTime.now());
                    accessProfileRepository.save(profile);
                }

                // Create health status entry
                AgentHealthStatus status = new AgentHealthStatus();
                status.setAgentId(agentId);
                status.setAgentName(name);
                status.setModelName(metadata.getModelName());
                status.setStatus(com.adlin.orin.modules.monitor.entity.AgentStatus.UNKNOWN);
                status.setLastHeartbeat(System.currentTimeMillis());
                healthStatusRepository.save(status);

                importedCount++;
                log.info("Imported agent: {} ({})", name, agentId);
            }

            log.info("Batch import completed: {} imported, {} skipped", importedCount, skippedCount);

            // Audit logging for successful import
            auditHelper.logAgentBatchImport("SYSTEM", "IMPORT", importedCount, skippedCount, detail, true, null);

        } catch (Exception e) {
            log.error("Failed to import agents", e);
            // Audit logging for failed import
            auditHelper.logAgentBatchImport("SYSTEM", "IMPORT", importedCount, skippedCount, detail, false, e.getMessage());
            throw new RuntimeException("Failed to import agents: " + e.getMessage(), e);
        }
    }

    private Double toDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Double) return (Double) obj;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void refreshAllAgentsMetadata() {
        log.info("Starting refresh of all agents metadata");

        List<AgentMetadata> allAgents = metadataRepository.findAll();
        int successCount = 0;
        int failedCount = 0;

        for (AgentMetadata agent : allAgents) {
            try {
                String agentId = agent.getAgentId();
                String providerType = agent.getProviderType();

                // Get access profile for API credentials
                AgentAccessProfile profile = accessProfileRepository.findById(agentId).orElse(null);
                if (profile == null) {
                    log.warn("No access profile for agent {}, skipping", agentId);
                    failedCount++;
                    continue;
                }

                if ("DIFY".equals(providerType)) {
                    // Fetch app info from Dify
                    difyIntegrationService.fetchAppMeta(profile.getEndpointUrl(), profile.getApiKey())
                            .ifPresent(meta -> {
                                if (meta.containsKey("name")) {
                                    agent.setName((String) meta.get("name"));
                                }
                                if (meta.containsKey("description")) {
                                    agent.setDescription((String) meta.get("description"));
                                }
                            });

                    // Fetch parameters from Dify
                    difyIntegrationService.fetchAppParameters(profile.getEndpointUrl(), profile.getApiKey())
                            .ifPresent(params -> {
                                try {
                                    agent.setParameters(new ObjectMapper().writeValueAsString(params));
                                } catch (Exception e) {
                                    log.warn("Failed to serialize parameters for agent {}", agentId);
                                }
                            });

                } else if ("SILICONFLOW".equals(providerType)) {
                    // For SiliconFlow, sync model name and other settings
                    // SiliconFlow agents typically don't have remote metadata to fetch
                    log.debug("SiliconFlow agent {} doesn't support remote metadata fetch", agentId);

                } else if ("OLLAMA".equals(providerType)) {
                    // For Ollama, try to verify connection
                    if (!ollamaIntegrationService.testConnection(profile.getEndpointUrl(), null, agent.getModelName())) {
                        log.warn("Ollama connection failed for agent {}", agentId);
                    }
                }

                agent.setSyncTime(LocalDateTime.now());
                metadataRepository.save(agent);
                successCount++;

            } catch (Exception e) {
                log.error("Failed to refresh metadata for agent {}: {}", agent.getAgentId(), e.getMessage());
                failedCount++;
            }
        }

        log.info("Refresh completed: {} success, {} failed", successCount, failedCount);

        // Audit logging for metadata refresh
        String detail = "刷新所有智能体元数据";
        boolean isSuccess = failedCount == 0;
        auditHelper.logAgentMetadataRefresh("SYSTEM", successCount, failedCount, detail, isSuccess, null);
    }

    @Override
    public Object getJobStatus(String jobId) {
        return agentJobRepository.findByJobId(jobId)
                .map(job -> java.util.Map.of(
                        "jobId", job.getJobId(),
                        "agentId", job.getAgentId() != null ? job.getAgentId() : "",
                        "jobType", job.getJobType(),
                        "status", job.getStatus().name(),
                        "statusDesc", job.getStatus().getDescription(),
                        "progress", job.getProgress() != null ? job.getProgress() : 0,
                        "resultData", job.getResultData() != null ? job.getResultData() : "",
                        "errorMessage", job.getErrorMessage() != null ? job.getErrorMessage() : "",
                        "createdAt", job.getCreatedAt() != null ? job.getCreatedAt().toString() : "",
                        "completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : ""
                ))
                .orElse(null);
    }

    @Override
    public String createAsyncJob(String jobType, List<String> agentIds, String triggeredBy) {
        String jobId = "job-" + UUID.randomUUID().toString();
        AgentJobEntity job = AgentJobEntity.builder()
                .jobId(jobId)
                .jobType(jobType)
                .status(AgentJobEntity.JobStatus.PENDING)
                .progress(0)
                .triggeredBy(triggeredBy)
                .build();
        agentJobRepository.save(job);
        log.info("Created async job: {} of type {}", jobId, jobType);
        return jobId;
    }
}
