package com.adlin.orin.modules.zeroclaw.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * ZeroClaw HTTP 客户端
 * 用于与轻量化 Agent 服务通信
 */
@Slf4j
@Component
public class ZeroClawClient {

    private final RestTemplate restTemplate;

    public ZeroClawClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 测试与 ZeroClaw 的连接
     */
    public boolean testConnection(String endpointUrl, String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (accessToken != null && !accessToken.isEmpty()) {
                headers.setBearerAuth(accessToken);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    endpointUrl + "/health",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("ZeroClaw connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 发送监控数据给 ZeroClaw 进行分析
     */
    public Map<String, Object> requestAnalysis(String endpointUrl, String accessToken,
                                                String analysisType, Map<String, Object> data) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (accessToken != null && !accessToken.isEmpty()) {
                headers.setBearerAuth(accessToken);
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("analysisType", analysisType);
            requestBody.put("data", data);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    endpointUrl + "/api/analyze",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            return null;
        } catch (Exception e) {
            log.error("ZeroClaw analysis request failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 请求 ZeroClaw 执行主动维护操作
     */
    public Map<String, Object> requestSelfHealing(String endpointUrl, String accessToken,
                                                   String actionType, Map<String, Object> params) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (accessToken != null && !accessToken.isEmpty()) {
                headers.setBearerAuth(accessToken);
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("action", actionType);
            requestBody.put("params", params);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    endpointUrl + "/api/self-healing",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            return null;
        } catch (Exception e) {
            log.error("ZeroClaw self-healing request failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取 ZeroClaw 状态
     */
    public Map<String, Object> getStatus(String endpointUrl, String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (accessToken != null && !accessToken.isEmpty()) {
                headers.setBearerAuth(accessToken);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    endpointUrl + "/status",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to get ZeroClaw status: {}", e.getMessage());
            return null;
        }
    }
}
