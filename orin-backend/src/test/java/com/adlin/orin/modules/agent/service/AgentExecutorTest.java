package com.adlin.orin.modules.agent.service;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentExecutorTest {

    @Mock
    private AgentManageService agentManageService;

    @InjectMocks
    private AgentExecutor agentExecutor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void executeAgent_Success() {
        // Arrange
        Long agentId = 1L;
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("message", "Hello, agent!");

        AgentMetadata metadata = new AgentMetadata();
        metadata.setName("Test Agent");

        when(agentManageService.getAgentMetadata(anyString())).thenReturn(metadata);
        when(agentManageService.chat(anyString(), anyString(), (String) isNull()))
                .thenReturn(Optional.of("Agent response"));

        // Act
        Map<String, Object> result = agentExecutor.executeAgent(agentId, inputs);

        // Assert
        assertTrue((Boolean) result.get("success"));
        assertEquals("Agent response", result.get("response"));
        assertEquals(agentId, result.get("agentId"));
        verify(agentManageService).chat(eq("1"), eq("Hello, agent!"), (String) isNull());
    }

    @Test
    void executeAgent_AgentNotFound() {
        // Arrange
        Long agentId = 999L;
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("message", "Test");

        when(agentManageService.getAgentMetadata(anyString())).thenReturn(null);

        // Act
        Map<String, Object> result = agentExecutor.executeAgent(agentId, inputs);

        // Assert
        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains("Agent not found"));
    }

    @Test
    void executeAgent_MissingMessage() {
        // Arrange
        Long agentId = 1L;
        Map<String, Object> inputs = new HashMap<>();
        // No message field

        AgentMetadata metadata = new AgentMetadata();
        metadata.setName("Test Agent");

        when(agentManageService.getAgentMetadata(anyString())).thenReturn(metadata);

        // Act
        Map<String, Object> result = agentExecutor.executeAgent(agentId, inputs);

        // Assert
        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains("Message is required"));
    }
}
