package com.adlin.orin.modules.agent.service;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.audit.service.AuditLogService;
import com.adlin.orin.modules.model.repository.ModelMetadataRepository;
import com.adlin.orin.modules.model.service.DeepSeekIntegrationService;
import com.adlin.orin.modules.model.service.KimiIntegrationService;
import com.adlin.orin.modules.model.service.MinimaxIntegrationService;
import com.adlin.orin.modules.model.service.SiliconFlowIntegrationService;
import com.adlin.orin.modules.model.service.ZhipuIntegrationService;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderOnboardingOwnershipTest {

    @Mock private AgentAccessProfileRepository accessProfileRepository;
    @Mock private AgentMetadataRepository metadataRepository;
    @Mock private AgentHealthStatusRepository healthStatusRepository;
    @Mock private ModelMetadataRepository modelMetadataRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private AgentOwnershipResolver ownershipResolver;
    @Mock private SiliconFlowIntegrationService siliconFlowIntegrationService;
    @Mock private ZhipuIntegrationService zhipuIntegrationService;
    @Mock private DeepSeekIntegrationService deepSeekIntegrationService;
    @Mock private MinimaxIntegrationService minimaxIntegrationService;
    @Mock private KimiIntegrationService kimiIntegrationService;

    @Test
    void providerOnboardingSetsCurrentUserAsOwner() {
        when(ownershipResolver.resolveFromCurrentRequest()).thenReturn(42L);
        when(modelMetadataRepository.findByModelId(anyString())).thenReturn(Optional.empty());
        when(siliconFlowIntegrationService.testConnection(anyString(), anyString())).thenReturn(true);
        when(siliconFlowIntegrationService.resolveSiliconFlowViewType(anyString(), anyString(), anyString()))
                .thenReturn("CHAT");
        when(kimiIntegrationService.testConnection(anyString(), anyString())).thenReturn(true);

        new SiliconFlowAgentManageService(siliconFlowIntegrationService, accessProfileRepository, metadataRepository,
                healthStatusRepository, modelMetadataRepository, auditLogService, ownershipResolver)
                .onboardAgent("https://api.example.test/v1", "key", "qwen", "sf");
        new ZhipuAgentManageService(zhipuIntegrationService, accessProfileRepository, metadataRepository,
                healthStatusRepository, modelMetadataRepository, auditLogService, ownershipResolver)
                .onboardAgent("https://api.example.test/v1", "key", "glm-4", "zhipu", 1.0);
        new DeepSeekAgentManageService(deepSeekIntegrationService, accessProfileRepository, metadataRepository,
                healthStatusRepository, modelMetadataRepository, auditLogService, ownershipResolver)
                .onboardAgent("https://api.example.test/v1", "key", "deepseek-chat", "deepseek", 1.0);
        new MinimaxAgentManageService(minimaxIntegrationService, accessProfileRepository, metadataRepository,
                healthStatusRepository, ownershipResolver)
                .onboardAgent("https://api.example.test/v1", "key", "abab6.5g-chat", "minimax");
        new KimiAgentManageService(kimiIntegrationService, accessProfileRepository, metadataRepository,
                healthStatusRepository, modelMetadataRepository, auditLogService, ownershipResolver)
                .onboardAgent("https://api.example.test/v1", "key", "moonshot-v1-8k-chat", "kimi");

        ArgumentCaptor<AgentMetadata> captor = ArgumentCaptor.forClass(AgentMetadata.class);
        verify(metadataRepository, org.mockito.Mockito.times(5)).save(captor.capture());
        assertEquals(5, captor.getAllValues().stream()
                .filter(metadata -> Long.valueOf(42L).equals(metadata.getOwnerUserId()))
                .count());
        verify(ownershipResolver, org.mockito.Mockito.times(5)).resolveFromCurrentRequest();
    }
}
