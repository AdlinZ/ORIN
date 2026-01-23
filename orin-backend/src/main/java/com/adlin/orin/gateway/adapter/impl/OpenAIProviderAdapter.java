package com.adlin.orin.gateway.adapter.impl;

import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.ChatCompletionResponse;
import com.adlin.orin.gateway.dto.EmbeddingRequest;
import com.adlin.orin.gateway.dto.EmbeddingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * OpenAI Provider适配器实现
 */
@Slf4j
public class OpenAIProviderAdapter implements ProviderAdapter {

    private final String providerId;
    private final String apiKey;
    private final String baseUrl;
    private final RestTemplate restTemplate;

    // OpenAI价格表（每1000 tokens）
    private static final Map<String, Double[]> MODEL_PRICING = Map.of(
            "gpt-4", new Double[] { 0.03, 0.06 }, // [input, output]
            "gpt-4-turbo", new Double[] { 0.01, 0.03 },
            "gpt-3.5-turbo", new Double[] { 0.0005, 0.0015 });

    public OpenAIProviderAdapter(String providerId, String apiKey, String baseUrl, RestTemplate restTemplate) {
        this.providerId = providerId;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.openai.com/v1";
        this.restTemplate = restTemplate;
    }

    @Override
    public String getProviderType() {
        return "openai";
    }

    @Override
    public String getProviderName() {
        return "OpenAI - " + providerId;
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.fromCallable(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + apiKey);
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<?> entity = new HttpEntity<>(headers);

                @SuppressWarnings("unchecked")
                ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate
                        .exchange(
                                baseUrl + "/models",
                                HttpMethod.GET,
                                entity,
                                Map.class);

                return response.getStatusCode().is2xxSuccessful();
            } catch (Exception e) {
                log.error("OpenAI health check failed: {}", e.getMessage());
                return false;
            }
        });
    }

    @Override
    public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request) {
        return Mono.fromCallable(() -> {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 构建OpenAI请求
            Map<String, Object> requestBody = buildOpenAIRequest(request);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate
                    .exchange(
                            baseUrl + "/chat/completions",
                            HttpMethod.POST,
                            entity,
                            Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("OpenAI API call failed");
            }

            // 转换响应并添加provider信息
            return convertToStandardResponse(response.getBody());
        }).doOnError(e -> log.error("OpenAI chat completion failed: {}", e.getMessage()));
    }

    @Override
    public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest request) {
        // TODO: 实现流式响应
        return chatCompletion(request).flux();
    }

    @Override
    public Mono<EmbeddingResponse> embedding(EmbeddingRequest request) {
        return Mono.fromCallable(() -> {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", request.getModel());
            requestBody.put("input", request.getInput());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate
                    .exchange(
                            baseUrl + "/embeddings",
                            HttpMethod.POST,
                            entity,
                            Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("OpenAI embedding API call failed");
            }

            return convertToEmbeddingResponse(response.getBody());
        }).doOnError(e -> log.error("OpenAI embedding failed: {}", e.getMessage()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getModels() {
        return Mono.fromSupplier(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + apiKey);
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<?> entity = new HttpEntity<>(headers);

                ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate
                        .exchange(
                                baseUrl + "/models",
                                HttpMethod.GET,
                                entity,
                                Map.class);

                if (response.getBody() != null) {
                    return (Map<String, Object>) response.getBody();
                }
                return new HashMap<String, Object>();
            } catch (Exception e) {
                log.error("Failed to get OpenAI models: {}", e.getMessage());
                return new HashMap<String, Object>();
            }
        });
    }

    @Override
    public Map<String, Object> getProviderConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("providerId", providerId);
        config.put("providerType", "openai");
        config.put("baseUrl", baseUrl);
        config.put("hasApiKey", apiKey != null && !apiKey.isEmpty());
        return config;
    }

    @Override
    public double estimateCost(ChatCompletionRequest request) {
        String model = request.getModel();

        // 查找匹配的定价
        Double[] pricing = MODEL_PRICING.entrySet().stream()
                .filter(e -> model.startsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(new Double[] { 0.001, 0.002 }); // 默认价格

        int inputTokens = estimateInputTokens(request);
        int outputTokens = request.getMaxTokens() != null ? request.getMaxTokens() : 500;

        double inputCost = (inputTokens / 1000.0) * pricing[0];
        double outputCost = (outputTokens / 1000.0) * pricing[1];

        return inputCost + outputCost;
    }

    /**
     * 构建OpenAI请求
     */
    private Map<String, Object> buildOpenAIRequest(ChatCompletionRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel());

        // 转换消息格式
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatCompletionRequest.Message msg : request.getMessages()) {
            Map<String, String> message = new HashMap<>();
            message.put("role", msg.getRole());
            message.put("content", msg.getContent());
            if (msg.getName() != null) {
                message.put("name", msg.getName());
            }
            messages.add(message);
        }
        body.put("messages", messages);

        // 添加可选参数
        if (request.getTemperature() != null)
            body.put("temperature", request.getTemperature());
        if (request.getTopP() != null)
            body.put("top_p", request.getTopP());
        if (request.getMaxTokens() != null)
            body.put("max_tokens", request.getMaxTokens());
        if (request.getStream() != null)
            body.put("stream", request.getStream());
        if (request.getStop() != null)
            body.put("stop", request.getStop());
        if (request.getPresencePenalty() != null)
            body.put("presence_penalty", request.getPresencePenalty());
        if (request.getFrequencyPenalty() != null)
            body.put("frequency_penalty", request.getFrequencyPenalty());
        if (request.getUser() != null)
            body.put("user", request.getUser());

        return body;
    }

    /**
     * 转换为标准响应格式
     */
    @SuppressWarnings("unchecked")
    private ChatCompletionResponse convertToStandardResponse(Map<String, Object> openAIResponse) {
        ChatCompletionResponse.ChatCompletionResponseBuilder builder = ChatCompletionResponse.builder()
                .id(openAIResponse.get("id").toString())
                .object(openAIResponse.get("object").toString())
                .created(((Number) openAIResponse.get("created")).longValue())
                .model(openAIResponse.get("model").toString())
                .provider("openai");

        // 转换choices
        List<Map<String, Object>> choices = (List<Map<String, Object>>) openAIResponse.get("choices");
        List<ChatCompletionResponse.Choice> convertedChoices = new ArrayList<>();

        for (Map<String, Object> choice : choices) {
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            convertedChoices.add(
                    ChatCompletionResponse.Choice.builder()
                            .index(((Number) choice.get("index")).intValue())
                            .message(ChatCompletionRequest.Message.builder()
                                    .role(message.get("role").toString())
                                    .content(message.get("content").toString())
                                    .build())
                            .finishReason(
                                    choice.get("finish_reason") != null ? choice.get("finish_reason").toString() : null)
                            .build());
        }
        builder.choices(convertedChoices);

        // 转换usage
        Map<String, Object> usage = (Map<String, Object>) openAIResponse.get("usage");
        if (usage != null) {
            builder.usage(ChatCompletionResponse.Usage.builder()
                    .promptTokens(((Number) usage.get("prompt_tokens")).intValue())
                    .completionTokens(((Number) usage.get("completion_tokens")).intValue())
                    .totalTokens(((Number) usage.get("total_tokens")).intValue())
                    .build());
        }

        return builder.build();
    }

    /**
     * 转换嵌入响应
     */
    @SuppressWarnings("unchecked")
    private EmbeddingResponse convertToEmbeddingResponse(Map<String, Object> openAIResponse) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) openAIResponse.get("data");
        List<EmbeddingResponse.EmbeddingData> embeddingData = new ArrayList<>();

        for (Map<String, Object> item : data) {
            embeddingData.add(EmbeddingResponse.EmbeddingData.builder()
                    .index(((Number) item.get("index")).intValue())
                    .embedding((List<Double>) item.get("embedding"))
                    .build());
        }

        Map<String, Object> usage = (Map<String, Object>) openAIResponse.get("usage");

        return EmbeddingResponse.builder()
                .data(embeddingData)
                .model(openAIResponse.get("model").toString())
                .usage(EmbeddingResponse.Usage.builder()
                        .promptTokens(((Number) usage.get("prompt_tokens")).intValue())
                        .totalTokens(((Number) usage.get("total_tokens")).intValue())
                        .build())
                .build();
    }

    /**
     * 估算输入Token数量
     */
    private int estimateInputTokens(ChatCompletionRequest request) {
        int total = 0;
        for (ChatCompletionRequest.Message message : request.getMessages()) {
            // 简单估算：每4个字符约等于1个token
            total += message.getContent().length() / 4;
        }
        return total;
    }
}
