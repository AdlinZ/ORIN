package com.adlin.orin.modules.trace.controller;

import com.adlin.orin.modules.observability.service.LangfuseObservabilityService;
import com.adlin.orin.modules.trace.entity.WorkflowTraceEntity;
import com.adlin.orin.modules.trace.service.TraceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TraceControllerTest {

    private TraceService traceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        traceService = mock(TraceService.class);
        LangfuseObservabilityService langfuseService = mock(LangfuseObservabilityService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TraceController(traceService, langfuseService))
                .build();
    }

    @Test
    void getTraceShouldWorkWithLegacyPrefix() throws Exception {
        when(traceService.queryTracesByTraceId("trace-1")).thenReturn(List.of(trace("trace-1")));

        mockMvc.perform(get("/api/traces/trace-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].traceId").value("trace-1"));
    }

    @Test
    void getTraceShouldWorkWithApiV1Prefix() throws Exception {
        when(traceService.queryTracesByTraceId("trace-1")).thenReturn(List.of(trace("trace-1")));

        mockMvc.perform(get("/api/v1/traces/trace-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].traceId").value("trace-1"));
    }

    @Test
    void getRecentTracesShouldUseApiV1Prefix() throws Exception {
        when(traceService.getRecentTraceSummaries(20)).thenReturn(List.of(Map.of(
                "traceId", "trace-1",
                "status", "SUCCESS",
                "totalSteps", 1
        )));

        mockMvc.perform(get("/api/v1/traces/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].traceId").value("trace-1"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    @Test
    void getTraceSummaryShouldReturnSanitizedAggregate() throws Exception {
        when(traceService.getTraceSummary("trace-1")).thenReturn(new java.util.LinkedHashMap<>(Map.of(
                "traceId", "trace-1",
                "found", true,
                "workflowTasks", List.of(Map.of("taskId", "task-1", "status", "FAILED")),
                "auditLogs", List.of(Map.of("id", "audit-1", "success", false))
        )));

        mockMvc.perform(get("/api/v1/traces/trace-1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value("trace-1"))
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.workflowTasks[0].taskId").value("task-1"))
                .andExpect(jsonPath("$.auditLogs[0].id").value("audit-1"))
                .andExpect(jsonPath("$.langfuse.available").value(false));
    }

    private WorkflowTraceEntity trace(String traceId) {
        return WorkflowTraceEntity.builder()
                .traceId(traceId)
                .instanceId(1L)
                .status(WorkflowTraceEntity.TraceStatus.SUCCESS)
                .build();
    }
}
