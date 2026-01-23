package com.adlin.orin.modules.model.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * DeepSeek集成服务
 * DeepSeek API与OpenAI兼容，使用相同的API格式
 */
@Service
public class DeepSeekIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekIntegrationService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 测试DeepSeek连接
     */
    public boolean testConnection(String endpoint, String apiKey, String model) {
        try {
            String url = endpoint + "/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model != null ? model : "deepseek-chat");

            List<Map<String, Object>> messages = Arrays.asList(
                    Map.of("role", "user", "content", "Hello"));
            requestBody.put("messages", messages);
            requestBody.put("stream", false);
            requestBody.put("max_tokens", 10);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            log.info("DeepSeek connection test successful: {}", response.getStatusCode());
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("DeepSeek connection test failed: {}", e.getMessage());
            throw new RuntimeException("DeepSeek连接测试失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送消息到DeepSeek
     */
    public Optional<Object> sendMessage(String endpoint, String apiKey, String model, String message) {
        try {
            String url = endpoint + "/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            List<Map<String, Object>> messages = Arrays.asList(
                    Map.of("role", "user", "content", message));
            requestBody.put("messages", messages);
            requestBody.put("stream", false);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            return Optional.ofNullable(response.getBody());
        } catch (Exception e) {
            log.error("Failed to send message to DeepSeek: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 使用完整参数发送消息到DeepSeek
     */
    public Optional<Object> sendMessageWithFullParams(
            String url,
            String apiKey,
            String model,
            List<Map<String, Object>> messages,
            double temperature,
            double topP,
            int maxTokens) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
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

            return Optional.ofNullable(response.getBody());
        } catch (Exception e) {
            log.error("Failed to send message with params to DeepSeek: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 发送流式消息到DeepSeek
     */
    public Optional<Object> sendStreamMessage(
            String endpoint,
            String apiKey,
            String model,
            List<Map<String, Object>> messages,
            double temperature,
            int maxTokens) {
        try {
            String url = endpoint + "/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("stream", true);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            return Optional.ofNullable(response.getBody());
        } catch (Exception e) {
            log.error("Failed to send stream message to DeepSeek: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
