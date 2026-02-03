package com.adlin.orin.modules.model.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiliconFlowIntegrationService {

    private final RestTemplate difyRestTemplate;

    /**
     * 测试硅基流动API连接性
     */
    @CircuitBreaker(name = "siliconFlow")
    @Retry(name = "siliconFlow")
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

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

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
        return getModels(endpointUrl, apiKey, null, null);
    }

    /**
     * 获取带筛选的模型列表
     */
    public Optional<Object> getModels(String endpointUrl, String apiKey, String type, String subType) {
        try {
            String url = endpointUrl + "/models";
            if (type != null || subType != null) {
                url += "?";
                if (type != null)
                    url += "type=" + type;
                if (subType != null)
                    url += (type != null ? "&" : "") + "sub_type=" + subType;
            }

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
            log.error("Failed to fetch models from SiliconFlow: ", e);
        }
        return Optional.empty();
    }

    /**
     * 根据 API 筛选功能确定模型类型
     */
    public String resolveSiliconFlowViewType(String endpointUrl, String apiKey, String modelId) {
        if (modelId == null)
            return "CHAT";

        log.info("Resolving SiliconFlow model type for: {}", modelId);

        // 1. 检查是否为视频模型
        if (isModelInSubType(endpointUrl, apiKey, modelId, "text-to-video")) {
            return "TTV";
        }
        // 2. 检查是否为语音识别
        if (isModelInSubType(endpointUrl, apiKey, modelId, "speech-to-text")) {
            return "STT";
        }
        // 3. 检查是否为语音合成
        if (isModelInSubType(endpointUrl, apiKey, modelId, "text-to-speech")) {
            return "TTS";
        }
        // 4. 检查是否为图像生成
        if (isModelInSubType(endpointUrl, apiKey, modelId, "text-to-image") ||
                isModelInSubType(endpointUrl, apiKey, modelId, "image-to-image")) {
            return "TTI";
        }
        // 5. 检查是否为文本模型 (默认对话)
        if (isModelInType(endpointUrl, apiKey, modelId, "text")) {
            return "CHAT";
        }

        return "CHAT"; // 最终兜底
    }

    private boolean isModelInSubType(String endpointUrl, String apiKey, String modelId, String subType) {
        return isModelInList(getModels(endpointUrl, apiKey, null, subType), modelId);
    }

    private boolean isModelInType(String endpointUrl, String apiKey, String modelId, String type) {
        return isModelInList(getModels(endpointUrl, apiKey, type, null), modelId);
    }

    private boolean isModelInList(Optional<Object> modelsResponse, String modelId) {
        if (modelsResponse.isEmpty())
            return false;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) modelsResponse.get();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
            if (data == null)
                return false;
            return data.stream()
                    .anyMatch(m -> modelId.equals(m.get("id")));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 发送聊天消息到硅基流动API
     */
    @CircuitBreaker(name = "siliconFlow")
    @Retry(name = "siliconFlow")
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
            log.error("Failed to send message to SiliconFlow: ", e);
        }
        return Optional.empty();
    }

    /**
     * 发送多模态聊天消息到硅基流动API
     * Supports content as a list of maps (OpenAI compatible multimodal format)
     */
    @CircuitBreaker(name = "siliconFlow")
    @Retry(name = "siliconFlow")
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
        return sendMessageWithFullParams(url, apiKey, model, messages, temperature, topP, maxTokens, null, null);
    }

    /**
     * 使用完整参数向硅基流动API发送消息 (支持深度思考)
     */
    public Optional<Object> sendMessageWithFullParams(String url, String apiKey, String model,
            List<Map<String, Object>> messages,
            double temperature, double topP, int maxTokens,
            Boolean enableThinking, Integer thinkingBudget) {
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

            // Add thinking parameters if enabled (for DeepSeek R1, etc.)
            if (enableThinking != null && enableThinking) {
                requestBody.put("enable_thinking", true);
                if (thinkingBudget != null && thinkingBudget > 0) {
                    requestBody.put("thinking_budget", thinkingBudget);
                }
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.POST, entity, new ParameterizedTypeReference<Map<String, Object>>() {
                    });

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
    @CircuitBreaker(name = "siliconFlow")
    @Retry(name = "siliconFlow")
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

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

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

    /**
     * 生成图像 - 调用硅基流动的文生图API
     */
    @CircuitBreaker(name = "siliconFlow")
    @Retry(name = "siliconFlow")
    public Optional<Object> generateImage(String endpointUrl, String apiKey, String model, String prompt,
            Map<String, Object> params) {
        try {
            String trimmedUrl = endpointUrl != null ? endpointUrl.trim() : "";
            if (trimmedUrl.endsWith("/")) {
                trimmedUrl = trimmedUrl.substring(0, trimmedUrl.length() - 1);
            }

            // SiliconFlow image generation endpoint
            String url;
            if (trimmedUrl.contains("siliconflow") && !trimmedUrl.endsWith("/v1")) {
                url = trimmedUrl + "/v1/images/generations";
            } else {
                url = trimmedUrl + "/images/generations";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("prompt", prompt);

            // 添加可选参数
            requestBody.put("image_size", params.getOrDefault("image_size", "1024x1024"));
            requestBody.put("batch_size", params.getOrDefault("batch_size", 1));
            requestBody.put("num_inference_steps", params.getOrDefault("num_inference_steps", 20));
            requestBody.put("guidance_scale", params.getOrDefault("guidance_scale", 7.5));

            if (params.containsKey("negative_prompt")) {
                requestBody.put("negative_prompt", params.get("negative_prompt"));
            }
            if (params.containsKey("seed")) {
                requestBody.put("seed", params.get("seed"));
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("Calling SiliconFlow image generation API: {} with model: {}", url, model);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Image generation successful");
                return Optional.of(response.getBody());
            } else {
                log.error("Image generation failed with status: {}", response.getStatusCode());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("SiliconFlow image generation client error: {} - {}", e.getStatusCode(),
                    e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Failed to generate image with SiliconFlow: ", e);
        }
        return Optional.empty();
    }

    /**
     * 语音转文字 - 调用硅基流动的语音识别API
     */
    @CircuitBreaker(name = "siliconFlow")
    @Retry(name = "siliconFlow")
    public Optional<Object> transcribeAudio(String endpointUrl, String apiKey, String model, byte[] audioData,
            String filename) {
        try {
            String trimmedUrl = endpointUrl != null ? endpointUrl.trim() : "";
            if (trimmedUrl.endsWith("/")) {
                trimmedUrl = trimmedUrl.substring(0, trimmedUrl.length() - 1);
            }

            String url;
            if (trimmedUrl.contains("siliconflow") && !trimmedUrl.endsWith("/v1")) {
                url = trimmedUrl + "/v1/audio/transcriptions";
            } else {
                url = trimmedUrl + "/audio/transcriptions";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();

            org.springframework.core.io.ByteArrayResource audioResource = new org.springframework.core.io.ByteArrayResource(
                    audioData) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            body.add("file", audioResource);
            body.add("model", model);

            HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            log.info("Calling SiliconFlow audio transcription API: {} with model: {}", url, model);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Audio transcription successful");
                return Optional.of(response.getBody());
            } else {
                log.error("Audio transcription failed with status: {}", response.getStatusCode());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("SiliconFlow audio transcription client error: {} - {}", e.getStatusCode(),
                    e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Failed to transcribe audio with SiliconFlow: ", e);
        }
        return Optional.empty();
    }

    /**
     * 语音合成 (TTS) - 调用硅基流动的语音合成 API
     * POST /v1/audio/speech
     */
    // @CircuitBreaker(name = "siliconFlow")
    // @Retry(name = "siliconFlow")
    public Optional<byte[]> generateAudio(String endpointUrl, String apiKey, String model, String input,
            Map<String, Object> params) {
        try {
            String trimmedUrl = endpointUrl != null ? endpointUrl.trim() : "";
            if (trimmedUrl.endsWith("/")) {
                trimmedUrl = trimmedUrl.substring(0, trimmedUrl.length() - 1);
            }

            String url;
            if (trimmedUrl.contains("siliconflow") && !trimmedUrl.endsWith("/v1")) {
                url = trimmedUrl + "/v1/audio/speech";
            } else {
                url = trimmedUrl + "/audio/speech";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", input);

            // Optional parameters
            if (params.containsKey("voice"))
                requestBody.put("voice", params.get("voice"));
            if (params.containsKey("speed"))
                requestBody.put("speed", params.get("speed"));
            if (params.containsKey("gain"))
                requestBody.put("gain", params.get("gain"));
            if (params.containsKey("response_format"))
                requestBody.put("response_format", params.get("response_format"));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("Calling SiliconFlow audio speech API: {} with model: {}", url, model);

            // Expecting binary response (byte array)
            ResponseEntity<byte[]> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    byte[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Audio generation successful, size: {} bytes", response.getBody().length);
                return Optional.of(response.getBody());
            } else {
                log.error("Audio generation failed with status: {}", response.getStatusCode());
                throw new RuntimeException("Audio generation failed with status: " + response.getStatusCode());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("SiliconFlow audio speech client error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("SiliconFlow API Error: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Failed to generate speech with SiliconFlow: ", e);
            throw new RuntimeException("Failed to generate speech: " + e.getMessage(), e);
        }
    }

    /**
     * 生成视频 - 调用硅基流动的视频提交 API
     * POST /v1/video/submit
     */
    public Optional<Object> generateVideo(String endpointUrl, String apiKey, String model, String prompt,
            Map<String, Object> params) {
        try {
            String url = SiliconFlowUrlUtils.buildUrl(endpointUrl, "/video/submit");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey != null ? apiKey.trim() : "");
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "Mozilla/5.0");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("prompt", prompt);

            // image_size is required by SiliconFlow
            // Valid options: 1280x720, 720x1280, 960x960
            String ratio = (String) params.getOrDefault("videoSize", "16:9");
            String imageSize = "1280x720";
            if ("9:16".equals(ratio))
                imageSize = "720x1280";
            else if ("1:1".equals(ratio))
                imageSize = "960x960";
            requestBody.put("image_size", imageSize);

            // Optional parameters
            if (params.containsKey("negative_prompt"))
                requestBody.put("negative_prompt", params.get("negative_prompt"));
            if (params.containsKey("seed"))
                requestBody.put("seed", params.get("seed"));

            // For I2V models, the parameter is 'image'
            if (params.containsKey("reference_image")) {
                requestBody.put("image", params.get("reference_image"));
            } else if (params.containsKey("image")) {
                requestBody.put("image", params.get("image"));
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            logCurrentRequestAsCurl(url, "POST", headers, requestBody);
            log.info("Calling SiliconFlow video submit API: {} with model: {}", url, model);

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
            log.error("Failed to submit video task to SiliconFlow: ", e);
        }
        return Optional.empty();
    }

    /**
     * 获取视频任务状态 - 注意 SiliconFlow 需要 POST 请求且参数在 Body 中
     * POST /v1/video/status
     */
    public Optional<Object> getVideoJobStatus(String endpointUrl, String apiKey, String requestId) {
        HttpURLConnection connection = null;
        try {
            String urlStr = SiliconFlowUrlUtils.buildUrl(endpointUrl, "/video/status");
            URL url = new URL(urlStr);

            String finalApiKey = apiKey != null ? apiKey.trim() : "";
            // Log curl for debugging
            Map<String, String> debugBody = new HashMap<>();
            debugBody.put("requestId", requestId);
            // Reusing the log logic just for visibility, though we build request manually
            // below
            HttpHeaders debugHeaders = new HttpHeaders();
            debugHeaders.setBearerAuth(finalApiKey);
            debugHeaders.setContentType(MediaType.APPLICATION_JSON);
            debugHeaders.set("User-Agent", "Mozilla/5.0");
            logCurrentRequestAsCurl(urlStr, "POST", debugHeaders, debugBody);

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + finalApiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            // Manual JSON construction to be absolutely safe
            String jsonBody = "{\"requestId\":\"" + requestId + "\"}";

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();

            InputStream stream;
            if (responseCode >= 200 && responseCode < 300) {
                stream = connection.getInputStream();
            } else {
                stream = connection.getErrorStream();
            }

            String responseBody = "";
            if (stream != null) {
                try (java.util.Scanner scanner = new java.util.Scanner(stream,
                        java.nio.charset.StandardCharsets.UTF_8.name())) {
                    responseBody = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                }
            }

            if (responseCode >= 200 && responseCode < 300) {
                // Manual JSON parsing using Jackson ObjectMapper since we have it on classpath
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> result = mapper.readValue(responseBody, Map.class);
                return Optional.of(result);
            } else {
                // Handle 404 / 20063
                if (responseCode == 404 && responseBody.contains("20063")) {
                    log.info("SiliconFlow task sync pending (20063) [HttpURLConnection]: {}. Retrying...",
                            responseBody);
                    Map<String, Object> errorBody = new HashMap<>();
                    errorBody.put("status", "TaskNotFound");
                    errorBody.put("reason", "Task is initialized, waiting for sync...");
                    return Optional.of(errorBody);
                }

                log.error("SiliconFlow status API error ({}): {}", responseCode, responseBody);
                Map<String, Object> errorBody = new HashMap<>();
                errorBody.put("status", "FAILED");
                errorBody.put("reason", "API Error: " + responseCode + " " + responseBody);
                return Optional.of(errorBody);
            }

        } catch (Exception e) {
            log.warn("Failed to get video job status [HttpURLConnection]: {}", e.getMessage());
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("status", "FAILED");
            errorBody.put("reason", "Network/Connection error: " + e.getMessage());
            return Optional.of(errorBody);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void logCurrentRequestAsCurl(String url, String method, HttpHeaders headers, Map<String, ?> body) {
        StringBuilder curl = new StringBuilder("curl -X ").append(method).append(" ");
        curl.append("'").append(url).append("'");

        headers.forEach((k, v) -> {
            v.forEach(val -> {
                curl.append(" -H '").append(k).append(": ").append(val).append("'");
            });
        });

        try {
            if (body != null && !body.isEmpty()) {
                String jsonBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
                curl.append(" -d '").append(jsonBody).append("'");
            }
        } catch (Exception e) {
            curl.append(" -d '[JSON Serialization Failed]'");
        }

        log.info("👇 [DEBUG CURL] Copy and run this in terminal to verify:\n{}\n", curl.toString());
    }
}