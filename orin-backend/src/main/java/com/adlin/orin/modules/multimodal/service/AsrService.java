package com.adlin.orin.modules.multimodal.service;

import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.TranscriptionRequest;
import com.adlin.orin.gateway.dto.TranscriptionResponse;
import com.adlin.orin.gateway.service.RouterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * ASR（自动语音识别）服务。
 * <p>对外只暴露 {@link #transcribe(String, String)}，由 model 名决定路由：
 * <ul>
 *   <li>model 名称包含 {@code whisper}（大小写不敏感）→ 走本地 Whisper CLI</li>
 *   <li>其它 → 走 {@link RouterService#selectProviderByType} 选 transcribe adapter，由
 *       adapter（如 {@code SiliconFlowTranscriptionAdapter}）调 {@code ProviderAdapter.transcribe}</li>
 * </ul>
 * <p>历史三家云厂商（aliCloud / tencent / xunfei）stub 已删除；新增转写能力以
 * {@code ProviderAdapter} 子接口或新 provider 形式注入。
 */
@Slf4j
@Service
public class AsrService {

    private static final String TRANSCRIPTION_PROVIDER_TYPE = "siliconflow-asr";

    private final RouterService routerService;
    private final ObjectMapper objectMapper;

    public AsrService(RouterService routerService, ObjectMapper objectMapper) {
        this.routerService = routerService;
        this.objectMapper = objectMapper;
    }

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
        return transcribeViaProvider(audioPath, model);
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
     * 通过 RouterService 选 transcribe adapter，调 {@code provider.transcribe()}。
     * <p>为符合 {@code RouterService.selectLowestCost} 成本估算接口，构造一个 dummy
     * ChatCompletionRequest（仅 model 字段）作为 routing-only DTO，不参与实际请求。
     */
    private String transcribeViaProvider(String audioPath, String model) {
        Path audioFile = Path.of(audioPath);
        if (!Files.exists(audioFile)) {
            log.warn("Audio file not found: {}", audioPath);
            return "[ASR Error] Audio file not found: " + audioPath;
        }

        String audioUrl;
        try {
            byte[] audioBytes = Files.readAllBytes(audioFile);
            String mimeType = Files.probeContentType(audioFile);
            if (mimeType == null) {
                mimeType = "audio/mpeg";
            }
            audioUrl = "data:" + mimeType + ";base64,"
                    + Base64.getEncoder().encodeToString(audioBytes);
        } catch (Exception e) {
            log.error("Failed to read audio file: {}", e.getMessage(), e);
            return "[ASR Error] " + e.getMessage();
        }

        TranscriptionRequest transcriptionRequest = TranscriptionRequest.builder()
                .model(model)
                .audioUrl(audioUrl)
                .build();

        ChatCompletionRequest routingDummy = ChatCompletionRequest.builder()
                .model(model)
                .build();
        Optional<ProviderAdapter> providerOpt =
                routerService.selectProviderByType(TRANSCRIPTION_PROVIDER_TYPE, routingDummy);
        if (providerOpt.isEmpty()) {
            log.warn("No healthy transcription provider available (type={}, model={})",
                    TRANSCRIPTION_PROVIDER_TYPE, model);
            return "[ASR Error] No healthy provider for transcription model " + model;
        }

        ProviderAdapter provider = providerOpt.get();
        try {
            TranscriptionResponse response = provider.transcribe(transcriptionRequest).block();
            if (response == null) {
                log.warn("Transcription provider {} returned null response", provider.getProviderName());
                return "[ASR Error] Empty response from provider " + provider.getProviderName();
            }
            String text = response.getText();
            if (text == null) {
                return "";
            }
            log.info("ASR via {} succeeded, model={}, chars={}",
                    provider.getProviderName(), model, text.length());
            return text;
        } catch (Exception e) {
            log.error("ASR via {} failed for model={}: {}",
                    provider.getProviderName(), model, e.getMessage(), e);
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
