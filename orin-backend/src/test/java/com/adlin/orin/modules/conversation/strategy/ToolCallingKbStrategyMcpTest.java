package com.adlin.orin.modules.conversation.strategy;

import com.adlin.orin.modules.skill.component.AiEngineMcpClient;
import com.adlin.orin.modules.conversation.tooling.ToolExecutionLogService;
import com.adlin.orin.modules.skill.component.McpErrorCode;
import com.adlin.orin.modules.skill.entity.McpService;
import com.adlin.orin.modules.skill.repository.McpServiceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolCallingKbStrategyMcpTest {

    @Mock
    private McpServiceRepository mcpServiceRepository;
    @Mock
    private AiEngineMcpClient aiEngineMcpClient;
    @Mock
    private ToolExecutionLogService toolExecutionLogService;

    private ToolCallingKbStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ToolCallingKbStrategy(
                null, null, null, null, null, null, null, null, null, null,
                mcpServiceRepository, aiEngineMcpClient, new ObjectMapper());
        strategy.configureExecutionLog("session-1", "agent-1", toolExecutionLogService);
    }

    private McpService service(McpService.McpStatus status) {
        return McpService.builder()
                .id(42L)
                .name("fetch")
                .enabled(true)
                .status(status)
                .build();
    }

    @Test
    void executeMcpTool_serviceNotFound_returnsNotFoundCode() {
        when(mcpServiceRepository.findById(42L)).thenReturn(Optional.empty());

        ToolCallingKbStrategy.ToolOutcome outcome = strategy.executeMcpTool(42L, "Fetch",
                Map.of("toolName", "fetch"));

        assertEquals("MCP_NOT_FOUND", outcome.errorCode());
        verifyNoInteractions(aiEngineMcpClient);
    }

    @Test
    void executeMcpTool_missingToolName_returnsBadRequestCodeWithoutCallingAiEngine() {
        when(mcpServiceRepository.findById(42L))
                .thenReturn(Optional.of(service(McpService.McpStatus.CONNECTED)));

        ToolCallingKbStrategy.ToolOutcome outcome = strategy.executeMcpTool(42L, "Fetch",
                Map.of("arguments", Map.of("url", "x")));

        assertEquals("MCP_BAD_REQUEST", outcome.errorCode());
        assertTrue(outcome.content().contains("toolName"));
        verifyNoInteractions(aiEngineMcpClient);
    }

    @Test
    void executeMcpTool_serviceNotConnected_returnsNotConnectedCodeWithoutCallingAiEngine() {
        when(mcpServiceRepository.findById(42L))
                .thenReturn(Optional.of(service(McpService.McpStatus.DISCONNECTED)));

        ToolCallingKbStrategy.ToolOutcome outcome = strategy.executeMcpTool(42L, "Fetch",
                Map.of("toolName", "fetch", "arguments", Map.of("url", "x")));

        assertEquals("MCP_NOT_CONNECTED", outcome.errorCode());
        assertTrue(outcome.content().contains("未连接"));
        verifyNoInteractions(aiEngineMcpClient);
    }

    @Test
    void executeMcpTool_success_delegatesToolNameAndArgumentsToAiEngine() {
        when(mcpServiceRepository.findById(42L))
                .thenReturn(Optional.of(service(McpService.McpStatus.CONNECTED)));
        when(aiEngineMcpClient.callTool(eq(42L), eq("fetch"), anyMap()))
                .thenReturn("{\"status\":200}");

        ToolCallingKbStrategy.ToolOutcome outcome = strategy.executeMcpTool(42L, "Fetch",
                Map.of("toolName", "fetch", "arguments", Map.of("url", "https://example.com")));

        assertNull(outcome.errorCode());
        assertEquals("{\"status\":200}", outcome.content());

        ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiEngineMcpClient).callTool(eq(42L), eq("fetch"), argsCaptor.capture());
        assertEquals("https://example.com", argsCaptor.getValue().get("url"));

        ArgumentCaptor<Map<String, Object>> detailCaptor = ArgumentCaptor.forClass(Map.class);
        verify(toolExecutionLogService).log(
                eq("session-1"),
                eq("agent-1"),
                eq("mcp:42"),
                eq("function_call"),
                eq(true),
                isNull(),
                longThat(value -> value >= 1),
                detailCaptor.capture());
        assertEquals("MCP", detailCaptor.getValue().get("source"));
        assertEquals(42L, detailCaptor.getValue().get("mcpServiceId"));
        assertEquals("fetch", detailCaptor.getValue().get("mcpToolName"));
        assertEquals(java.util.List.of("url"), detailCaptor.getValue().get("argumentKeys"));
    }

    @Test
    void executeMcpTool_aiEngineFailure_propagatesErrorCodeAndReadableMessage() {
        when(mcpServiceRepository.findById(42L))
                .thenReturn(Optional.of(service(McpService.McpStatus.CONNECTED)));
        when(aiEngineMcpClient.callTool(anyLong(), anyString(), anyMap()))
                .thenThrow(new AiEngineMcpClient.McpToolCallException(
                        McpErrorCode.MCP_TIMEOUT, "MCP 工具调用超时或 AI Engine 不可达", null));

        ToolCallingKbStrategy.ToolOutcome outcome = strategy.executeMcpTool(42L, "Fetch",
                Map.of("toolName", "fetch", "arguments", Map.of()));

        assertEquals("MCP_TIMEOUT", outcome.errorCode());
        assertTrue(outcome.content().contains("执行失败"));
        assertTrue(outcome.content().contains("超时"));
        verify(toolExecutionLogService).log(
                eq("session-1"),
                eq("agent-1"),
                eq("mcp:42"),
                eq("function_call"),
                eq(false),
                eq("MCP_TIMEOUT"),
                longThat(value -> value >= 1),
                anyMap());
    }
}
