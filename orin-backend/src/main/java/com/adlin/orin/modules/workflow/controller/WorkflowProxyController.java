package com.adlin.orin.modules.workflow.controller;

import com.adlin.orin.modules.model.entity.ModelConfig;
import com.adlin.orin.modules.model.service.ModelConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.adlin.orin.modules.audit.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Duration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/workflow")
public class WorkflowProxyController {

    private final WebClient aiEngineWebClient;
    private final ModelConfigService modelConfigService;
    private final AuditLogService auditLogService;

    public WorkflowProxyController(@Qualifier("aiEngineWebClient") WebClient aiEngineWebClient,
            ModelConfigService modelConfigService,
            AuditLogService auditLogService) {
        this.aiEngineWebClient = aiEngineWebClient;
        this.modelConfigService = modelConfigService;
        this.auditLogService = auditLogService;
    }

    /**
     * Proxies the workflow execution request to the Python AI Engine.
     * Enriches the DSL with backend-stored API keys and endpoints.
     */
    @PostMapping("/run")
    public ResponseEntity<JsonNode> runWorkflowProxy(@RequestBody JsonNode workflowRequest,
            HttpServletRequest request) {
        log.info("Received workflow execution request. Enriching with model credentials and proxying...");

        long startTime = System.currentTimeMillis();
        String userId = "system";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            userId = auth.getName();
        }

        // Enrich the DSL with credentials from ModelConfig before sending
        enrichDslWithCredentials(workflowRequest);

        ResponseEntity<JsonNode> response;
        try {
            response = aiEngineWebClient.post()
                    .uri("/api/v1/run")
                    .bodyValue(workflowRequest)
                    .retrieve()
                    .toEntity(JsonNode.class)
                    .block(Duration.ofSeconds(60));

            long duration = System.currentTimeMillis() - startTime;

            // Log to Audit System
            if (response != null && response.getBody() != null) {
                logExecutionToAudit(userId, workflowRequest, response.getBody(), response.getStatusCode().value(),
                        duration, request);
            }

            return response;
        } catch (WebClientResponseException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("AI Engine returned error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());

            auditLogService.logApiCall(userId, null, "WorkflowEngine", "WORKFLOW",
                    "/api/v1/workflow/run", "POST", "HybridExecutor", request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    "DSL Proxy", e.getResponseBodyAsString(), e.getStatusCode().value(),
                    duration, 0, 0, 0.0, false, e.getMessage());

            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAs(JsonNode.class));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Unexpected error during AI Engine proxy: {}", e.getMessage());

            auditLogService.logApiCall(userId, null, "WorkflowEngine", "WORKFLOW",
                    "/api/v1/workflow/run", "POST", "HybridExecutor", request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    "DSL Proxy", null, 500,
                    duration, 0, 0, 0.0, false, e.getMessage());

            return ResponseEntity.status(500).build();
        }
    }

    private void logExecutionToAudit(String userId, JsonNode request, JsonNode response, int statusCode, long duration,
            HttpServletRequest httpRequest) {
        try {
            String status = response.has("status") ? response.get("status").asText() : "unknown";
            // Correct status check (Python engine uses 'success')
            boolean success = "success".equals(status) || "succeeded".equals(status) || "partial".equals(status);

            int totalTokens = 0;
            if (response.has("trace") && response.get("trace").isArray()) {
                for (JsonNode node : response.get("trace")) {
                    if (node.has("outputs") && node.get("outputs").isObject()) {
                        JsonNode outputs = node.get("outputs");
                        if (outputs.has("tokens_used")) {
                            totalTokens += outputs.get("tokens_used").asInt(0);
                        }
                    }
                }
            }

            auditLogService.logApiCall(
                    userId,
                    null,
                    "WorkflowEngine",
                    "WORKFLOW",
                    "/api/v1/workflow/run",
                    "POST",
                    "HybridExecutor",
                    httpRequest.getRemoteAddr(),
                    httpRequest.getHeader("User-Agent"),
                    request.toString(), // Full Request JSON
                    response.toString(), // Full Response JSON
                    statusCode,
                    duration,
                    totalTokens, 0, 0.0,
                    success,
                    response.has("error") ? response.get("error").asText() : null,
                    request.has("id") ? request.get("id").asText() : null);
        } catch (Exception e) {
            log.warn("Failed to record workflow audit log: {}", e.getMessage());
        }
    }

    /**
     * Iterates through the DSL nodes and injects API keys/Base URLs from the Java
     * database.
     */
    private void enrichDslWithCredentials(JsonNode root) {
        if (!(root instanceof ObjectNode))
            return;
        ObjectNode requestBody = (ObjectNode) root;

        if (!requestBody.has("dsl") || !requestBody.get("dsl").has("nodes")) {
            log.warn("Request body missing DSL nodes path");
            return;
        }
        JsonNode nodes = requestBody.get("dsl").get("nodes");
        if (!nodes.isArray())
            return;

        ModelConfig config = modelConfigService.getConfig();
        log.info("Enriching {} nodes with credentials from DB", nodes.size());

        for (JsonNode node : nodes) {
            String nodeType = node.has("type") ? node.get("type").asText() : "";
            if ("llm".equals(nodeType) && node.has("data")) {
                ObjectNode data = (ObjectNode) node.get("data");

                String provider = "OpenAI"; // Default
                if (data.has("model") && data.get("model").has("provider")) {
                    provider = data.get("model").get("provider").asText();
                }

                String modelName = "";
                if (data.has("model") && data.get("model").has("name")) {
                    modelName = data.get("model").get("name").asText();
                }

                log.info("Processing LLM node {} with provider: {}, model: {}",
                        node.get("id").asText(), provider, modelName);

                // Inject API Key and Base URL based on provider stored in Java DB
                // Logic: If provider is OpenAI but we have SiliconFlow/Dify configured,
                // and the node is missing a key, we inject the available one as a fallback.

                boolean injected = false;

                if ("SiliconFlow".equalsIgnoreCase(provider) || ("OpenAI".equalsIgnoreCase(provider)
                        && config.getSiliconFlowApiKey() != null && !config.getSiliconFlowApiKey().isEmpty())) {
                    if (config.getSiliconFlowApiKey() != null && !config.getSiliconFlowApiKey().isEmpty()) {
                        data.put("api_key", config.getSiliconFlowApiKey());
                        data.put("base_url", config.getSiliconFlowEndpoint());
                        log.info("Injected SiliconFlow credentials for node {}", node.get("id").asText());
                        injected = true;
                    }
                }

                if (!injected && ("Dify".equalsIgnoreCase(provider) || "OpenAI".equalsIgnoreCase(provider))) {
                    if (config.getDifyApiKey() != null && !config.getDifyApiKey().isEmpty()) {
                        data.put("api_key", config.getDifyApiKey());
                        data.put("base_url", config.getDifyEndpoint());
                        log.info("Injected Dify credentials for node {}", node.get("id").asText());
                        injected = true;
                    }
                }
            }
        }
    }
}
