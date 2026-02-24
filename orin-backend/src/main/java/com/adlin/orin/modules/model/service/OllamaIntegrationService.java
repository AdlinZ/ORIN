package com.adlin.orin.modules.model.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Ollama 集成服务
 * 提供与本地 Ollama 服务的交互能力
 */
@Slf4j
@Service
public class OllamaIntegrationService {

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 测试 Ollama 连接
     */
    public boolean testConnection(String endpoint, String apiKey, String model) {
        try {
            // For Ollama, native /api/chat is more robust across versions
            String url = endpoint;
            if (url == null)
                return false;

            if (!url.contains("/api/") && !url.contains("/v1/")) {
                url = url.endsWith("/") ? url + "api/chat" : url + "/api/chat";
            } else if (url.contains("/v1") && !url.contains("/chat/completions")) {
                url = url.endsWith("/") ? url + "chat/completions" : url + "/chat/completions";
            }

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
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            return response.getStatusCode().is2xxSuccessful();
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("Ollama connection timed out or refused at {}: {}", endpoint, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Ollama connection test failed at {}: {}", endpoint, e.getMessage());
            return false;
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
            String url = endpoint;
            if (url == null)
                return Optional.empty();

            if (!url.contains("/api/") && !url.contains("/v1/")) {
                url = url.endsWith("/") ? url + "api/chat" : url + "/api/chat";
            } else if (url.contains("/v1") && !url.contains("/chat/completions")) {
                url = url.endsWith("/") ? url + "chat/completions" : url + "/chat/completions";
            }

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
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to send message to Ollama: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 获取 Ollama 向量
     */
    public Optional<Object> getEmbeddings(String endpoint, String apiKey, String model, String input) {
        try {
            String url = endpoint;
            if (url == null)
                return Optional.empty();

            if (!url.contains("/embeddings")) {
                if (!url.contains("/v1") && !url.contains("/api")) {
                    url = url.endsWith("/") ? url + "api/embeddings" : url + "/api/embeddings";
                } else {
                    url = url.endsWith("/") ? url + "embeddings" : url + "/embeddings";
                }
            }

            HttpHeaders headers = new HttpHeaders();
            if (apiKey != null && !apiKey.isEmpty() && !"sk-placeholder".equals(apiKey)) {
                headers.setBearerAuth(apiKey);
            }
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", input);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to get embeddings from Ollama: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
