package com.adlin.orin.modules.multimodal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;

@Slf4j
@Service
public class VisualAnalysisService {

    @Value("${siliconflow.vlm.model:}")
    private String fallbackVlmModel;

    @Value("${siliconflow.api.key}")
    private String apiKey;

    @Value("${siliconflow.api.base-url:https://api.siliconflow.cn/v1}")
    private String baseUrl;

    private final com.adlin.orin.modules.model.service.ModelConfigService modelConfigService;
    private final com.adlin.orin.modules.audit.service.AuditLogService auditLogService;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VisualAnalysisService(
            com.adlin.orin.modules.model.service.ModelConfigService modelConfigService,
            com.adlin.orin.modules.audit.service.AuditLogService auditLogService) {
        this.modelConfigService = modelConfigService;
        this.auditLogService = auditLogService;

        // Configure RestTemplate with long timeouts for VLM
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10s connect
        factory.setReadTimeout(600000); // 10m read (requested by user)
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Analyse image and generate caption using a dynamic model.
     *
     * @param imageUrl  Public URL or Base64 data URI of the image
     * @param modelName Optional model name to override default
     * @return Generated caption
     */
    public String analyzeImage(String imageUrl, String modelName) {
        return analyzeImage(imageUrl, modelName, null);
    }

    /**
     * Analyse image with custom prompt.
     *
     * @param imageUrl  Public URL or Base64 data URI of the image
     * @param modelName Optional model name to override default
     * @param customPrompt Optional custom prompt to override default
     * @return Generated caption
     */
    public String analyzeImage(String imageUrl, String modelName, String customPrompt) {
        var config = modelConfigService.getConfig();
        String sfApiKey = (config.getSiliconFlowApiKey() != null && !config.getSiliconFlowApiKey().isEmpty())
                ? config.getSiliconFlowApiKey()
                : apiKey;
        String sfBaseUrl = (config.getSiliconFlowEndpoint() != null && !config.getSiliconFlowEndpoint().isEmpty())
                ? config.getSiliconFlowEndpoint()
                : baseUrl;

        // Log warning if using placeholder API key
        if (sfApiKey == null || sfApiKey.isEmpty()) {
            log.error("SiliconFlow API key is NOT CONFIGURED! Please set it in System Settings -> Model Config");
        } else if (sfApiKey.equals("sk-placeholder")) {
            log.error("SiliconFlow API key is still using placeholder 'sk-placeholder'! Please configure a valid API key");
        } else {
            log.info("Using SiliconFlow API key starting with: {}", sfApiKey.substring(0, Math.min(8, sfApiKey.length())));
        }

        String url = sfBaseUrl + "/chat/completions";
        String model = (modelName != null && !modelName.isEmpty()) ? modelName
                : (config.getVlmModel() != null ? config.getVlmModel() : fallbackVlmModel);

        if (model == null || model.isEmpty()) {
            throw new RuntimeException("未配置 VLM 模型，请在系统设置中选择视觉模型");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(sfApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Use custom prompt if provided, otherwise use default
        String prompt = customPrompt != null ? customPrompt
                : "请详细描述这张图片的内容，用于语义检索和资产管理。如果你能识别出物体、场景、文字、颜色和氛围，请一并说明。";

        // Log image data size for debugging
        if (imageUrl.startsWith("data:")) {
            log.info("Sending image to VLM model {}, base64 data size: {} chars, first 50 chars: {}",
                    model, imageUrl.length(), imageUrl.substring(0, Math.min(50, imageUrl.length())));
        }

        // Construct VLM payload (SiliconFlow/OpenAI Compatible)
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)));
        content.add(Map.of("type", "text", "text", prompt));

        // Debug: 打印完整请求体
        Map<String, Object> debugBody = new HashMap<>();
        debugBody.put("model", model);
        debugBody.put("messages", Collections.singletonList(Map.of(
                "role", "user",
                "content", content
        )));
        debugBody.put("max_tokens", 1024);
        log.debug("VLM Request body: {}", debugBody);

        Map<String, Object> message = Map.of(
                "role", "user",
                "content", content);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", Collections.singletonList(message));
        body.put("max_tokens", 1024);

        // 打印完整请求 JSON
        try {
            String requestJson = objectMapper.writeValueAsString(body);
            log.info("=== VLM Request JSON (first 500 chars) ===");
            log.info(requestJson.substring(0, Math.min(500, requestJson.length())));
        } catch (Exception e) {
            log.warn("Failed to serialize request body: {}", e.getMessage());
        }

        // Support for "Thinking" models like Kimi K2.5 or DeepSeek-R1 VLM if used
        if (config.getAutoAnalysisEnabled() != null && config.getAutoAnalysisEnabled()) {
            // We can check model name prefix or just enable it if the user wants
            // high-quality
            // For now, let's keep it optional but allow it for SF reasoning models
            if (model.contains("thinking") || model.contains("R1") || model.contains("Kimi")) {
                body.put("enable_thinking", true);
                body.put("thinking_budget", 4096);
            }
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        long startTime = System.currentTimeMillis();

        // Retry logic for transient server errors
        int maxRetries = 3;
        int retryDelay = 2000; // 2 seconds

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
                long duration = System.currentTimeMillis() - startTime;

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode choice = root.path("choices").get(0);
                    JsonNode messageNode = choice.path("message");

                    String contentText = messageNode.path("content").asText();
                    String reasoningContent = messageNode.path("reasoning_content").asText("");

                    // If there's reasoning content, we might want to include it or at least log it
                    String finalSummary = contentText;
                    if (!reasoningContent.isEmpty()) {
                        log.debug("VLM Reasoning: {}", reasoningContent);
                        // For summary purposes, normally we just want the final content
                    }

                    // Audit Logging
                    JsonNode usageNode = root.path("usage");
                    int promptTokens = usageNode.path("prompt_tokens").asInt(0);
                    int completionTokens = usageNode.path("completion_tokens").asInt(0);

                    auditLogService.logApiCall(
                            "system", "SF_VLM_INTERNAL", "SiliconFlow", "SILICONFLOW",
                            "/multimodal/vlm", "POST", model, "127.0.0.1", "ORIN-VLM-Service",
                            "[Image Analysis]", finalSummary, response.getStatusCode().value(),
                            duration, promptTokens, completionTokens, 0.0, true, null, null, null);

                    return finalSummary;
                } else {
                    String errorBody = response.getBody();
                    // Check for server error (5xx) - retryable
                    boolean isServerError = response.getStatusCode().is5xxServerError() ||
                            (errorBody != null && errorBody.contains("5"));

                    if (isServerError && attempt < maxRetries) {
                        log.warn("VLM API returned server error (attempt {}/{}): {} - {}. Retrying...",
                                attempt, maxRetries, response.getStatusCode(), errorBody);
                        try {
                            Thread.sleep(retryDelay * attempt); // Exponential backoff
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Retry interrupted", ie);
                        }
                        continue;
                    }

                    String error = "VLM API failed: " + response.getStatusCode() + " - " + errorBody;
                    auditLogService.logApiCall(
                            "system", "SF_VLM_INTERNAL", "SiliconFlow", "SILICONFLOW",
                            "/multimodal/vlm", "POST", model, "127.0.0.1", "ORIN-VLM-Service",
                            "[Image Analysis]", null, response.getStatusCode().value(),
                            duration, 0, 0, 0.0, false, error, null, null);
                    throw new RuntimeException(error);
                }
            } catch (Exception e) {
                // Network errors and JSON parsing errors are retryable
                if (attempt < maxRetries) {
                    log.warn("VLM API request failed (attempt {}/{}): {}. Retrying...",
                            attempt, maxRetries, e.getMessage());
                    try {
                        Thread.sleep(retryDelay * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                    continue;
                }
                // On last attempt, wrap and throw
                long duration = System.currentTimeMillis() - startTime;
                String errorMsg = "VLM Analysis Failed using model " + model + ": " + e.getMessage();
                log.error(errorMsg, e);
                auditLogService.logApiCall(
                        "system", "SF_VLM_INTERNAL", "SiliconFlow", "SILICONFLOW",
                        "/multimodal/vlm", "POST", model, "127.0.0.1", "ORIN-VLM-Service",
                        "[Image Analysis]", null, 500,
                        duration, 0, 0, 0.0, false, e.getMessage(), null, null);
                throw new RuntimeException(errorMsg, e);
            }
        }

        // Should not reach here, but just in case
        throw new RuntimeException("VLM Analysis failed after " + maxRetries + " attempts");
    }

    public String analyzeImage(String imageUrl) {
        return analyzeImage(imageUrl, null);
    }

    /**
     * Get available models from SiliconFlow using system API key.
     */
    public List<Map<String, String>> getAvailableModels() {
        String url = baseUrl + "/models";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode data = response.getBody().path("data");
                if (data.isArray()) {
                    List<Map<String, String>> models = new ArrayList<>();
                    for (JsonNode node : data) {
                        String id = node.path("id").asText();
                        models.add(Map.of("id", id, "name", id));
                    }
                    return models;
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch models", e);
        }
        return Collections.emptyList();
    }

    @Async
    public CompletableFuture<String> analyzeImageAsync(String imageUrl, String modelName) {
        try {
            String result = analyzeImage(imageUrl, modelName);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("Async Analysis Failed", e);
            throw e;
        }
    }
}
