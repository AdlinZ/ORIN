package com.adlin.orin.modules.model.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiliconFlowIntegrationService {

    private final RestTemplate difyRestTemplate;

    /**
     * 测试硅基流动API连接性
     */
    public boolean testConnection(String endpointUrl, String apiKey) {
        String trimmedUrl = endpointUrl != null ? endpointUrl.trim() : "";
        String trimmedKey = apiKey != null ? apiKey.trim() : "";

        try {
            // Remove trailing slash if exists
            if (trimmedUrl.endsWith("/")) {
                trimmedUrl = trimmedUrl.substring(0, trimmedUrl.length() - 1);
            }

            String url = trimmedUrl + "/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(trimmedKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "Qwen/Qwen2-7B-Instruct"); // 使用默认模型
            requestBody.put("messages", Arrays.asList(
                    Map.of("role", "user", "content", "Hello, are you available?")));
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 100);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("API error: {}", response.getStatusCode());
                return false;
            }
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("SiliconFlow auth error: {}", e.getResponseBodyAsString());
            String errorMsg = e.getStatusCode() + " " + e.getStatusText();
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                errorMsg = "API Key 认证失败 (401)，请检查秘钥是否正确且具有该模型权限";
            }
            log.error("硅基流动连接失败: {}", errorMsg);
            return false;
        } catch (Exception e) {
            log.error("SiliconFlow connection test failed: ", e);
            log.error("服务连接异常: {}", (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
            return false;
        }
    }

    /**
     * 获取模型列表
     */
    public Optional<Object> getModels(String endpointUrl, String apiKey) {
        try {
            String url = endpointUrl + "/models";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to fetch models from SiliconFlow: ", e);
        }
        return Optional.empty();
    }

    /**
     * 发送聊天消息到硅基流动API
     */
    public Optional<Object> sendMessage(String endpointUrl, String apiKey, String model, String message) {
        try {
            String url = endpointUrl + "/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", Arrays.asList(
                    Map.of("role", "user", "content", message)));
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 500);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to send message to SiliconFlow: ", e);
        }
        return Optional.empty();
    }

    /**
     * 发送多模态聊天消息到硅基流动API
     * Supports content as a list of maps (OpenAI compatible multimodal format)
     */
    public Optional<Object> sendMultimodalMessage(String endpointUrl, String apiKey, String model,
            List<Map<String, Object>> contentList) {
        try {
            String url = endpointUrl + "/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            // Construct message with array content
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", contentList);

            requestBody.put("messages", Arrays.asList(userMessage));
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 500);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to send multimodal message to SiliconFlow: ", e);
        }
        return Optional.empty();
    }

    /**
     * 使用完整参数向硅基流动API发送消息
     */
    public Optional<Object> sendMessageWithFullParams(String url, String apiKey, String model,
            List<Map<String, Object>> messages,
            double temperature, double topP, int maxTokens) {
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

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = difyRestTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to send message to SiliconFlow with params: ", e);
        }
        return Optional.empty();
    }

    /**
     * 获取嵌入向量
     */
    public Optional<Object> getEmbeddings(String endpointUrl, String apiKey, String model, List<String> input) {
        try {
            String url = endpointUrl + "/embeddings";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", input);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to get embeddings from SiliconFlow: ", e);
        }
        return Optional.empty();
    }

    /**
     * 上传文件到硅基流动API
     */
    public Optional<Object> uploadFile(String endpointUrl, String apiKey,
            org.springframework.web.multipart.MultipartFile file) {
        try {
            String trimmedUrl = endpointUrl != null ? endpointUrl.trim() : "";
            if (trimmedUrl.endsWith("/")) {
                trimmedUrl = trimmedUrl.substring(0, trimmedUrl.length() - 1);
            }

            String url;
            // Fix common URL configuration mistake where /v1 is missing for SiliconFlow
            if (trimmedUrl.contains("siliconflow") && !trimmedUrl.endsWith("/v1")) {
                url = trimmedUrl + "/v1/files";
            } else {
                url = trimmedUrl + "/files";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();

            // Use ByteArrayResource to ensure filename is passed correctly
            org.springframework.core.io.ByteArrayResource fileResource = new org.springframework.core.io.ByteArrayResource(
                    file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            body.add("file", fileResource);
            body.add("purpose", "batch");

            HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            } else {
                log.error("SiliconFlow upload failed with status: {} Body: {}", response.getStatusCode(),
                        response.getBody());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("SiliconFlow upload client error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Failed to upload file to SiliconFlow: ", e);
        }
        return Optional.empty();
    }
}