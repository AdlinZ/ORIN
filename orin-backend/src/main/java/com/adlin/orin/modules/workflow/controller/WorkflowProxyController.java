package com.adlin.orin.modules.workflow.controller;

import com.adlin.orin.modules.apikey.service.GatewaySecretService;
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

import org.slf4j.MDC;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/workflow")
public class WorkflowProxyController {

    private final WebClient aiEngineWebClient;
    private final ModelConfigService modelConfigService;
    private final AuditLogService auditLogService;
    private final GatewaySecretService gatewaySecretService;

    public WorkflowProxyController(@Qualifier("aiEngineWebClient") WebClient aiEngineWebClient,
            ModelConfigService modelConfigService,
            AuditLogService auditLogService,
            GatewaySecretService gatewaySecretService) {
        this.aiEngineWebClient = aiEngineWebClient;
        this.modelConfigService = modelConfigService;
        this.auditLogService = auditLogService;
        this.gatewaySecretService = gatewaySecretService;
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

        // Extract traceId from MDC for propagation
        String traceId = MDC.get("traceId");

        // Enrich the DSL with credentials from ModelConfig before sending
        enrichDslWithCredentials(workflowRequest);

        ResponseEntity<JsonNode> response;
        try {
            response = aiEngineWebClient.post()
                    .uri("/api/v1/run")
                    .header("X-Trace-Id", traceId != null ? traceId : "")
                    .bodyValue(workflowRequest)
                    .retrieve()
                    .toEntity(JsonNode.class)
                    .block(Duration.ofSeconds(60));

            long duration = System.currentTimeMillis() - startTime;

            // Log to Audit System
            if (response != null && response.getBody() != null) {
                logExecutionToAudit(userId, workflowRequest, response.getBody(), response.getStatusCode().value(),
                        duration, request, traceId);
            }

            return response;
        } catch (WebClientResponseException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("AI Engine returned error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());

            auditLogService.logApiCall(userId, null, "WorkflowEngine", "WORKFLOW",
                    "/api/v1/workflow/run", "POST", "HybridExecutor", request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    "DSL Proxy", e.getResponseBodyAsString(), e.getStatusCode().value(),
                    duration, 0, 0, 0.0, false, e.getMessage(),
                    null, null, null, null, traceId);

            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAs(JsonNode.class));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Unexpected error during AI Engine proxy: {}", e.getMessage());

            auditLogService.logApiCall(userId, null, "WorkflowEngine", "WORKFLOW",
                    "/api/v1/workflow/run", "POST", "HybridExecutor", request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    "DSL Proxy", null, 500,
                    duration, 0, 0, 0.0, false, e.getMessage(),
                    null, null, null, null, traceId);

            return ResponseEntity.status(500).build();
        }
    }

    private void logExecutionToAudit(String userId, JsonNode request, JsonNode response, int statusCode, long duration,
            HttpServletRequest httpRequest, String traceId) {
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
                    request.has("id") ? request.get("id").asText() : null,
                    null, null, traceId);
        } catch (Exception e) {
            log.warn("Failed to record workflow audit log: {}", e.getMessage());
        }
    }

    private static final String CURRENT_DSL_VERSION = "1.0";

    /**
     * Iterates through the DSL nodes and injects API keys/Base URLs from the Java
     * database. Strictly matches provider - no incorrect fallback.
     *
     * Also sets the DSL version for protocol compatibility between Java backend and Python engine.
     *
     * Supported providers: SiliconFlow, Dify, Ollama, OpenAI
     * - SiliconFlow: Only injected when provider is explicitly "SiliconFlow"
     * - Dify: Only injected when provider is explicitly "Dify"
     * - Ollama: Only injected when provider is explicitly "Ollama"
     * - OpenAI: Only injected when provider is explicitly "OpenAI"
     */
    private void enrichDslWithCredentials(JsonNode root) {
        if (!(root instanceof ObjectNode))
            return;
        ObjectNode requestBody = (ObjectNode) root;

        if (!requestBody.has("dsl") || !requestBody.get("dsl").has("nodes")) {
            log.warn("Request body missing DSL nodes path");
            return;
        }
        ObjectNode dsl = (ObjectNode) requestBody.get("dsl");

        // Set DSL version for protocol compatibility
        dsl.put("version", CURRENT_DSL_VERSION);
        log.info("Set DSL version to {}", CURRENT_DSL_VERSION);

        JsonNode nodes = dsl.get("nodes");
        if (!nodes.isArray())
            return;

        ModelConfig config = modelConfigService.getConfig();
        log.info("Enriching {} nodes with credentials from DB", nodes.size());

        for (JsonNode node : nodes) {
            String nodeType = node.has("type") ? node.get("type").asText() : "";
            if ("llm".equals(nodeType) && node.has("data")) {
                ObjectNode data = (ObjectNode) node.get("data");

                String provider = null;
                if (data.has("model") && data.get("model").has("provider")) {
                    provider = data.get("model").get("provider").asText();
                }

                if (provider == null || provider.isEmpty()) {
                    log.warn("LLM node {} has no provider specified, skipping credential injection",
                            node.has("id") ? node.get("id").asText() : "unknown");
                    continue;
                }

                String modelName = "";
                if (data.has("model") && data.get("model").has("name")) {
                    modelName = data.get("model").get("name").asText();
                }

                log.info("Processing LLM node {} with provider: {}, model: {}",
                        node.get("id").asText(), provider, modelName);

                // Strict provider matching - no fallback to different providers
                if ("SiliconFlow".equalsIgnoreCase(provider)) {
                    var credentialOpt = gatewaySecretService.resolveProviderCredential("siliconflow");
                    if (credentialOpt.isEmpty() || credentialOpt.get().getApiKey() == null
                            || credentialOpt.get().getApiKey().isEmpty()) {
                        log.error("SiliconFlow provider configured but no API key available in ModelConfig");
                        continue;
                    }
                    data.put("api_key", credentialOpt.get().getApiKey());
                    data.put("base_url", credentialOpt.get().getBaseUrl() != null
                            ? credentialOpt.get().getBaseUrl()
                            : config.getSiliconFlowEndpoint());
                    log.info("Injected SiliconFlow credentials for node {}", node.get("id").asText());

                } else if ("Dify".equalsIgnoreCase(provider)) {
                    var credentialOpt = gatewaySecretService.resolveProviderCredential("dify");
                    if (credentialOpt.isEmpty() || credentialOpt.get().getApiKey() == null
                            || credentialOpt.get().getApiKey().isEmpty()) {
                        log.error("Dify provider configured but no API key available in ModelConfig");
                        continue;
                    }
                    data.put("api_key", credentialOpt.get().getApiKey());
                    data.put("base_url", credentialOpt.get().getBaseUrl() != null
                            ? credentialOpt.get().getBaseUrl()
                            : config.getDifyEndpoint());
                    log.info("Injected Dify credentials for node {}", node.get("id").asText());

                } else if ("Ollama".equalsIgnoreCase(provider)) {
                    if (config.getOllamaEndpoint() == null || config.getOllamaEndpoint().isEmpty()) {
                        log.error("Ollama provider configured but no endpoint available in ModelConfig");
                        continue;
                    }
                    data.put("base_url", config.getOllamaEndpoint());
                    var credentialOpt = gatewaySecretService.resolveProviderCredential("local-ollama")
                            .or(() -> gatewaySecretService.resolveProviderCredential("ollama"));
                    if (credentialOpt.isPresent() && credentialOpt.get().getApiKey() != null
                            && !credentialOpt.get().getApiKey().isEmpty()) {
                        data.put("api_key", credentialOpt.get().getApiKey());
                    }
                    log.info("Injected Ollama credentials for node {}", node.get("id").asText());

                } else if ("OpenAI".equalsIgnoreCase(provider)) {
                    // OpenAI requires explicit configuration - no fallback to other providers
                    // The DSL should already contain the api_key for OpenAI, or the user must configure it
                    log.info("OpenAI provider node {} - using credentials from DSL or requiring explicit configuration",
                            node.get("id").asText());

                } else {
                    log.warn("Unknown provider '{}' for node {} - no credentials injected",
                            provider, node.get("id").asText());
                }
            }
        }
    }
}
