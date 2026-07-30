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
import java.time.Duration;

/**
 * Ollama Provider适配器实现
 * 支持本地部署的 Ollama 服务 (OpenAI 兼容接口)
 */
@Slf4j
public class OllamaProviderAdapter implements ProviderAdapter {

    private final String providerId;
    private final String apiKey;
    private final String baseUrl;
    private final String rootUrl;
    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OllamaProviderAdapter(String providerId, String apiKey, String baseUrl, RestTemplate restTemplate) {
        this.providerId = providerId;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.baseUrl = normalizeOpenAIBaseUrl(baseUrl);
        this.rootUrl = deriveRootUrl(this.baseUrl);
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(this.baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                        reactor.netty.http.client.HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(120))));
        if (!this.apiKey.isEmpty() && !"sk-placeholder".equals(this.apiKey)) {
            builder.defaultHeader("Authorization", "Bearer " + this.apiKey);
        }
        this.webClient = builder.build();
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
                ResponseEntity<Map> response = restTemplate.exchange(
                        baseUrl + "/models",
                        HttpMethod.GET,
                        new HttpEntity<>(buildHeaders()),
                        Map.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    return true;
                }
            } catch (Exception ignored) {
                // fall through to native tags health check
            }

            try {
                ResponseEntity<Map> response = restTemplate.exchange(
                        rootUrl + "/api/tags",
                        HttpMethod.GET,
                        new HttpEntity<>(buildHeaders()),
                        Map.class);
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
            HttpEntity<ChatCompletionRequest> entity = new HttpEntity<>(request, buildHeaders());
            ResponseEntity<ChatCompletionResponse> response = restTemplate.exchange(
                    baseUrl + "/chat/completions",
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
            HttpEntity<EmbeddingRequest> entity = new HttpEntity<>(request, buildHeaders());

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
                        HttpMethod.GET, new HttpEntity<>(buildHeaders()),
                        new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                        });
                if (response.getBody() != null) {
                    return (Map<String, Object>) response.getBody();
                }
            } catch (Exception e) {
                log.warn("Failed to get OpenAI-compatible Ollama models at {}: {}", baseUrl, e.getMessage());
            }

            try {
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(rootUrl + "/api/tags",
                        HttpMethod.GET, new HttpEntity<>(buildHeaders()),
                        new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                        });
                if (response.getBody() != null) {
                    return convertTagsToOpenAIModels(response.getBody());
                }
            } catch (Exception e) {
                log.error("Failed to get Ollama models: {}", e.getMessage());
            }
            return new HashMap<>();
        });
    }

    @Override
    public Map<String, Object> getProviderConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("providerId", providerId);
        config.put("providerType", "ollama");
        config.put("baseUrl", baseUrl);
        config.put("hasApiKey", apiKey != null && !apiKey.isEmpty());
        return config;
    }

    @Override
    public double estimateCost(ChatCompletionRequest request) {
        // Local models are free
        return 0.0;
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isEmpty() && !"sk-placeholder".equals(apiKey)) {
            headers.set("Authorization", "Bearer " + apiKey);
        }
        return headers;
    }

    private String normalizeOpenAIBaseUrl(String input) {
        String raw = (input == null || input.isBlank()) ? "http://localhost:11434" : input.trim();
        String normalized = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        normalized = normalized.replaceAll("/v1/.*$", "/v1");
        normalized = normalized.replaceAll("/api/v1$", "/v1");
        if (normalized.endsWith("/v1")) {
            return normalized;
        }
        if (normalized.endsWith("/api")) {
            return normalized.substring(0, normalized.length() - 4) + "/v1";
        }
        if (normalized.contains("/api/")) {
            return normalized.replaceFirst("/api/.*$", "/v1");
        }
        return normalized + "/v1";
    }

    private String deriveRootUrl(String openAIBaseUrl) {
        if (openAIBaseUrl.endsWith("/v1")) {
            return openAIBaseUrl.substring(0, openAIBaseUrl.length() - 3);
        }
        return openAIBaseUrl;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertTagsToOpenAIModels(Map<String, Object> tagsResponse) {
        Map<String, Object> result = new HashMap<>();
        result.put("object", "list");

        List<Map<String, Object>> data = new ArrayList<>();
        Object modelsObj = tagsResponse.get("models");
        if (modelsObj instanceof List<?> models) {
            for (Object modelObj : models) {
                if (modelObj instanceof Map<?, ?> modelMap) {
                    Object name = modelMap.get("name");
                    if (name != null) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("id", name.toString());
                        item.put("object", "model");
                        item.put("created", 0);
                        item.put("owned_by", "ollama");
                        data.add(item);
                    }
                }
            }
        }
        result.put("data", data);
        return result;
    }
}
