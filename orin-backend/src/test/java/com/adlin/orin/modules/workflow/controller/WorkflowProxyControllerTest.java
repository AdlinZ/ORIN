package com.adlin.orin.modules.workflow.controller;

import com.adlin.orin.modules.apikey.service.GatewaySecretService;
import com.adlin.orin.modules.audit.service.AuditLogService;
import com.adlin.orin.modules.model.service.ModelConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link WorkflowProxyController}.
 *
 * No Spring context, no external services (Milvus / RabbitMQ / Neo4j).
 * Only the HTTP call to the Python AI Engine is faked via MockWebServer.
 */
class WorkflowProxyControllerTest {

    private static MockWebServer mockAiEngine;
    private static String mockBaseUrl;

    private WorkflowProxyController controller;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ModelConfigService modelConfigService;
    private AuditLogService auditLogService;
    private GatewaySecretService gatewaySecretService;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void startServer() throws IOException {
        mockAiEngine = new MockWebServer();
        mockAiEngine.start();
        mockBaseUrl = "http://localhost:" + mockAiEngine.getPort();
    }

    @AfterAll
    static void stopServer() throws IOException {
        mockAiEngine.shutdown();
    }

    @BeforeEach
    void setUp() {
        WebClient webClient = WebClient.builder()
                .baseUrl(mockBaseUrl)
                .build();

        // Pure mocks — no real service-layer logic exercised here
        modelConfigService = mock(ModelConfigService.class);
        auditLogService = mock(AuditLogService.class);
        gatewaySecretService = mock(GatewaySecretService.class);

        controller = new WorkflowProxyController(
                webClient,
                modelConfigService,
                auditLogService,
                gatewaySecretService
        );
    }

    private ObjectNode emptyDsl() {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode dsl = objectMapper.createObjectNode();
        dsl.set("nodes", objectMapper.createArrayNode());
        root.set("dsl", dsl);
        return root;
    }

    @Test
    void runWorkflowProxy_returnsSuccessAndSetsDslVersion() throws Exception {
        mockAiEngine.enqueue(new MockResponse()
                .setBody("{\"success\": true, \"status\": \"success\"}")
                .addHeader("Content-Type", "application/json"));

        ObjectNode root = emptyDsl();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");

        ResponseEntity<JsonNode> response = controller.runWorkflowProxy(root, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success").asBoolean()).isTrue();
        // DSL version must be injected by the controller
        assertThat(root.get("dsl").get("version").asText()).isEqualTo("1.0");
        // Audit must have been called
        verify(auditLogService, atLeastOnce()).logApiCall(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), anyInt(), anyLong(), anyInt(), anyInt(), anyDouble(),
                anyBoolean(), any(), any(), any(), any(), any());
    }

    @Test
    void runWorkflowProxy_returns500OnEngineFailure() throws Exception {
        mockAiEngine.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\": \"AI Engine failed\"}")
                .addHeader("Content-Type", "application/json"));

        ObjectNode root = emptyDsl();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");

        ResponseEntity<JsonNode> response = controller.runWorkflowProxy(root, request);

        // Error path must return 500
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void runWorkflowProxy_acceptsAuthorizationHeader() throws Exception {
        mockAiEngine.enqueue(new MockResponse()
                .setBody("{\"success\": true, \"status\": \"success\"}")
                .addHeader("Content-Type", "application/json"));

        ObjectNode root = emptyDsl();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.addHeader("Authorization", "Bearer test-token");

        ResponseEntity<JsonNode> response = controller.runWorkflowProxy(root, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
