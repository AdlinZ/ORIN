package com.adlin.orin.modules.conversation.strategy;

import com.adlin.orin.modules.skill.component.AiEngineMcpClient;
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

    private ToolCallingKbStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ToolCallingKbStrategy(
                null, null, null, null, null, null, null, null, null, null,
                mcpServiceRepository, aiEngineMcpClient, new ObjectMapper());
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
    void executeMcpTool_missingToolName_returnsHintWithoutCallingAiEngine() {
        when(mcpServiceRepository.findById(42L))
                .thenReturn(Optional.of(service(McpService.McpStatus.CONNECTED)));

        String result = strategy.executeMcpTool(42L, "Fetch", Map.of("arguments", Map.of("url", "x")));

        assertTrue(result.contains("toolName"));
        verifyNoInteractions(aiEngineMcpClient);
    }

    @Test
    void executeMcpTool_serviceNotConnected_returnsHintWithoutCallingAiEngine() {
        when(mcpServiceRepository.findById(42L))
                .thenReturn(Optional.of(service(McpService.McpStatus.DISCONNECTED)));

        String result = strategy.executeMcpTool(42L, "Fetch",
                Map.of("toolName", "fetch", "arguments", Map.of("url", "x")));

        assertTrue(result.contains("未连接"));
        verifyNoInteractions(aiEngineMcpClient);
    }

    @Test
    void executeMcpTool_success_delegatesToolNameAndArgumentsToAiEngine() {
        when(mcpServiceRepository.findById(42L))
                .thenReturn(Optional.of(service(McpService.McpStatus.CONNECTED)));
        when(aiEngineMcpClient.callTool(eq(42L), eq("fetch"), anyMap()))
                .thenReturn("{\"status\":200}");

        String result = strategy.executeMcpTool(42L, "Fetch",
                Map.of("toolName", "fetch", "arguments", Map.of("url", "https://example.com")));

        assertEquals("{\"status\":200}", result);

        ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiEngineMcpClient).callTool(eq(42L), eq("fetch"), argsCaptor.capture());
        assertEquals("https://example.com", argsCaptor.getValue().get("url"));
    }

    @Test
    void executeMcpTool_aiEngineFailure_returnsReadableError() {
        when(mcpServiceRepository.findById(42L))
                .thenReturn(Optional.of(service(McpService.McpStatus.CONNECTED)));
        when(aiEngineMcpClient.callTool(anyLong(), anyString(), anyMap()))
                .thenThrow(new AiEngineMcpClient.McpToolCallException("MCP 工具调用超时", null));

        String result = strategy.executeMcpTool(42L, "Fetch",
                Map.of("toolName", "fetch", "arguments", Map.of()));

        assertTrue(result.contains("执行失败"));
        assertTrue(result.contains("超时"));
    }
}
