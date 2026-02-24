package com.adlin.orin.gateway.adapter.impl;

import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.ChatCompletionResponse;
import com.adlin.orin.gateway.dto.EmbeddingRequest;
import com.adlin.orin.gateway.dto.EmbeddingResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Ollama Provider适配器实现
 * 支持本地部署的 Ollama 服务 (OpenAI 兼容接口)
 */
@Slf4j
public class OllamaProviderAdapter implements ProviderAdapter {

    private final String providerId;
    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OllamaProviderAdapter(String providerId, String baseUrl, RestTemplate restTemplate) {
        this.providerId = providerId;
        this.baseUrl = baseUrl != null ? baseUrl : "http://localhost:11434/v1";
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();

        this.webClient = WebClient.builder()
                .baseUrl(this.baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String getProviderType() {
        return "ollama";
    }

    @Override
    public String getProviderName() {
        return "Ollama - " + providerId;
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.fromCallable(() -> {
            try {
                // Ollama typically responds to / or /api/tags
                ResponseEntity<String> response = restTemplate.getForEntity(baseUrl.replace("/v1", ""), String.class);
                return response.getStatusCode().is2xxSuccessful();
            } catch (Exception e) {
                log.error("Ollama health check failed at {}: {}", baseUrl, e.getMessage());
                return false;
            }
        });
    }

    @Override
    public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request) {
        return Mono.fromCallable(() -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<ChatCompletionRequest> entity = new HttpEntity<>(request, headers);

            String url = baseUrl;
            if (!url.contains("/api/") && !url.contains("/v1/")) {
                url = url.endsWith("/") ? url + "api/chat" : url + "/api/chat";
            } else if (!url.contains("/chat/completions") && !url.contains("/chat")) {
                url = url.endsWith("/") ? url + "chat/completions" : url + "/chat/completions";
            }

            // If using native /api/chat, we might need a custom response converter if
            // response is not OpenAI format
            // But for now, let's try the exchange with current response type
            ResponseEntity<ChatCompletionResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ChatCompletionResponse.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Ollama API call failed: " + response.getStatusCode());
            }

            ChatCompletionResponse res = response.getBody();
            // Ensure provider field is set for tracking
            // Use reflection or a setter if builder is not available for modification
            return res;
        }).doOnError(e -> log.error("Ollama chat completion failed: {}", e.getMessage()));
    }

    @Override
    public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest request) {
        request.setStream(true);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> line.startsWith("data: "))
                .map(line -> line.substring(6).trim())
                .filter(content -> !content.equals("[DONE]"))
                .flatMap(content -> {
                    try {
                        return Mono.just(objectMapper.readValue(content, ChatCompletionResponse.class));
                    } catch (Exception e) {
                        log.error("Error parsing Ollama stream chunk: {}", e.getMessage());
                        return Mono.empty();
                    }
                });
    }

    @Override
    public Mono<EmbeddingResponse> embedding(EmbeddingRequest request) {
        return Mono.fromCallable(() -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<EmbeddingRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<EmbeddingResponse> response = restTemplate.exchange(
                    baseUrl + "/embeddings",
                    HttpMethod.POST,
                    entity,
                    EmbeddingResponse.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Ollama embedding call failed: " + response.getStatusCode());
            }

            return response.getBody();
        }).doOnError(e -> log.error("Ollama embedding failed: {}", e.getMessage()));
    }

    @Override
    public Mono<Map<String, Object>> getModels() {
        return Mono.fromSupplier(() -> {
            try {
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(baseUrl + "/models",
                        HttpMethod.GET, null,
                        new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                        });
                if (response.getBody() != null) {
                    return (Map<String, Object>) response.getBody();
                }
                return new HashMap<>();
            } catch (Exception e) {
                log.error("Failed to get Ollama models: {}", e.getMessage());
                return new HashMap<>();
            }
        });
    }

    @Override
    public Map<String, Object> getProviderConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("providerId", providerId);
        config.put("providerType", "ollama");
        config.put("baseUrl", baseUrl);
        return config;
    }

    @Override
    public double estimateCost(ChatCompletionRequest request) {
        // Local models are free
        return 0.0;
    }
}
