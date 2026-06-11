package com.adlin.orin.modules.multimodal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * ASR（自动语音识别）服务。
 * <p>对外只暴露 {@link #transcribe(String, String)}，由 model 名决定路由：
 * <ul>
 *   <li>model 名称包含 {@code whisper}（大小写不敏感）→ 走本地 Whisper CLI</li>
 *   <li>其它 → 走 SiliconFlow ASR HTTP（与历史契约一致）</li>
 * </ul>
 * <p>历史三家云厂商（aliCloud / tencent / xunfei）stub 已删除；后续若需新增，
 * 应以 {@code ProviderAdapter} 子接口或新 provider 形式注入，不要在本类罗列厂商。
 * <p>ProviderAdapter.transcribe 通道的实装（{@code SiliconFlowTranscriptionAdapter}）
 * 留待后续切片，本刀仍由本服务直接发 HTTP。
 */
@Slf4j
@Service
public class AsrService {

    @Value("${siliconflow.api.key:}")
    private String siliconFlowApiKey;

    @Value("${siliconflow.api.base-url:https://api.siliconflow.cn/v1}")
    private String siliconFlowBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 用指定 ASR 模型转写音频。
     *
     * @param audioPath 音频文件路径
     * @param model     ASR 模型名（如 {@code openai/whisper-large-v3-turbo} 或 {@code whisper-large-v3}）
     * @return 识别文本；失败时返回形如 {@code [ASR Error] ...} 的字符串，与历史契约一致
     */
    public String transcribe(String audioPath, String model) {
        if (audioPath == null || audioPath.isBlank()) {
            return "[ASR Error] audioPath is required";
        }
        if (model == null || model.isBlank()) {
            return "[ASR Error] model is required";
        }

        if (isWhisperModel(model)) {
            return transcribeWithWhisperCli(audioPath);
        }
        return transcribeWithSiliconFlowAsr(audioPath, model);
    }

    /**
     * 判断模型名是否走本地 Whisper CLI：包含 "whisper" 子串即视为本地路径。
     * <p>注意：当前 AudioParser 走 cloud 路径时传入的 model 也是 whisper 系列
     * （如 {@code openai/whisper-large-v3-turbo}），但该路径会命中本判断并错误地走 CLI。
     * AudioParser 已知此约定（本地走 {@code parseWithWhisper}，不走 AsrService），
     * 故实际不会发生。如未来需要"云 Whisper"，由调用方在 model 前缀上做区分。
     */
    private boolean isWhisperModel(String model) {
        return model.toLowerCase().contains("whisper");
    }

    /**
     * 用 SiliconFlow ASR 转写音频。
     */
    @SuppressWarnings("unchecked")
    private String transcribeWithSiliconFlowAsr(String audioPath, String model) {
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

            byte[] audioBytes = Files.readAllBytes(audioFile);
            String mimeType = Files.probeContentType(audioFile);
            if (mimeType == null) {
                mimeType = "audio/mpeg";
            }
            String base64Audio = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(audioBytes);

            String endpoint = siliconFlowBaseUrl + "/audio/transcriptions";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("audio_url", base64Audio);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(siliconFlowApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

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
     * 用本地 Whisper CLI 转写音频。
     */
    @SuppressWarnings("unchecked")
    private String transcribeWithWhisperCli(String audioPath) {
        try {
            Path audioFile = Path.of(audioPath);
            if (!Files.exists(audioFile)) {
                return "[ASR Error] Audio file not found: " + audioPath;
            }

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
}
