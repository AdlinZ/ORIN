package com.adlin.orin.modules.adapter.impl;

import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.ChatCompletionResponse;
import com.adlin.orin.gateway.dto.EmbeddingRequest;
import com.adlin.orin.gateway.dto.EmbeddingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * n8n 工作流适配器
 * 支持通过 Webhook 或 API 调用 n8n 工作流
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class N8nWorkflowAdapter implements ProviderAdapter {

    private final RestTemplate restTemplate;
    private String baseUrl;
    private String apiKey;

    /**
     * 配置适配器
     */
    public void configure(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    @Override
    public String getProviderType() {
        return "n8n";
    }

    @Override
    public String getProviderName() {
        return "n8n Workflow";
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.fromCallable(() -> {
            try {
                String url = baseUrl + "/healthz";
                HttpHeaders headers = new HttpHeaders();
                if (apiKey != null) {
                    headers.set("X-N8N-API-KEY", apiKey);
                }
                HttpEntity<?> entity = new HttpEntity<>(headers);

                ResponseEntity<Map> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, Map.class);
                return response.getStatusCode().is2xxSuccessful();
            } catch (Exception e) {
                log.error("n8n health check failed: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * 调用 n8n 工作流
     */
    public Map<String, Object> invokeWorkflow(String workflowId, Map<String, Object> inputs) {
        log.info("Invoking n8n workflow: {}", workflowId);

        try {
            String url = baseUrl + "/webhook/" + workflowId;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null) {
                headers.set("X-N8N-API-KEY", apiKey);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(inputs, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new RuntimeException("n8n workflow invocation failed");
            }
        } catch (Exception e) {
            log.error("Error invoking n8n workflow: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }

    @Override
    public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request) {
        return Mono.error(new UnsupportedOperationException("n8n adapter does not support chat completion"));
    }

    @Override
    public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest request) {
        return Flux.error(new UnsupportedOperationException("n8n adapter does not support streaming"));
    }

    @Override
    public Mono<EmbeddingResponse> embedding(EmbeddingRequest request) {
        return Mono.error(new UnsupportedOperationException("n8n adapter does not support embedding"));
    }

    @Override
    public Mono<Map<String, Object>> getModels() {
        return Mono.just(Map.of("provider", "n8n", "type", "workflow"));
    }

    @Override
    public Map<String, Object> getProviderConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("providerType", "n8n");
        config.put("baseUrl", baseUrl);
        config.put("hasApiKey", apiKey != null && !apiKey.isEmpty());
        return config;
    }

    @Override
    public double estimateCost(ChatCompletionRequest request) {
        return 0.0; // n8n 通常按执行次数计费
    }
}
