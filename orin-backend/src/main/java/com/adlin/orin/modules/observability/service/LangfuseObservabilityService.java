package com.adlin.orin.modules.observability.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Langfuse 观测服务 (HTTP API 方式)
 * 用于智能体调用链路追踪、Token 消耗统计、耗时监控
 * 
 * API 文档: https://langfuse.com/docs/api
 */
@Slf4j
@Service
public class LangfuseObservabilityService {

    private final WebClient webClient;
    private final boolean enabled;
    private final String host;
    private final String publicKey;

    public LangfuseObservabilityService(
            @Value("${langfuse.public-key:}") String publicKey,
            @Value("${langfuse.secret-key:}") String secretKey,
            @Value("${langfuse.host:}") String host,
            @Value("${langfuse.enabled:false}") boolean enabled) {

        this.publicKey = publicKey;
        this.host = host;
        this.enabled = enabled && !publicKey.isEmpty() && !secretKey.isEmpty();
        
        if (this.enabled) {
            String baseUrl = host.isEmpty() ? "https://cloud.langfuse.com" : host;
            this.webClient = WebClient.builder()
                    .baseUrl(baseUrl + "/api/v1")
                    .defaultHeader("Authorization", "Basic " + 
                        java.util.Base64.getEncoder().encodeToString(
                            (publicKey + ":" + secretKey).getBytes()))
                    .defaultHeader("Content-Type", "application/json")
                    .build();
            log.info("Langfuse initialized with base URL: {}", baseUrl);
        } else {
            this.webClient = null;
            log.warn("Langfuse is not enabled or credentials not configured");
        }
    }

    /**
     * 创建追踪会话
     */
    public String createTrace(String traceName, Map<String, String> metadata) {
        if (!isEnabled()) {
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("name", traceName);
            requestBody.put("metadata", metadata);

            TraceResponse response = webClient.post()
                    .uri("/traces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(TraceResponse.class)
                    .block();

            if (response != null) {
                log.info("Created Langfuse trace: {}", response.id);
                return response.id;
            }
        } catch (Exception e) {
            log.error("Failed to create Langfuse trace: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 记录 LLM 调用
     */
    public void recordLLMGeneration(String traceId, String model, String prompt, String completion,
            int promptTokens, int completionTokens, int totalTokens, long latencyMs) {
        if (!isEnabled() || traceId == null) {
            return;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("traceId", traceId);
            requestBody.put("name", "LLM Generation");
            requestBody.put("model", model);
            requestBody.put("prompt", prompt);
            requestBody.put("completion", completion);
            requestBody.put("promptTokens", promptTokens);
            requestBody.put("completionTokens", completionTokens);
            requestBody.put("totalTokens", totalTokens);
            requestBody.put("latency", latencyMs);

            webClient.post()
                    .uri("/generations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.debug("Recorded LLM generation for trace: {}", traceId);
        } catch (Exception e) {
            log.error("Failed to record LLM generation: {}", e.getMessage());
        }
    }

    /**
     * 记录工具调用
     */
    public void recordToolExecution(String traceId, String toolName, String input, String output,
            long startTime, long endTime) {
        if (!isEnabled() || traceId == null) {
            return;
        }

        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("startTime", Instant.ofEpochMilli(startTime).toString());
            metadata.put("endTime", Instant.ofEpochMilli(endTime).toString());
            metadata.put("duration", endTime - startTime);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("traceId", traceId);
            requestBody.put("name", toolName);
            requestBody.put("input", input);
            requestBody.put("output", output);
            requestBody.put("metadata", metadata);
            requestBody.put("startTime", Instant.ofEpochMilli(startTime).toString());
            requestBody.put("endTime", Instant.ofEpochMilli(endTime).toString());

            webClient.post()
                    .uri("/spans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.debug("Recorded tool execution: {} for trace: {}", toolName, traceId);
        } catch (Exception e) {
            log.error("Failed to record tool execution: {}", e.getMessage());
        }
    }

    /**
     * 记录智能体事件
     */
    public void recordEvent(String traceId, String eventName, Map<String, Object> metadata) {
        if (!isEnabled() || traceId == null) {
            return;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("traceId", traceId);
            requestBody.put("name", eventName);
            requestBody.put("metadata", metadata);

            webClient.post()
                    .uri("/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.debug("Recorded event: {} for trace: {}", eventName, traceId);
        } catch (Exception e) {
            log.error("Failed to record event: {}", e.getMessage());
        }
    }

    /**
     * 更新追踪分数（用于评估智能体回答质量）
     */
    public void updateTraceScore(String traceId, String scoreName, double score) {
        if (!isEnabled() || traceId == null) {
            return;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> scoreMap = new HashMap<>();
            scoreMap.put("name", scoreName);
            scoreMap.put("value", score);
            requestBody.put("scores", new Object[]{scoreMap});

            webClient.patch()
                    .uri("/traces/" + traceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.debug("Updated trace score: {} = {} for trace: {}", scoreName, score, traceId);
        } catch (Exception e) {
            log.error("Failed to update trace score: {}", e.getMessage());
        }
    }

    /**
     * 检查 Langfuse 是否可用
     */
    public boolean isEnabled() {
        return enabled && webClient != null;
    }

    /**
     * 检查是否已配置凭证（即使未启用）
     */
    public boolean isConfigured() {
        return !publicKey.isEmpty();
    }

    /**
     * 检查是否有公钥
     */
    public boolean hasPublicKey() {
        return !publicKey.isEmpty();
    }

    /**
     * 获取 Dashboard URL
     */
    public String getDashboardUrl() {
        if (host != null && !host.isEmpty()) {
            return host;
        }
        return "https://cloud.langfuse.com";
    }

    // Response DTOs
    @Data
    private static class TraceResponse {
        private String id;
        private String name;
        private String createdAt;
    }
}
