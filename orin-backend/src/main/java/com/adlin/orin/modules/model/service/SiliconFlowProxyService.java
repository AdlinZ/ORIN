package com.adlin.orin.modules.model.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * SiliconFlow API 代理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiliconFlowProxyService {

    private final RestTemplate restTemplate;

    @Value("${siliconflow.api.key:}")
    private String apiKey;

    @Value("${siliconflow.api.base-url:https://api.siliconflow.cn/v1}")
    private String baseUrl;

    /**
     * 获取可用模型列表
     */
    public List<Map<String, Object>> getModels() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/models",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                return data != null ? data : Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Failed to get models from SiliconFlow", e);
        }
        
        return Collections.emptyList();
    }

    /**
     * 创建聊天完成
     */
    public Map<String, Object> createChatCompletion(Map<String, Object> requestBody) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/chat/completions",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to create chat completion", e);
        }
        
        return Map.of("error", "Request failed");
    }

    /**
     * 创建 Embedding
     */
    public Map<String, Object> createEmbedding(String input, String model) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            Map<String, Object> requestBody = Map.of(
                    "input", input,
                    "model", model
            );
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/embeddings",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to create embedding", e);
        }
        
        return Map.of("error", "Request failed");
    }
}
