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
import com.adlin.orin.modules.workflow.dsl.OrinWorkflowDslNormalizer;
import com.adlin.orin.modules.workflow.dto.WorkflowExecutionSubmissionResponse;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import com.adlin.orin.modules.workflow.repository.WorkflowRepository;
import com.adlin.orin.modules.workflow.service.WorkflowService;
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
        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        ExternalMcpAgentExecutionService exec = mock(ExternalMcpAgentExecutionService.class);
        WorkflowService workflowService = mock(WorkflowService.class);
        McpJsonRpcService service = new McpJsonRpcService(repo, workflowRepo, exec, workflowService, new OrinWorkflowDslNormalizer());
        AgentMetadata agent = agent("agent-a", 1L, true);
        when(repo.findByOwnerUserIdAndMcpExposedTrue(1L)).thenReturn(List.of(agent));
        Map<String, Object> list = service.handle(req(1, "tools/list", Map.of()), secret("1"));
        List<?> tools = (List<?>) ((Map<?, ?>) list.get("result")).get("tools");
        assertThat(tools).hasSize(1);
        String toolName = String.valueOf(((Map<?, ?>) tools.get(0)).get("name"));
        assertThat(toolName).startsWith("agent.");

        when(repo.findById("agent-a")).thenReturn(Optional.of(agent));
        Map<String, Object> forbidden = service.handle(req(2, "tools/call",
                Map.of("name", toolName, "arguments", Map.of("message", "hello"))), secret("2"));
        assertThat(String.valueOf(((Map<?, ?>) forbidden.get("error")).get("message"))).isEqualTo("Forbidden");
    }

    @Test
    void jsonRpcListsAndCallsOwnedExposedWorkflows() {
        AgentMetadataRepository repo = mock(AgentMetadataRepository.class);
        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        ExternalMcpAgentExecutionService exec = mock(ExternalMcpAgentExecutionService.class);
        WorkflowService workflowService = mock(WorkflowService.class);
        McpJsonRpcService service = new McpJsonRpcService(repo, workflowRepo, exec, workflowService, new OrinWorkflowDslNormalizer());
        when(repo.findByOwnerUserIdAndMcpExposedTrue(1L)).thenReturn(List.of(agent("agent-a", 1L, true)));
        WorkflowEntity workflow = workflow(42L, 1L, true, List.of(Map.of("name", "topic", "type", "string", "required", true)));
        when(workflowRepo.findByOwnerUserIdAndMcpExposedTrue(1L)).thenReturn(List.of(workflow));
        when(workflowRepo.findById(42L)).thenReturn(Optional.of(workflow));
        when(workflowService.submitWorkflowExecution(eq(42L), eq(Map.of("topic", "MCP")), any(), eq("1"), eq("external_mcp")))
                .thenReturn(WorkflowExecutionSubmissionResponse.builder()
                        .taskId("task-42")
                        .workflowId(42L)
                        .workflowInstanceId(99L)
                        .traceId("trace-42")
                        .status(com.adlin.orin.modules.task.entity.TaskEntity.TaskStatus.QUEUED)
                        .statusUrl("/api/v1/workflow-tasks/task-42")
                        .build());

        Map<String, Object> list = service.handle(req(1, "tools/list", Map.of()), secret("1"));
        List<?> tools = (List<?>) ((Map<?, ?>) list.get("result")).get("tools");
        assertThat(tools).hasSize(2);
        assertThat(tools.stream().map(t -> String.valueOf(((Map<?, ?>) t).get("name"))))
                .anyMatch(name -> name.startsWith("agent."));
        Map<?, ?> tool = tools.stream()
                .map(t -> (Map<?, ?>) t)
                .filter(t -> "workflow.42".equals(t.get("name")))
                .findFirst()
                .orElseThrow();
        assertThat(tool.get("name")).isEqualTo("workflow.42");
        Map<?, ?> schema = (Map<?, ?>) tool.get("inputSchema");
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        List<?> required = (List<?>) schema.get("required");
        assertThat(properties.keySet().stream().map(String::valueOf)).contains("topic");
        assertThat(required.stream().map(String::valueOf)).contains("topic");

        Map<String, Object> called = service.handle(req(2, "tools/call",
                Map.of("name", "workflow.42", "arguments", Map.of("topic", "MCP"))), secret("1"));
        Map<?, ?> result = (Map<?, ?>) called.get("result");
        assertThat(result.get("isError")).isEqualTo(false);
        assertThat(String.valueOf(((Map<?, ?>) ((List<?>) result.get("content")).get(0)).get("text")))
                .contains("task-42", "trace-42", "/api/v1/workflow-tasks/task-42");
    }

    @Test
    void jsonRpcForbidsWorkflowFromAnotherOwnerAndDefaultsSchemaToQuery() {
        AgentMetadataRepository repo = mock(AgentMetadataRepository.class);
        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        ExternalMcpAgentExecutionService exec = mock(ExternalMcpAgentExecutionService.class);
        WorkflowService workflowService = mock(WorkflowService.class);
        McpJsonRpcService service = new McpJsonRpcService(repo, workflowRepo, exec, workflowService, new OrinWorkflowDslNormalizer());
        WorkflowEntity workflow = workflow(43L, 1L, true, List.of());
        when(workflowRepo.findByOwnerUserIdAndMcpExposedTrue(1L)).thenReturn(List.of(workflow));
        when(workflowRepo.findById(43L)).thenReturn(Optional.of(workflow));

        Map<String, Object> list = service.handle(req(1, "tools/list", Map.of()), secret("1"));
        Map<?, ?> schema = (Map<?, ?>) ((Map<?, ?>) ((List<?>) ((Map<?, ?>) list.get("result")).get("tools")).get(0)).get("inputSchema");
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertThat(properties.keySet().stream().map(String::valueOf)).contains("query");

        Map<String, Object> forbidden = service.handle(req(2, "tools/call",
                Map.of("name", "workflow.43", "arguments", Map.of("query", "hello"))), secret("2"));
        assertThat(String.valueOf(((Map<?, ?>) forbidden.get("error")).get("message"))).isEqualTo("Forbidden");
        verifyNoInteractions(workflowService);
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

    private WorkflowEntity workflow(Long id, Long owner, boolean exposed, List<Map<String, Object>> variables) {
        return WorkflowEntity.builder()
                .id(id)
                .workflowName("Workflow " + id)
                .description("Test workflow")
                .ownerUserId(owner)
                .mcpExposed(exposed)
                .status(WorkflowEntity.WorkflowStatus.ACTIVE)
                .workflowDefinition(Map.of(
                        "version", "orin.workflow.v1",
                        "kind", "workflow",
                        "graph", Map.of(
                                "nodes", List.of(Map.of(
                                        "id", "start_1",
                                        "type", "start",
                                        "data", Map.of("variables", variables))),
                                "edges", List.of())))
                .build();
    }

    private Map<String, Object> req(Object id, String method, Map<String, Object> params) {
        return Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params);
    }
}
