package com.adlin.orin.modules.multimodal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * OCR 服务
 * 支持本地 Tesseract 和云服务（SiliconFlow VLM）
 */
@Slf4j
@Service
public class OcrService {

    @Value("${siliconflow.api.key:}")
    private String siliconFlowApiKey;

    @Value("${siliconflow.api.base-url:https://api.siliconflow.cn/v1}")
    private String siliconFlowBaseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OcrService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 使用 SiliconFlow VLM 进行 OCR 文字识别
     *
     * @param imageUrl 图片 URL 或 Base64 data URI
     * @return 识别的文字
     */
    public String ocrWithSiliconFlowVlm(String imageUrl) {
        return ocrWithSiliconFlowVlm(imageUrl, "Qwen/Qwen2-VL-7B-Instruct");
    }

    /**
     * 使用 SiliconFlow VLM 进行 OCR 文字识别
     *
     * @param imageUrl 图片 URL 或 Base64 data URI
     * @param model 模型名称
     * @return 识别的文字
     */
    public String ocrWithSiliconFlowVlm(String imageUrl, String model) {
        if (siliconFlowApiKey == null || siliconFlowApiKey.isEmpty()) {
            log.warn("SiliconFlow API key not configured, OCR failed");
            return "[OCR Error] SiliconFlow API key not configured";
        }

        try {
            String endpoint = siliconFlowBaseUrl + "/chat/completions";

            // 构建消息
            List<Map<String, Object>> messages = new ArrayList<>();

            // 系统消息
            Map<String, Object> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", "You are an OCR service. Extract ALL text from the image. " +
                    "Return ONLY the extracted text, nothing else. " +
                    "If no text is found, return '[NO_TEXT_DETECTED]'.");
            messages.add(systemMsg);

            // 用户消息（带图片）
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");

            List<Map<String, Object>> content = new ArrayList<>();

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", "Please extract all text from this image:");
            content.add(textPart);

            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "image_url");

            Map<String, Object> imageUrlObj = new HashMap<>();
            if (imageUrl.startsWith("data:")) {
                // Base64 图片
                imageUrlObj.put("url", imageUrl);
            } else {
                // URL 图片
                imageUrlObj.put("url", imageUrl);
            }
            imagePart.put("image_url", imageUrlObj);
            content.add(imagePart);

            userMsg.put("content", content);
            messages.add(userMsg);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 4096);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(siliconFlowApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 发送请求
            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);

                // 解析响应
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    String contentText = (String) message.get("content");

                    if (contentText != null && !contentText.contains("[NO_TEXT_DETECTED]")) {
                        log.info("SiliconFlow VLM OCR succeeded, extracted {} chars", contentText.length());
                        return contentText.trim();
                    } else {
                        log.info("SiliconFlow VLM OCR: no text detected");
                        return "";
                    }
                }
            }

            log.warn("SiliconFlow VLM OCR returned unexpected response");
            return "[OCR Error] Unexpected response from SiliconFlow";

        } catch (Exception e) {
            log.error("SiliconFlow VLM OCR failed: {}", e.getMessage(), e);
            return "[OCR Error] " + e.getMessage();
        }
    }

    /**
     * 使用阿里云 OCR API 进行文字识别
     *
     * @param imageUrl 图片 URL
     * @param accessKeyId AK
     * @param accessKeySecret SK
     * @return 识别的文字
     */
    public String ocrWithAliCloud(String imageUrl, String accessKeyId, String accessKeySecret) {
        // TODO: 实现阿里云 OCR 集成
        // 阿里云 OCR API: https://help.aliyun.com/document_detail/297.html
        log.info("AliCloud OCR not implemented yet");
        return "[OCR Error] AliCloud OCR not implemented";
    }

    /**
     * 使用腾讯云 OCR API 进行文字识别
     *
     * @param imageUrl 图片 URL
     * @param secretId Secret ID
     * @param secretKey Secret Key
     * @return 识别的文字
     */
    public String ocrWithTencentCloud(String imageUrl, String secretId, String secretKey) {
        // TODO: 实现腾讯云 OCR 集成
        // 腾讯云 OCR API: https://cloud.tencent.com/document/product/866/33524
        log.info("TencentCloud OCR not implemented yet");
        return "[OCR Error] TencentCloud OCR not implemented";
    }

    /**
     * 使用百度 OCR API 进行文字识别
     *
     * @param imageUrl 图片 URL 或 Base64
     * @param apiKey API Key
     * @param secretKey Secret Key
     * @return 识别的文字
     */
    public String ocrWithBaidu(String imageUrl, String apiKey, String secretKey) {
        // TODO: 实现百度 OCR 集成
        // 百度 OCR API: https://cloud.baidu.com/doc/OCRAPI/sdk_python
        log.info("Baidu OCR not implemented yet");
        return "[OCR Error] Baidu OCR not implemented";
    }
}
