package com.adlin.orin.modules.model.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Ollama 集成服务
 * 提供与本地 Ollama 服务的交互能力
 */
@Slf4j
@Service
public class OllamaIntegrationService {

    private final RestTemplate restTemplate;

    public OllamaIntegrationService() {
        // Ollama 模型推理有时需要 60s+ 才能返回正文，配置较长超时
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        factory.setReadTimeout(java.time.Duration.ofSeconds(120));
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 测试 Ollama 连接
     */
    public boolean testConnection(String endpoint, String apiKey, String model) {
        try {
            String baseUrl = endpoint;
            if (baseUrl == null)
                return false;

            HttpHeaders headers = new HttpHeaders();
            if (apiKey != null && !apiKey.isEmpty() && !"sk-placeholder".equals(apiKey)) {
                headers.setBearerAuth(apiKey);
            }
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model != null ? model : "llama3");
            requestBody.put("messages", Collections.singletonList(
                    Map.of("role", "user", "content", "hi")));
            requestBody.put("stream", false);
            requestBody.put("max_tokens", 5);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Try OpenAI-compatible endpoint first, then fall back to native endpoint
            String[] urlPaths = buildOllamaUrls(baseUrl);
            for (String url : urlPaths) {
                try {
                    ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                            url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
                            });
                    if (response.getStatusCode().is2xxSuccessful()) {
                        return true;
                    }
                } catch (Exception e) {
                    log.debug("Ollama connection attempt failed at {}: {}", url, e.getMessage());
                }
            }
            return false;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("Ollama connection timed out or refused at {}: {}", endpoint, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Ollama connection test failed at {}: {}", endpoint, e.getMessage());
            return false;
        }
    }

    /**
     * Build possible Ollama URL paths to try
     * Always uses /v1/chat/completions — /api/chat can return 502 and is not used
     */
    private String[] buildOllamaUrls(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        if (normalized.contains("/v1")) {
            // Already /v1 — use only OpenAI-compatible endpoint
            return new String[] { normalized + "/chat/completions" };
        } else if (normalized.contains("/api/")) {
            // Convert native /api/ format to /v1
            return new String[] { normalized.replaceFirst("/api/.*$", "/v1/chat/completions") };
        } else {
            // No version prefix — use /v1
            return new String[] { normalized + "/v1/chat/completions" };
        }
    }

    /**
     * 发送消息到 Ollama
     */
    public Optional<Object> sendMessage(String endpoint, String apiKey, String model, String message) {
        List<Map<String, Object>> messages = Collections.singletonList(
                Map.of("role", "user", "content", message));
        return sendMessageWithFullParams(endpoint, apiKey, model, messages, 0.7, 0.9, 512);
    }

    /**
     * 使用完整参数发送消息到 Ollama
     */
    public Optional<Object> sendMessageWithFullParams(
            String endpoint,
            String apiKey,
            String model,
            List<Map<String, Object>> messages,
            double temperature,
            double topP,
            int maxTokens) {
        try {
            String baseUrl = endpoint;
            if (baseUrl == null)
                return Optional.empty();

            HttpHeaders headers = new HttpHeaders();
            if (apiKey != null && !apiKey.isEmpty() && !"sk-placeholder".equals(apiKey)) {
                headers.setBearerAuth(apiKey);
            }
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);
            requestBody.put("top_p", topP);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("stream", false);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Try all possible Ollama URL formats
            String[] urls = buildOllamaUrls(baseUrl);
            String lastError = null;
            for (String url : urls) {
                try {
                    ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                            url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
                            });
                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        return Optional.of(response.getBody());
                    }
                } catch (Exception e) {
                    lastError = "URL=" + url + ", ERR=" + e.getMessage();
                    log.debug("Ollama send message attempt failed at {}: {}", url, e.getMessage());
                }
            }

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "FAILED");
            errorResult.put("errorMessage", lastError != null ? lastError : "No successful response from Ollama");
            return Optional.of(errorResult);
        } catch (Exception e) {
            log.error("Failed to send message to Ollama: {}", e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "FAILED");
            errorResult.put("errorMessage", e.getMessage());
            return Optional.of(errorResult);
        }
    }

    /**
     * 获取 Ollama 向量
     */
    public Optional<Object> getEmbeddings(String endpoint, String apiKey, String model, String input) {
        try {
            String baseUrl = endpoint;
            if (baseUrl == null)
                return Optional.empty();

            HttpHeaders headers = new HttpHeaders();
            if (apiKey != null && !apiKey.isEmpty() && !"sk-placeholder".equals(apiKey)) {
                headers.setBearerAuth(apiKey);
            }
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", input);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Try possible embedding URL formats
            String[] urls = buildEmbeddingUrls(baseUrl);
            for (String url : urls) {
                try {
                    ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                            url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
                            });
                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        return Optional.of(response.getBody());
                    }
                } catch (Exception e) {
                    log.debug("Ollama embeddings attempt failed at {}: {}", url, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to get embeddings from Ollama: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private String[] buildEmbeddingUrls(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        if (normalized.contains("/v1")) {
            return new String[] { normalized + "/embeddings" };
        } else if (normalized.contains("/api/")) {
            return new String[] { normalized.replaceFirst("/api/.*$", "/v1/embeddings") };
        } else {
            return new String[] { normalized + "/v1/embeddings" };
        }
    }
}
