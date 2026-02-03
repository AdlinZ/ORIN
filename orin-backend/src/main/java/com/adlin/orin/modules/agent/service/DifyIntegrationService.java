package com.adlin.orin.modules.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DifyIntegrationService {

    private final RestTemplate difyRestTemplate;

    @Value("${dify.default.endpoint:http://localhost:3000/v1}")
    private String defaultEndpoint;

    /**
     * 测试Dify API连接性
     */
    public boolean testConnection(String endpointUrl, String apiKey) {
        try {
            String url = buildUrl(endpointUrl, "/console/api/workspaces");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Dify connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取Dify应用列表
     */
    public Optional<Object> getApplications(String endpointUrl, String apiKey) {
        try {
            String url = buildUrl(endpointUrl, "/console/api/apps");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch applications from Dify: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 获取Dify应用参数信息
     */
    public Optional<Object> getAppParameters(String endpointUrl, String apiKey, String appId) {
        try {
            String url = buildUrl(endpointUrl, "/apps/" + appId + "/parameters");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch app parameters from Dify: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 获取应用参数 (App API - /parameters)
     * 不需要 App ID，直接使用 App Key
     */
    public Optional<java.util.Map<String, Object>> fetchAppParameters(String endpointUrl, String apiKey) {
        try {
            String url = buildUrl(endpointUrl, "/parameters");
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response.getStatusCode().is2xxSuccessful())
                return Optional.ofNullable(response.getBody());
        } catch (Exception e) {
            log.warn("Failed to fetch app parameters: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 获取应用元数据 (App API - /meta)
     * 不需要 App ID，直接使用 App Key
     */
    public Optional<java.util.Map<String, Object>> fetchAppMeta(String endpointUrl, String apiKey) {
        try {
            String url = buildUrl(endpointUrl, "/meta");
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response.getStatusCode().is2xxSuccessful())
                return Optional.ofNullable(response.getBody());
        } catch (Exception e) {
            log.warn("Failed to fetch app meta: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 发送消息到Dify应用 (支持对话ID)
     */
    public Optional<Object> sendMessage(String endpointUrl, String apiKey, String conversationId, String message) {
        try {
            String url = buildUrl(endpointUrl, "/chat-messages");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("inputs", new HashMap<>());
            requestBody.put("query", message);
            requestBody.put("response_mode", "blocking"); // 同步响应
            requestBody.put("conversation_id",
                    (conversationId != null && !conversationId.isEmpty()) ? conversationId : "");
            requestBody.put("user", "orin-system");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to send message to Dify: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 获取Dify应用的对话历史
     */
    public Optional<Object> getConversations(String endpointUrl, String apiKey, String appId) {
        try {
            String url = buildUrl(endpointUrl, "/conversations");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch conversations from Dify: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 发送文本补全消息 (Text Generation)
     */
    public Optional<Object> sendCompletion(String endpointUrl, String apiKey, String prompt,
            Map<String, Object> inputs) {
        try {
            String url = buildUrl(endpointUrl, "/completion-messages");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("inputs", inputs != null ? inputs : new HashMap<>());
            // Note: For completion-messages, the prompt is often passed differently or via
            // inputs depending on setup.
            // But standard Dify API for completion usually expects 'inputs' where variables
            // are defined.
            // If 'prompt' is a direct override, it might vary. Standard Dify uses 'inputs'
            // map.
            // We'll rely on inputs mainly, or assume a default key if prompt is raw.
            // For now, let's assume standard behavior:
            requestBody.put("response_mode", "blocking");
            requestBody.put("user", "orin-system");

            // If prompt is provided and inputs are empty, might try to put it in a default
            // key?
            // Safer to just pass inputs as is.

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to send completion to Dify: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 运行工作流 (Workflow)
     */
    public Optional<Object> runWorkflow(String endpointUrl, String apiKey, Map<String, Object> inputs) {
        try {
            String url = buildUrl(endpointUrl, "/workflows/run");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("inputs", inputs != null ? inputs : new HashMap<>());
            requestBody.put("response_mode", "blocking");
            requestBody.put("user", "orin-system");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to run workflow on Dify: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 构建完整的API URL
     */
    private String buildUrl(String endpointUrl, String path) {
        String base = endpointUrl != null ? endpointUrl : defaultEndpoint;

        // 确保基础URL格式正确
        if (!base.startsWith("http")) {
            base = "http://" + base;
        }

        // 处理路径拼接
        if (base.endsWith("/") && path.startsWith("/")) {
            base = base.substring(0, base.length() - 1);
        } else if (!base.endsWith("/") && !path.startsWith("/")) {
            base += "/";
        }

        return base + path;
    }
}