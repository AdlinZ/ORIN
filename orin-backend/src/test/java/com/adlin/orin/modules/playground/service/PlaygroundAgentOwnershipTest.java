package com.adlin.orin.modules.playground.service;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.service.AgentOwnershipResolver;
import com.adlin.orin.modules.playground.repository.PlaygroundConversationRepository;
import com.adlin.orin.modules.playground.repository.PlaygroundMessageRepository;
import com.adlin.orin.modules.playground.repository.PlaygroundRunRepository;
import com.adlin.orin.modules.playground.repository.PlaygroundWorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaygroundAgentOwnershipTest {

    @Mock private AgentMetadataRepository agentRepository;
    @Mock private PlaygroundWorkflowRepository workflowRepository;
    @Mock private PlaygroundConversationRepository conversationRepository;
    @Mock private PlaygroundMessageRepository messageRepository;
    @Mock private PlaygroundRunRepository runRepository;
    @Mock private PlaygroundGraphFactory graphFactory;
    @Mock private PlaygroundRuntimeClient runtimeClient;
    @Mock private AgentOwnershipResolver ownershipResolver;

    @Test
    void createAgentUsesCurrentRequestOwner() {
        when(ownershipResolver.resolveFromCurrentRequest()).thenReturn(42L);
        when(agentRepository.save(any(AgentMetadata.class))).thenAnswer(inv -> inv.getArgument(0));

        service().createAgent(Map.of(
                "name", "custom",
                "description", "custom agent",
                "system_prompt", "answer clearly"
        ));

        ArgumentCaptor<AgentMetadata> captor = ArgumentCaptor.forClass(AgentMetadata.class);
        verify(agentRepository).save(captor.capture());
        assertEquals(42L, captor.getValue().getOwnerUserId());
    }

    @Test
    void defaultAgentUsesSystemAdminOwner() {
        when(agentRepository.findById("playground_default_assistant")).thenReturn(Optional.empty());
        when(agentRepository.save(any(AgentMetadata.class))).thenAnswer(inv -> inv.getArgument(0));
        when(agentRepository.findAll()).thenReturn(List.of());
        when(ownershipResolver.resolveForSystemSeed()).thenReturn(1L);

        service().listAgents();

        ArgumentCaptor<AgentMetadata> captor = ArgumentCaptor.forClass(AgentMetadata.class);
        verify(agentRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getOwnerUserId());
    }

    private PlaygroundService service() {
        return new PlaygroundService(agentRepository, workflowRepository, conversationRepository, messageRepository,
                runRepository, graphFactory, runtimeClient, new ObjectMapper(), ownershipResolver);
    }
}
