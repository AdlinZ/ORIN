package com.adlin.orin.gateway.adapter.impl;

import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.ChatCompletionResponse;
import com.adlin.orin.gateway.dto.EmbeddingRequest;
import com.adlin.orin.gateway.dto.EmbeddingResponse;
import com.adlin.orin.modules.agent.service.DifyIntegrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Dify Provider适配器实现
 */
@Slf4j
public class DifyProviderAdapter implements ProviderAdapter {

    private final String providerId;
    private final String endpointUrl;
    private final String apiKey;
    private final RestTemplate restTemplate;
    private final DifyIntegrationService difyService;

    public DifyProviderAdapter(String providerId, String endpointUrl, String apiKey,
            RestTemplate restTemplate, DifyIntegrationService difyService) {
        this.providerId = providerId;
        this.endpointUrl = endpointUrl;
        this.apiKey = apiKey;
        this.restTemplate = restTemplate;
        this.difyService = difyService;
    }

    @Override
    public String getProviderType() {
        return "dify";
    }

    @Override
    public String getProviderName() {
        return "Dify - " + providerId;
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.fromCallable(() -> difyService.testConnection(endpointUrl, apiKey))
                .doOnError(e -> log.error("Dify health check failed: {}", e.getMessage()))
                .onErrorReturn(false);
    }

    @Override
    public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request) {
        return Mono.fromCallable(() -> {
            // 构建Dify消息格式
            String lastUserMessage = extractLastUserMessage(request.getMessages());

            // 调用Dify API
            Optional<Object> response = difyService.sendMessage(endpointUrl, apiKey, providerId, lastUserMessage);

            if (response.isEmpty()) {
                throw new RuntimeException("Dify API call failed");
            }

            // 转换为OpenAI兼容格式
            @SuppressWarnings("unchecked")
            Map<String, Object> difyResponse = (Map<String, Object>) response.get();

            return ChatCompletionResponse.builder()
                    .id(difyResponse.getOrDefault("message_id", UUID.randomUUID().toString()).toString())
                    .object("chat.completion")
                    .created(System.currentTimeMillis() / 1000)
                    .model(request.getModel())
                    .provider("dify")
                    .choices(List.of(
                            ChatCompletionResponse.Choice.builder()
                                    .index(0)
                                    .message(ChatCompletionRequest.Message.builder()
                                            .role("assistant")
                                            .content(difyResponse.getOrDefault("answer", "").toString())
                                            .build())
                                    .finishReason("stop")
                                    .build()))
                    .usage(buildUsage(difyResponse))
                    .build();
        }).doOnError(e -> log.error("Dify chat completion failed: {}", e.getMessage()));
    }

    @Override
    public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest request) {
        // Dify流式响应实现（暂时返回非流式）
        return chatCompletion(request).flux();
    }

    @Override
    public Mono<EmbeddingResponse> embedding(EmbeddingRequest request) {
        // Dify目前不直接支持嵌入API
        return Mono.error(new UnsupportedOperationException("Dify does not support embedding API directly"));
    }

    @Override
    public Mono<Map<String, Object>> getModels() {
        return Mono.fromCallable(() -> {
            Map<String, Object> models = new HashMap<>();
            models.put("object", "list");
            models.put("data", List.of(
                    Map.of(
                            "id", "dify-" + providerId,
                            "object", "model",
                            "created", System.currentTimeMillis() / 1000,
                            "owned_by", "dify")));
            return models;
        });
    }

    @Override
    public Map<String, Object> getProviderConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("providerId", providerId);
        config.put("providerType", "dify");
        config.put("endpointUrl", endpointUrl);
        config.put("hasApiKey", apiKey != null && !apiKey.isEmpty());
        return config;
    }

    @Override
    public double estimateCost(ChatCompletionRequest request) {
        // Dify成本估算（根据实际情况调整）
        int estimatedTokens = estimateTokens(request);
        // 假设每1000 tokens $0.002
        return (estimatedTokens / 1000.0) * 0.002;
    }

    /**
     * 提取最后一条用户消息
     */
    private String extractLastUserMessage(List<ChatCompletionRequest.Message> messages) {
        return messages.stream()
                .filter(m -> "user".equals(m.getRole()))
                .reduce((first, second) -> second)
                .map(ChatCompletionRequest.Message::getContent)
                .orElse("");
    }

    /**
     * 构建Usage信息
     */
    private ChatCompletionResponse.Usage buildUsage(Map<String, Object> difyResponse) {
        // 从Dify响应中提取token使用信息
        Object metadata = difyResponse.get("metadata");
        if (metadata instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) metadata;
            Object usage = meta.get("usage");
            if (usage instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> usageMap = (Map<String, Object>) usage;
                return ChatCompletionResponse.Usage.builder()
                        .promptTokens(getIntValue(usageMap, "prompt_tokens"))
                        .completionTokens(getIntValue(usageMap, "completion_tokens"))
                        .totalTokens(getIntValue(usageMap, "total_tokens"))
                        .build();
            }
        }

        // 默认值
        return ChatCompletionResponse.Usage.builder()
                .promptTokens(0)
                .completionTokens(0)
                .totalTokens(0)
                .build();
    }

    /**
     * 从Map中安全获取整数值
     */
    private Integer getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    /**
     * 估算Token数量
     */
    private int estimateTokens(ChatCompletionRequest request) {
        int total = 0;
        for (ChatCompletionRequest.Message message : request.getMessages()) {
            // 简单估算：每4个字符约等于1个token
            total += message.getContent().length() / 4;
        }
        // 加上max_tokens（如果设置）
        if (request.getMaxTokens() != null) {
            total += request.getMaxTokens();
        } else {
            total += 500; // 默认假设500 tokens的响应
        }
        return total;
    }
}
