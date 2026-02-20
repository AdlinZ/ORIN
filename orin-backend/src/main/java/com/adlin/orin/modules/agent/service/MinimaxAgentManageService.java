package com.adlin.orin.modules.agent.service;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.model.service.MinimaxIntegrationService;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.entity.AgentStatus;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinimaxAgentManageService implements AgentManageService {

    private final MinimaxIntegrationService minimaxIntegrationService;
    private final AgentAccessProfileRepository accessProfileRepository;
    private final AgentMetadataRepository metadataRepository;
    private final AgentHealthStatusRepository healthStatusRepository;

    @Override
    public AgentMetadata onboardAgent(String endpointUrl, String apiKey, String datasetApiKey) {
        return onboardAgentInternal(endpointUrl, apiKey, "abab6.5g-chat", null);
    }

    public AgentMetadata onboardAgent(String endpointUrl, String apiKey, String model, String agentName) {
        return onboardAgentInternal(endpointUrl, apiKey, model, agentName);
    }

    private AgentMetadata onboardAgentInternal(String endpointUrl, String apiKey, String model, String agentName) {
        String modelName = model != null && !model.isEmpty() ? model : "abab6.5g-chat";
        minimaxIntegrationService.testConnection(endpointUrl, apiKey, modelName);

        String generatedId = UUID.randomUUID().toString().substring(0, 8);
        String finalName = (agentName != null && !agentName.trim().isEmpty()) ? agentName
                : "MiniMax-" + modelName + "-" + generatedId.substring(0, 4);

        AgentAccessProfile profile = AgentAccessProfile.builder()
                .agentId(generatedId)
                .endpointUrl(endpointUrl)
                .apiKey(apiKey)
                .createdAt(LocalDateTime.now())
                .connectionStatus("VALID")
                .build();
        accessProfileRepository.save(profile);

        String viewType = "CHAT";
        if (modelName.contains("speech")) {
            viewType = "TTS";
        }

        AgentMetadata metadata = AgentMetadata.builder()
                .agentId(generatedId)
                .name(finalName)
                .description("通过 MiniMax API 接入的智能体: " + modelName)
                .mode("chat")
                .icon("🤖")
                .modelName(modelName)
                .providerType("MiniMax")
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
                .providerType("MiniMax")
                .modelName(modelName)
                .viewType(viewType)
                .build();
        healthStatusRepository.save(health);

        return metadata;
    }

    @Override
    public void updateAgent(String agentId, com.adlin.orin.modules.agent.dto.AgentOnboardRequest request) {
        // Implementation similar to SiliconFlow
    }

    @Override
    public Optional<Object> chat(String agentId, String message, org.springframework.web.multipart.MultipartFile file) {
        return Optional.empty(); // Not supported yet
    }

    @Override
    public Optional<Object> chat(String agentId, String message, String fileId) {
        // Implementation similar to SiliconFlow
        return Optional.empty();
    }

    @Override
    public List<AgentMetadata> getAllAgents() {
        return metadataRepository.findAll();
    }

    @Override
    public AgentAccessProfile getAgentAccessProfile(String agentId) {
        return accessProfileRepository.findById(agentId).orElseThrow();
    }

    @Override
    public AgentMetadata getAgentMetadata(String agentId) {
        return metadataRepository.findById(agentId).orElseThrow();
    }

    @Override
    public void deleteAgent(String agentId) {
        metadataRepository.deleteById(agentId);
        accessProfileRepository.deleteById(agentId);
        healthStatusRepository.deleteById(agentId);
    }
}
