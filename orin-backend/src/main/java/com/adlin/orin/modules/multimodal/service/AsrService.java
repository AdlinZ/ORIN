package com.adlin.orin.modules.multimodal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * ASR（自动语音识别）服务
 * 支持本地 Whisper 和云服务（SiliconFlow ASR）
 */
@Slf4j
@Service
public class AsrService {

    @Value("${siliconflow.api.key:}")
    private String siliconFlowApiKey;

    @Value("${siliconflow.api.base-url:https://api.siliconflow.cn/v1}")
    private String siliconFlowBaseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AsrService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 使用 SiliconFlow ASR 进行语音识别
     *
     * @param audioPath 音频文件路径
     * @return 识别的文字
     */
    public String transcribeWithSiliconFlowAsr(String audioPath) {
        return transcribeWithSiliconFlowAsr(audioPath, "openai/whisper-large-v3-turbo");
    }

    /**
     * 使用 SiliconFlow ASR 进行语音识别
     *
     * @param audioPath 音频文件路径
     * @param model 模型名称
     * @return 识别的文字
     */
    public String transcribeWithSiliconFlowAsr(String audioPath, String model) {
        if (siliconFlowApiKey == null || siliconFlowApiKey.isEmpty()) {
            log.warn("SiliconFlow API key not configured, ASR failed");
            return "[ASR Error] SiliconFlow API key not configured";
        }

        try {
            Path audioFile = Path.of(audioPath);
            if (!Files.exists(audioFile)) {
                log.warn("Audio file not found: {}", audioPath);
                return "[ASR Error] Audio file not found: " + audioPath;
            }

            // 读取音频文件并转为 Base64
            byte[] audioBytes = Files.readAllBytes(audioFile);
            String mimeType = Files.probeContentType(audioFile);
            if (mimeType == null) {
                mimeType = "audio/mpeg";
            }
            String base64Audio = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(audioBytes);

            String endpoint = siliconFlowBaseUrl + "/audio/transcriptions";

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("audio_url", base64Audio);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(siliconFlowApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 发送请求
            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);

                String text = (String) responseMap.get("text");
                if (text != null && !text.trim().isEmpty()) {
                    log.info("SiliconFlow ASR succeeded, extracted {} chars", text.length());
                    return text.trim();
                } else {
                    log.info("SiliconFlow ASR: no speech detected");
                    return "";
                }
            }

            log.warn("SiliconFlow ASR returned unexpected response");
            return "[ASR Error] Unexpected response from SiliconFlow";

        } catch (Exception e) {
            log.error("SiliconFlow ASR failed: {}", e.getMessage(), e);
            return "[ASR Error] " + e.getMessage();
        }
    }

    /**
     * 使用本地 Whisper CLI 进行语音识别
     *
     * @param audioPath 音频文件路径
     * @return 识别的文字
     */
    public String transcribeWithWhisperCli(String audioPath) {
        try {
            Path audioFile = Path.of(audioPath);
            if (!Files.exists(audioFile)) {
                return "[ASR Error] Audio file not found: " + audioPath;
            }

            // 尝试使用 Whisper CLI
            ProcessBuilder pb = new ProcessBuilder(
                    "whisper",
                    audioPath,
                    "--model", "large-v3",
                    "--language", "auto",
                    "--output_format", "json"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode == 0 && !output.isEmpty()) {
                // 解析 Whisper JSON 输出
                Map<String, Object> result = objectMapper.readValue(output, Map.class);
                String text = (String) result.get("text");
                if (text != null) {
                    log.info("Whisper CLI ASR succeeded, extracted {} chars", text.length());
                    return text.trim();
                }
            }

            log.warn("Whisper CLI ASR not available or failed");
            return "[ASR Error] Whisper CLI not available";

        } catch (Exception e) {
            log.warn("Whisper CLI ASR failed: {}", e.getMessage());
            return "[ASR Error] " + e.getMessage();
        }
    }

    /**
     * 使用阿里云 ASR API 进行语音识别
     *
     * @param audioPath 音频文件路径
     * @param accessKeyId AK
     * @param accessKeySecret SK
     * @return 识别的文字
     */
    public String transcribeWithAliCloud(String audioPath, String accessKeyId, String accessKeySecret) {
        // TODO: 实现阿里云 ASR 集成
        // 阿里云 ASR API: https://help.aliyun.com/document_detail/306441.html
        log.info("AliCloud ASR not implemented yet");
        return "[ASR Error] AliCloud ASR not implemented";
    }

    /**
     * 使用腾讯云 ASR API 进行语音识别
     *
     * @param audioPath 音频文件路径
     * @param secretId Secret ID
     * @param secretKey Secret Key
     * @return 识别的文字
     */
    public String transcribeWithTencentCloud(String audioPath, String secretId, String secretKey) {
        // TODO: 实现腾讯云 ASR 集成
        // 腾讯云 ASR API: https://cloud.tencent.com/document/product/1093/37850
        log.info("TencentCloud ASR not implemented yet");
        return "[ASR Error] TencentCloud ASR not implemented";
    }

    /**
     * 使用讯飞 ASR API 进行语音识别
     *
     * @param audioPath 音频文件路径
     * @param appId 讯飞 AppId
     * @param apiKey 讯飞 API Key
     * @return 识别的文字
     */
    public String transcribeWithXunFei(String audioPath, String appId, String apiKey) {
        // TODO: 实现讯飞 ASR 集成
        // 讯飞 ASR API: https://www.xfyun.cn/doc/asr/online_asr/Chinese.html
        log.info("XunFei ASR not implemented yet");
        return "[ASR Error] XunFei ASR not implemented";
    }
}