package com.adlin.orin.modules.mcp;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.service.GatewaySecretService;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.collaboration.config.CollaborationOrchestrationMode;
import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.entity.CollaborationPackageEntity;
import com.adlin.orin.modules.collaboration.repository.CollabSubtaskRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.collaboration.service.CollaborationExecutor;
import com.adlin.orin.modules.collaboration.service.CollaborationRedisService;
import com.adlin.orin.config.WebConfig;
import com.adlin.orin.modules.endpoint.dto.ExecuteEndpointRequest;
import com.adlin.orin.modules.endpoint.dto.ExecuteEndpointResponse;
import com.adlin.orin.modules.endpoint.entity.AgentEndpoint;
import com.adlin.orin.modules.endpoint.entity.EndpointStatus;
import com.adlin.orin.modules.endpoint.repository.AgentEndpointRepository;
import com.adlin.orin.modules.endpoint.service.EndpointExecutionService;
import com.adlin.orin.modules.mcp.controller.McpStreamableHttpController;
import com.adlin.orin.modules.mcp.service.ExternalMcpAgentExecutionService;
import com.adlin.orin.modules.mcp.service.McpJsonRpcService;
import com.adlin.orin.modules.workflow.dsl.OrinWorkflowDslNormalizer;
import com.adlin.orin.modules.workflow.dto.WorkflowExecutionSubmissionResponse;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import com.adlin.orin.modules.workflow.repository.WorkflowRepository;
import com.adlin.orin.modules.workflow.service.WorkflowService;
import com.adlin.orin.security.ApiKeyAuthInterceptor;
import com.adlin.orin.security.ApiRateLimitInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpStreamableHttpTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @SuppressWarnings("unchecked")
    void webConfigWiresApiKeyInterceptorsForMcpAndGatewayV1Endpoints() {
        ApiKeyAuthInterceptor auth = mock(ApiKeyAuthInterceptor.class);
        ApiRateLimitInterceptor rateLimit = mock(ApiRateLimitInterceptor.class);
        WebConfig config = new WebConfig(auth, rateLimit);
        InterceptorRegistry registry = new InterceptorRegistry();

        config.addInterceptors(registry);

        List<Object> registrations = (List<Object>) ReflectionTestUtils.getField(registry, "registrations");
        assertThat(registrations).hasSizeGreaterThanOrEqualTo(2);
        List<String> authPatterns = (List<String>) ReflectionTestUtils.getField(registrations.get(0), "includePatterns");
        List<String> rateLimitPatterns = (List<String>) ReflectionTestUtils.getField(registrations.get(1), "includePatterns");
        assertThat(authPatterns).containsExactly("/api/v1/**", "/v1/mcp", "/v1/mcp/**",
                "/v1/chat/completions", "/v1/embeddings", "/v1/models",
                "/v1/endpoints/**");
        assertThat(rateLimitPatterns).containsExactly("/api/v1/**", "/v1/mcp", "/v1/mcp/**",
                "/v1/chat/completions", "/v1/embeddings", "/v1/models",
                "/v1/endpoints/**");
        assertThat(authPatterns).doesNotContain("/v1/**");
        assertThat(rateLimitPatterns).doesNotContain("/v1/**");
    }

    @Test
    void mcpEndpointRequiresClientAccessApiKeyThroughInterceptor() throws Exception {
        McpJsonRpcService json = mock(McpJsonRpcService.class);
        McpStreamableHttpController controller = new McpStreamableHttpController(json);
        GatewaySecretService gatewaySecretService = mock(GatewaySecretService.class);
        ApiKeyAuthInterceptor auth = new ApiKeyAuthInterceptor(gatewaySecretService, mapper);
        GatewaySecret valid = secret("1");
        valid.setSecretType(GatewaySecret.SecretType.CLIENT_ACCESS);
        when(gatewaySecretService.validateClientAccessSecret("sk-orin-valid")).thenReturn(Optional.of(valid));
        when(gatewaySecretService.validateClientAccessSecret("sk-orin-invalid")).thenReturn(Optional.empty());
        when(json.handle(any(), same(valid))).thenReturn(Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "result", Map.of("serverInfo", Map.of("name", "ORIN"))));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(auth)
                .build();

        mvc.perform(post("/v1/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/v1/mcp")
                        .header("Authorization", "Bearer sk-orin-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/v1/mcp")
                        .header("Authorization", "Bearer eyJ.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/v1/mcp")
                        .header("Authorization", "Bearer sk-orin-valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.serverInfo.name").value("ORIN"));
        verify(json).handle(any(), same(valid));
    }

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
        GatewaySecret providerSecret = secret("1");
        providerSecret.setSecretType(GatewaySecret.SecretType.PROVIDER_CREDENTIAL);
        assertThat(controller.post(Map.of(), "http://localhost:3000", request(providerSecret)).getStatusCode().value())
                .isEqualTo(401);
        assertThat(controller.get("http://localhost:3000").getStatusCode().value()).isEqualTo(405);
    }

    @Test
    void controllerReturnsAcceptedForInitializedNotificationWithBlankOrigin() {
        McpJsonRpcService json = mock(McpJsonRpcService.class);
        McpStreamableHttpController controller = new McpStreamableHttpController(json);
        ReflectionTestUtils.setField(controller, "allowedOrigins", "http://localhost:3000");
        GatewaySecret secret = secret("1");
        Map<String, Object> body = req(1, "notifications/initialized", Map.of());
        when(json.handle(body, secret)).thenReturn(null);

        assertThat(controller.post(body, null, request(secret)).getStatusCode().value()).isEqualTo(202);
        verify(json).handle(body, secret);
    }

    @Test
    void jsonRpcRejectsBatchAndNonnumericOwnerDoesNotExposeTools() {
        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        AgentEndpointRepository endpointRepo = mock(AgentEndpointRepository.class);
        EndpointExecutionService endpointExec = mock(EndpointExecutionService.class);
        WorkflowService workflowService = mock(WorkflowService.class);
        AuditHelper auditHelper = mock(AuditHelper.class);
        McpJsonRpcService service = new McpJsonRpcService(workflowRepo, endpointRepo, endpointExec,
                workflowService, new OrinWorkflowDslNormalizer(), auditHelper, mapper);

        Map<String, Object> batch = service.handle(List.of(req(1, "tools/list", Map.of())), secret("1"));
        assertThat(((Map<?, ?>) batch.get("error")).get("code")).isEqualTo(-32600);
        assertThat(String.valueOf(((Map<?, ?>) batch.get("error")).get("message"))).contains("batch is not supported");

        Map<String, Object> list = service.handle(req(2, "tools/list", Map.of()), secret("not-number"));
        List<?> tools = (List<?>) ((Map<?, ?>) list.get("result")).get("tools");
        assertThat(tools).isEmpty();
        verifyNoInteractions(workflowRepo, endpointRepo, endpointExec, workflowService);
        verify(auditHelper).log(eq("not-number"), eq("MCP_TOOLS_LIST"), eq("/v1/mcp"),
                contains("secretId=gsec-not-number"), eq(true), isNull());
    }

    @Test
    void jsonRpcListsPublishedEndpointsAndRejectsUnauthorizedKeys() {
        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        AgentEndpointRepository endpointRepo = mock(AgentEndpointRepository.class);
        EndpointExecutionService endpointExec = mock(EndpointExecutionService.class);
        WorkflowService workflowService = mock(WorkflowService.class);
        AuditHelper auditHelper = mock(AuditHelper.class);
        McpJsonRpcService service = new McpJsonRpcService(workflowRepo, endpointRepo, endpointExec,
                workflowService, new OrinWorkflowDslNormalizer(), auditHelper, mapper);

        // Published endpoint with API key gsec-1 in allowed list
        AgentEndpoint ep = endpoint("ep_001", "agent-a", "gsec-1");
        when(endpointRepo.findAll()).thenReturn(List.of(ep));

        // tools/list: lists endpoints where API key is in allowedApiKeyIds
        Map<String, Object> list = service.handle(req(1, "tools/list", Map.of()), secret("1"));
        List<?> tools = (List<?>) ((Map<?, ?>) list.get("result")).get("tools");
        assertThat(tools).hasSize(1);
        String toolName = String.valueOf(((Map<?, ?>) tools.get(0)).get("name"));
        assertThat(toolName).startsWith("endpoint.");

        // A different CLIENT_ACCESS key must not discover this endpoint through
        // tools/list.  Tool discovery is part of the Endpoint access boundary.
        Map<String, Object> unauthorizedList = service.handle(req(11, "tools/list", Map.of()), secret("other"));
        List<?> unauthorizedTools = (List<?>) ((Map<?, ?>) unauthorizedList.get("result")).get("tools");
        assertThat(unauthorizedTools).isEmpty();

        // tools/call: same key can call
        when(endpointRepo.findById("ep_001")).thenReturn(Optional.of(ep));
        ExecuteEndpointResponse resp = ExecuteEndpointResponse.builder()
                .runId("run-1").traceId("trace-1").status("COMPLETED").output("hello").build();
        when(endpointExec.execute(eq("ep_001"), any(ExecuteEndpointRequest.class), any(GatewaySecret.class)))
                .thenReturn(resp);

        Map<String, Object> called = service.handle(req(2, "tools/call",
                Map.of("name", toolName, "arguments", Map.of("input", "hello"))), secret("1"));
        Map<?, ?> result = (Map<?, ?>) called.get("result");
        assertThat(result.get("isError")).isEqualTo(false);
        String content = String.valueOf(((Map<?, ?>) ((List<?>) result.get("content")).get(0)).get("text"));
        assertThat(content).contains("hello");
    }

    @Test
    void jsonRpcCallsPublishedEndpointAndReturnsErrorsAsToolErrors() {
        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        AgentEndpointRepository endpointRepo = mock(AgentEndpointRepository.class);
        EndpointExecutionService endpointExec = mock(EndpointExecutionService.class);
        WorkflowService workflowService = mock(WorkflowService.class);
        AuditHelper auditHelper = mock(AuditHelper.class);
        McpJsonRpcService service = new McpJsonRpcService(workflowRepo, endpointRepo, endpointExec,
                workflowService, new OrinWorkflowDslNormalizer(), auditHelper, mapper);

        AgentEndpoint ep = endpoint("ep_001", "agent-a", "gsec-1");
        when(endpointRepo.findAll()).thenReturn(List.of(ep));
        when(endpointRepo.findById("ep_001")).thenReturn(Optional.of(ep));

        // tools/list
        Map<String, Object> list = service.handle(req(1, "tools/list", Map.of()), secret("1"));
        String toolName = String.valueOf(((Map<?, ?>) ((List<?>) ((Map<?, ?>) list.get("result")).get("tools")).get(0)).get("name"));

        // Successful call
        ExecuteEndpointResponse okResp = ExecuteEndpointResponse.builder()
                .runId("run-1").traceId("trace-ok").status("COMPLETED").output("agent ok").build();
        when(endpointExec.execute(eq("ep_001"), any(ExecuteEndpointRequest.class), any(GatewaySecret.class)))
                .thenReturn(okResp);

        Map<String, Object> called = service.handle(req(2, "tools/call",
                Map.of("name", toolName, "arguments", Map.of("input", "hello"))), secret("1"));
        Map<?, ?> result = (Map<?, ?>) called.get("result");
        assertThat(result.get("isError")).isEqualTo(false);
        String text = String.valueOf(((Map<?, ?>) ((List<?>) result.get("content")).get(0)).get("text"));
        assertThat(text).contains("agent ok");

        // Missing input
        Map<String, Object> missingInput = service.handle(req(3, "tools/call",
                Map.of("name", toolName, "arguments", Map.of())), secret("1"));
        assertThat(((Map<?, ?>) missingInput.get("error")).get("code")).isEqualTo(-32602);
        assertThat(String.valueOf(((Map<?, ?>) missingInput.get("error")).get("message"))).isEqualTo("input is required");

        // Execution failure
        when(endpointExec.execute(eq("ep_001"), any(ExecuteEndpointRequest.class), any(GatewaySecret.class)))
                .thenThrow(new RuntimeException("boom"));
        Map<String, Object> failed = service.handle(req(4, "tools/call",
                Map.of("name", toolName, "arguments", Map.of("input", "fail"))), secret("1"));
        Map<?, ?> failedResult = (Map<?, ?>) failed.get("result");
        assertThat(failedResult.get("isError")).isEqualTo(true);
        assertThat(String.valueOf(((Map<?, ?>) ((List<?>) failedResult.get("content")).get(0)).get("text"))).contains("boom");

        // Audit verifications
        verify(auditHelper).log(eq("1"), eq("MCP_TOOLS_LIST"), eq("/v1/mcp"),
                contains("method=tools/list;secretId=gsec-1"), eq(true), isNull());
        verify(auditHelper).log(eq("1"), eq("MCP_TOOLS_CALL"), eq("/v1/mcp"),
                argThat(detail -> detail.contains("toolName=" + toolName)
                        && detail.contains("traceId=trace-ok")),
                eq(true), isNull());
        verify(auditHelper).log(eq("1"), eq("MCP_TOOLS_CALL"), eq("/v1/mcp"),
                argThat(detail -> detail.contains("errorCode=-32602")),
                eq(false), eq("-32602"));
    }

    @Test
    void jsonRpcRejectsUnpublishedAgentsAndInvalidToolNames() {
        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        AgentEndpointRepository endpointRepo = mock(AgentEndpointRepository.class);
        EndpointExecutionService endpointExec = mock(EndpointExecutionService.class);
        WorkflowService workflowService = mock(WorkflowService.class);
        AuditHelper auditHelper = mock(AuditHelper.class);
        McpJsonRpcService service = new McpJsonRpcService(workflowRepo, endpointRepo, endpointExec,
                workflowService, new OrinWorkflowDslNormalizer(), auditHelper, mapper);

        // Invalid base64 tool name
        Map<String, Object> invalid = service.handle(req(1, "tools/call",
                Map.of("name", "agent.%%%", "arguments", Map.of("input", "hello"))), secret("1"));
        assertThat(((Map<?, ?>) invalid.get("error")).get("code")).isEqualTo(-32602);
        assertThat(String.valueOf(((Map<?, ?>) invalid.get("error")).get("message"))).isEqualTo("Invalid tool name");

        // Agent exists but not published as endpoint → no endpoint found for this agent
        when(endpointRepo.findByAgentId("agent-hidden")).thenReturn(List.of());
        Map<String, Object> notPublished = service.handle(req(2, "tools/call",
                Map.of("name", "agent.YWdlbnQtaGlkZGVu", "arguments", Map.of("input", "hello"))), secret("1"));
        assertThat(((Map<?, ?>) notPublished.get("error")).get("code")).isEqualTo(-32003);
        assertThat(String.valueOf(((Map<?, ?>) notPublished.get("error")).get("message")))
                .contains("not published");
        verifyNoInteractions(endpointExec);
    }

    @Test
    void jsonRpcListsAndCallsOwnedExposedWorkflows() {
        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        AgentEndpointRepository endpointRepo = mock(AgentEndpointRepository.class);
        EndpointExecutionService endpointExec = mock(EndpointExecutionService.class);
        WorkflowService workflowService = mock(WorkflowService.class);
        AuditHelper auditHelper = mock(AuditHelper.class);
        McpJsonRpcService service = new McpJsonRpcService(workflowRepo, endpointRepo, endpointExec,
                workflowService, new OrinWorkflowDslNormalizer(), auditHelper, mapper);
        when(endpointRepo.findAll()).thenReturn(List.of());
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
        assertThat(tools).hasSize(1); // only workflow, no endpoints configured
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
        verify(auditHelper).log(eq("1"), eq("MCP_TOOLS_CALL"), eq("/v1/mcp"),
                argThat(detail -> detail.contains("toolName=workflow.42") && detail.contains("traceId=trace-42")),
                eq(true), isNull());
    }

    @Test
    void jsonRpcForbidsWorkflowFromAnotherOwnerAndDefaultsSchemaToQuery() {
        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        AgentEndpointRepository endpointRepo = mock(AgentEndpointRepository.class);
        EndpointExecutionService endpointExec = mock(EndpointExecutionService.class);
        WorkflowService workflowService = mock(WorkflowService.class);
        AuditHelper auditHelper = mock(AuditHelper.class);
        McpJsonRpcService service = new McpJsonRpcService(workflowRepo, endpointRepo, endpointExec,
                workflowService, new OrinWorkflowDslNormalizer(), auditHelper, mapper);
        when(endpointRepo.findAll()).thenReturn(List.of());
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

        ExternalMcpAgentExecutionService.ExecutionResult result =
                service.execute(agent("agent-a", 1L, true), "hello", "ctx", 99, "1");
        assertThat(result.text()).isEqualTo("done");
        assertThat(result.traceId()).isNotBlank();
        assertThat(result.packageId()).startsWith("mcp_");

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
        return GatewaySecret.builder()
                .secretId("gsec-" + userId)
                .secretType(GatewaySecret.SecretType.CLIENT_ACCESS)
                .userId(userId)
                .build();
    }

    private AgentMetadata agent(String id, Long owner, boolean exposed) {
        return AgentMetadata.builder()
                .agentId(id).name("Agent " + id).description("Test agent")
                .ownerUserId(owner).mcpExposed(exposed).providerType("local").build();
    }

    private AgentEndpoint endpoint(String id, String agentId, String... allowedKeyIds) {
        String config;
        try {
            config = mapper.writeValueAsString(Map.of("allowedApiKeyIds", List.of(allowedKeyIds)));
        } catch (Exception e) {
            config = "{}";
        }
        return AgentEndpoint.builder()
                .id(id).agentId(agentId).agentVersionId("ver-1")
                .name("Endpoint " + id).endpointType(com.adlin.orin.modules.endpoint.entity.EndpointType.REST_API)
                .status(EndpointStatus.ACTIVE).endpointPath("/v1/endpoints/" + id + "/run")
                .config(config).createdBy("1").build();
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
