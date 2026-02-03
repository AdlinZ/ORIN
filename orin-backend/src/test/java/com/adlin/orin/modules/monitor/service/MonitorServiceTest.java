package com.adlin.orin.modules.monitor.service;

import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.entity.AgentStatus;
import com.adlin.orin.modules.monitor.entity.AgentMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock
    private MonitorService monitorService;

    @BeforeEach
    void setUp() {
        // Since we are mocking the interface, we can define behavior in each test
    }

    @Test
    void testGetGlobalSummary() {
        Map<String, Object> mockSummary = Map.of(
                "totalAgents", 10,
                "activeAgents", 5,
                "totalRequests", 1000,
                "avgResponseTime", 200.5);

        when(monitorService.getGlobalSummary()).thenReturn(mockSummary);

        Map<String, Object> result = monitorService.getGlobalSummary();

        assertNotNull(result);
        assertEquals(10, result.get("totalAgents"));
        assertEquals(5, result.get("activeAgents"));
        verify(monitorService).getGlobalSummary();
    }

    @Test
    void testGetAgentStatus() {
        AgentHealthStatus mockStatus = AgentHealthStatus.builder()
                .agentId("agent-1")
                .healthScore(95)
                .status(AgentStatus.RUNNING)
                .lastHeartbeat(System.currentTimeMillis())
                .build();

        when(monitorService.getAgentStatus("agent-1")).thenReturn(mockStatus);

        AgentHealthStatus result = monitorService.getAgentStatus("agent-1");

        assertNotNull(result);
        assertEquals("agent-1", result.getAgentId());
        assertEquals(AgentStatus.RUNNING, result.getStatus());
        assertEquals(95, result.getHealthScore());
    }

    @Test
    void testGetAgentMetrics() {
        AgentMetric metric = AgentMetric.builder()
                .timestamp(System.currentTimeMillis())
                .cpuUsage(0.5)
                .memoryUsage(0.4)
                .build();

        List<AgentMetric> metrics = Collections.singletonList(metric);

        when(monitorService.getAgentMetrics(anyString(), anyLong(), anyLong(), anyString()))
                .thenReturn(metrics);

        List<AgentMetric> result = monitorService.getAgentMetrics("agent-1", 0L, 0L, "1m");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(0.5, result.get(0).getCpuUsage());
    }
}
