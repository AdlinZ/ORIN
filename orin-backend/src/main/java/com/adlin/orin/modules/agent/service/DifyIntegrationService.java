package com.adlin.orin.modules.agent.service;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
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

            ResponseEntity<Map> response = difyRestTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
            );

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

            ResponseEntity<Map> response = difyRestTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
            );

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

            ResponseEntity<Map> response = difyRestTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch app parameters from Dify: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 发送消息到Dify应用
     */
    public Optional<Object> sendMessage(String endpointUrl, String apiKey, String appId, String message) {
        try {
            String url = buildUrl(endpointUrl, "/chat-messages");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("inputs", new HashMap<>());
            requestBody.put("query", message);
            requestBody.put("response_mode", "blocking"); // 同步响应
            requestBody.put("conversation_id", ""); // 新对话
            requestBody.put("user", "orin-system");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = difyRestTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );

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

            ResponseEntity<Map> response = difyRestTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch conversations from Dify: {}", e.getMessage());
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