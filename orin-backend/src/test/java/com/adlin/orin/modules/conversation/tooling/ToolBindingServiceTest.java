package com.adlin.orin.modules.conversation.tooling;

import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.service.AgentOwnershipResolver;
import com.adlin.orin.modules.conversation.dto.ChatMessageRequest;
import com.adlin.orin.modules.conversation.dto.tooling.EffectiveToolBinding;
import com.adlin.orin.modules.conversation.entity.AgentChatSession;
import com.adlin.orin.modules.conversation.entity.AgentToolBinding;
import com.adlin.orin.modules.conversation.entity.SessionToolBinding;
import com.adlin.orin.modules.conversation.repository.AgentToolBindingRepository;
import com.adlin.orin.modules.conversation.repository.SessionToolBindingRepository;
import com.adlin.orin.modules.skill.repository.McpServiceRepository;
import com.adlin.orin.modules.skill.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolBindingServiceTest {

    @Mock
    private AgentToolBindingRepository agentToolBindingRepository;

    @Mock
    private SessionToolBindingRepository sessionToolBindingRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private McpServiceRepository mcpServiceRepository;

    @Mock
    private AgentMetadataRepository agentMetadataRepository;

    @Mock
    private AgentOwnershipResolver ownershipResolver;

    @InjectMocks
    private ToolBindingService toolBindingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(toolBindingService, "defaultBindActiveSkills", false);
        ReflectionTestUtils.setField(toolBindingService, "defaultBindConnectedMcp", false);
    }

    @Test
    void resolveEffectiveBinding_prefersRequestThenSessionThenAgentThenAttached() {
        AgentChatSession session = new AgentChatSession();
        session.setSessionId("session-1");
        session.setAgentId("agent-1");
        session.setAttachedKbIds(List.of("kb-attached"));

        when(agentToolBindingRepository.findById("agent-1")).thenReturn(Optional.of(
                AgentToolBinding.builder()
                        .agentId("agent-1")
                        .kbIds(List.of("kb-agent"))
                        .build()));
        when(sessionToolBindingRepository.findById("session-1")).thenReturn(Optional.of(
                SessionToolBinding.builder()
                        .sessionId("session-1")
                        .agentId("agent-1")
                        .kbIds(List.of("kb-session"))
                        .build()));

        ChatMessageRequest requestWithKb = new ChatMessageRequest();
        requestWithKb.setKbIds(List.of("kb-request"));
        EffectiveToolBinding fromRequest = toolBindingService.resolveEffectiveBinding(session, requestWithKb);
        assertEquals(List.of("kb-request"), fromRequest.getKbIds());

        EffectiveToolBinding fromSession = toolBindingService.resolveEffectiveBinding(
                session, new ChatMessageRequest());
        assertEquals(List.of("kb-session"), fromSession.getKbIds());

        when(sessionToolBindingRepository.findById("session-1")).thenReturn(Optional.empty());
        EffectiveToolBinding fromAgent = toolBindingService.resolveEffectiveBinding(
                session, new ChatMessageRequest());
        assertEquals(List.of("kb-agent"), fromAgent.getKbIds());

        when(agentToolBindingRepository.findById("agent-1")).thenReturn(Optional.empty());
        EffectiveToolBinding fromAttached = toolBindingService.resolveEffectiveBinding(
                session, new ChatMessageRequest());
        assertEquals(List.of("kb-attached"), fromAttached.getKbIds());
    }
}
