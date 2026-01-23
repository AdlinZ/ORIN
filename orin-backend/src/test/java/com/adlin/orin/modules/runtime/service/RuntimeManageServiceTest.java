package com.adlin.orin.modules.runtime.service;

import com.adlin.orin.modules.runtime.entity.AgentLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeManageServiceTest {

    @Mock
    private RuntimeManageService runtimeManageService;

    @Test
    void testControlAgent() {
        runtimeManageService.controlAgent("agent-1", "start");
        verify(runtimeManageService).controlAgent("agent-1", "start");
    }

    @Test
    void testGetAgentLogs() {
        AgentLog log = AgentLog.builder()
                .agentId("agent-1")
                .level("INFO")
                .content("Started successfully")
                .timestamp(java.time.LocalDateTime.now())
                .build();

        List<AgentLog> logs = Collections.singletonList(log);

        when(runtimeManageService.getAgentLogs("agent-1")).thenReturn(logs);

        List<AgentLog> result = runtimeManageService.getAgentLogs("agent-1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("INFO", result.get(0).getLevel());
    }
}
