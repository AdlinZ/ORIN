package com.adlin.orin.modules.mcp;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.collaboration.config.CollaborationOrchestrationMode;
import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.entity.CollaborationPackageEntity;
import com.adlin.orin.modules.collaboration.repository.CollabSubtaskRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.collaboration.service.CollaborationExecutor;
import com.adlin.orin.modules.collaboration.service.CollaborationRedisService;
import com.adlin.orin.modules.mcp.controller.McpStreamableHttpController;
import com.adlin.orin.modules.mcp.service.ExternalMcpAgentExecutionService;
import com.adlin.orin.modules.mcp.service.McpJsonRpcService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class McpStreamableHttpTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void controllerRejectsMissingApiKeyAndBadOrigin() {
        McpJsonRpcService json = mock(McpJsonRpcService.class);
        McpStreamableHttpController controller = new McpStreamableHttpController(json);
        ReflectionTestUtils.setField(controller, "allowedOrigins", "http://localhost:3000");

        assertThat(controller.post(Map.of("jsonrpc", "2.0"), null, new MockHttpServletRequest()).getStatusCode().value())
                .isEqualTo(401);
        assertThat(controller.post(Map.of(), "https://evil.example", request(secret("1"))).getStatusCode().value())
                .isEqualTo(403);
        ReflectionTestUtils.setField(controller, "allowedOrigins", "");
        assertThat(controller.post(Map.of(), "http://localhost:3000", request(secret("1"))).getStatusCode().value())
                .isEqualTo(403);
        ReflectionTestUtils.setField(controller, "allowedOrigins", "http://localhost:3000");
        assertThat(controller.get("http://localhost:3000").getStatusCode().value()).isEqualTo(405);
    }

    @Test
    void jsonRpcListsOwnedExposedAgentsAndForbidsOtherOwners() {
        AgentMetadataRepository repo = mock(AgentMetadataRepository.class);
        ExternalMcpAgentExecutionService exec = mock(ExternalMcpAgentExecutionService.class);
        McpJsonRpcService service = new McpJsonRpcService(repo, exec);
        AgentMetadata agent = agent("agent-a", 1L, true);
        when(repo.findByOwnerUserIdAndMcpExposedTrue(1L)).thenReturn(List.of(agent));
        Map<String, Object> list = service.handle(req(1, "tools/list", Map.of()), secret("1"));
        List<?> tools = (List<?>) ((Map<?, ?>) list.get("result")).get("tools");
        assertThat(tools).hasSize(1);
        String toolName = String.valueOf(((Map<?, ?>) tools.get(0)).get("name"));

        when(repo.findById("agent-a")).thenReturn(Optional.of(agent));
        Map<String, Object> forbidden = service.handle(req(2, "tools/call",
                Map.of("name", toolName, "arguments", Map.of("message", "hello"))), secret("2"));
        assertThat(String.valueOf(((Map<?, ?>) forbidden.get("error")).get("message"))).isEqualTo("Forbidden");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LANGGRAPH_MQ", "JAVA_NATIVE"})
    void externalExecutionPersistsSourceForMqAndFallback(String mode) throws Exception {
        CollaborationPackageRepository packages = mock(CollaborationPackageRepository.class);
        CollabSubtaskRepository subtasks = mock(CollabSubtaskRepository.class);
        CollaborationExecutor executor = mock(CollaborationExecutor.class);
        CollaborationRedisService redis = mock(CollaborationRedisService.class);
        CollaborationOrchestrationMode orchestration = new CollaborationOrchestrationMode();
        orchestration.setMode(mode);
        orchestration.setMqForSequential(true);
        when(executor.executeSubtask(any(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture("done"));
        when(subtasks.findByPackageIdAndSubTaskId(anyString(), eq("mcp_call")))
                .thenAnswer(inv -> Optional.of(CollabSubtaskEntity.builder().packageId(inv.getArgument(0)).subTaskId("mcp_call").build()));
        ExternalMcpAgentExecutionService service = new ExternalMcpAgentExecutionService(
                packages, subtasks, executor, orchestration, redis, mapper);

        assertThat(service.execute(agent("agent-a", 1L, true), "hello", "ctx", 99, "1")).isEqualTo("done");

        ArgumentCaptor<CollaborationPackageEntity> pkg = ArgumentCaptor.forClass(CollaborationPackageEntity.class);
        ArgumentCaptor<CollabSubtaskEntity> sub = ArgumentCaptor.forClass(CollabSubtaskEntity.class);
        verify(packages).save(pkg.capture());
        verify(subtasks, atLeastOnce()).save(sub.capture());
        String expectedPath = "LANGGRAPH_MQ".equals(mode) ? "mq" : "fallback";
        assertThat(pkg.getValue().getSharedContext()).contains("\"source\":\"external_mcp\"", expectedPath);
        assertThat(pkg.getValue().getStrategy()).contains("STATIC_ONLY", "agent-a");
        assertThat(sub.getAllValues().get(0).getInputData()).contains("\"preferred_agent_id\":\"agent-a\"", expectedPath);
        assertThat(sub.getAllValues().get(sub.getAllValues().size() - 1).getOutputData()).contains("\"source\":\"external_mcp\"");
        verify(redis).updateContextField(anyString(), eq("source"), eq("external_mcp"));
    }

    private MockHttpServletRequest request(GatewaySecret secret) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("apiKey", secret);
        return request;
    }

    private GatewaySecret secret(String userId) {
        return GatewaySecret.builder().secretId("sk-" + userId).userId(userId).build();
    }

    private AgentMetadata agent(String id, Long owner, boolean exposed) {
        return AgentMetadata.builder()
                .agentId(id).name("Agent " + id).description("Test agent")
                .ownerUserId(owner).mcpExposed(exposed).providerType("local").build();
    }

    private Map<String, Object> req(Object id, String method, Map<String, Object> params) {
        return Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params);
    }
}
