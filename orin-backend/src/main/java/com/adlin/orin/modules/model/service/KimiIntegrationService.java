package com.adlin.orin.modules.model.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Kimi (Moonshot) 集成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KimiIntegrationService {

    private final RestTemplate restTemplate;

    @Value("${kimi.default.endpoint:https://api.moonshot.cn/v1}")
    private String defaultEndpoint;

    @Value("${kimi.default.model:moonshot-v1-8k-chat}")
    private String defaultModel;

    /**
     * 测试连接
     */
    public boolean testConnection(String endpointUrl, String apiKey) {
        try {
            String url = buildUrl(endpointUrl, "/models");
            log.info("Testing Kimi connection to: {}", url);
            HttpHeaders headers = createHeaders(apiKey);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            log.info("Kimi connection test response status: {}", response.getStatusCode());
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Kimi connection test failed: {}, endpoint: {}, error type: {}",
                    e.getMessage(), endpointUrl, e.getClass().getSimpleName());
            if (e.getCause() != null) {
                log.warn("Caused by: {}", e.getCause().getMessage());
            }
            return false;
        }
    }

    /**
     * 发送聊天消息
     */
    public Optional<Object> sendMessage(String endpointUrl, String apiKey, String model, String message) {
        try {
            String url = buildUrl(endpointUrl, "/chat/completions");

            HttpHeaders headers = createHeaders(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model != null ? model : defaultModel);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "user", "content", message));
            requestBody.put("messages", messages);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to send message to Kimi: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 发送带完整参数的聊天消息
     */
    public Optional<Object> sendMessageWithFullParams(String endpointUrl, String apiKey, String model,
                                                     List<Map<String, Object>> messages,
                                                     Double temperature, Double topP, Integer maxTokens) {
        try {
            String url = buildUrl(endpointUrl, "/chat/completions");

            HttpHeaders headers = createHeaders(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model != null ? model : defaultModel);
            requestBody.put("messages", messages);

            if (temperature != null) {
                requestBody.put("temperature", temperature);
            }
            if (topP != null) {
                requestBody.put("top_p", topP);
            }
            if (maxTokens != null) {
                requestBody.put("max_tokens", maxTokens);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to send message to Kimi with full params: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 获取可用模型列表
     */
    public Optional<Object> getModels(String endpointUrl, String apiKey) {
        try {
            String url = buildUrl(endpointUrl, "/models");

            HttpHeaders headers = createHeaders(apiKey);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get models from Kimi: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private String buildUrl(String endpointUrl, String path) {
        String baseUrl = endpointUrl != null && !endpointUrl.isEmpty() ? endpointUrl : defaultEndpoint;
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl + path;
    }

    private HttpHeaders createHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
