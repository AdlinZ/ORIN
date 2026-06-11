package com.adlin.orin.gateway.adapter.impl;

import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.ChatCompletionResponse;
import com.adlin.orin.gateway.dto.EmbeddingRequest;
import com.adlin.orin.gateway.dto.EmbeddingResponse;
import com.adlin.orin.gateway.dto.TranscriptionRequest;
import com.adlin.orin.gateway.dto.TranscriptionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * SiliconFlow ASR 转写 adapter。
 * <p>对接 SiliconFlow 的 OpenAI 兼容 {@code /audio/transcriptions} 端点，专门承载语音转写。
 * <p>不实现 chat / embedding / 流式（仅 ASR），对应方法返回 {@code Mono/Flux.empty()} 让上层识别"该能力不可用"。
 * <p>注册 id 默认 {@code siliconflow-asr}，provider type 同名，由
 * {@code GatewayProviderRefreshService} 在启动时按 siliconflow 凭据构建。
 */
@Slf4j
public class SiliconFlowTranscriptionAdapter implements ProviderAdapter {

    private final String providerId;
    private final String apiKey;
    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SiliconFlowTranscriptionAdapter(String providerId, String apiKey, String baseUrl,
            RestTemplate restTemplate) {
        this.providerId = providerId;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.baseUrl = (baseUrl == null || baseUrl.isBlank())
                ? "https://api.siliconflow.cn/v1"
                : baseUrl.trim().replaceAll("/+$", "");
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getProviderType() {
        return "siliconflow-asr";
    }

    @Override
    public String getProviderName() {
        return "SiliconFlow ASR - " + providerId;
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.fromCallable(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + apiKey);
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<?> entity = new HttpEntity<>(headers);
                @SuppressWarnings("unchecked")
                ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate
                        .exchange(baseUrl + "/models", HttpMethod.GET, entity, Map.class);
                return response.getStatusCode().is2xxSuccessful();
            } catch (Exception e) {
                log.warn("SiliconFlow ASR health check failed at {}: {}", baseUrl, e.getMessage());
                return false;
            }
        });
    }

    @Override
    public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request) {
        return Mono.error(new UnsupportedOperationException(
                "SiliconFlowTranscriptionAdapter does not support chat; use OpenAIProviderAdapter"));
    }

    @Override
    public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest request) {
        return Flux.error(new UnsupportedOperationException(
                "SiliconFlowTranscriptionAdapter does not support chat stream"));
    }

    @Override
    public Mono<EmbeddingResponse> embedding(EmbeddingRequest request) {
        return Mono.error(new UnsupportedOperationException(
                "SiliconFlowTranscriptionAdapter does not support embedding"));
    }

    @Override
    public Mono<Map<String, Object>> getModels() {
        return Mono.just(Map.of("object", "list", "data", java.util.List.of(
                Map.of("id", providerId, "object", "model", "owned_by", "siliconflow"))));
    }

    @Override
    public Map<String, Object> getProviderConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("providerId", providerId);
        config.put("providerType", getProviderType());
        config.put("baseUrl", baseUrl);
        config.put("hasApiKey", !apiKey.isEmpty());
        return config;
    }

    @Override
    public double estimateCost(ChatCompletionRequest request) {
        // ASR 暂不计费成本（与本地 Whisper CLI 一样视为 0）；后续按 token/audio-minute 估算时再扩
        return 0.0;
    }

    @Override
    public Mono<TranscriptionResponse> transcribe(TranscriptionRequest request) {
        return Mono.fromCallable(() -> {
            if (apiKey.isEmpty()) {
                throw new IllegalStateException("SiliconFlow ASR API key not configured");
            }
            if (request == null || request.getAudioUrl() == null || request.getAudioUrl().isBlank()) {
                throw new IllegalArgumentException("audioUrl is required");
            }
            if (request.getModel() == null || request.getModel().isBlank()) {
                throw new IllegalArgumentException("model is required");
            }

            String endpoint = baseUrl + "/audio/transcriptions";
            Map<String, Object> body = new HashMap<>();
            body.put("model", request.getModel());
            body.put("audio_url", request.getAudioUrl());
            // language 透传（SiliconFlow 支持；不传时由 model 自动检测）
            if (request.getLanguage() != null && !request.getLanguage().isBlank()) {
                body.put("language", request.getLanguage());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, entity, String.class);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new RuntimeException("SiliconFlow ASR unexpected response: "
                        + response.getStatusCode());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);
            String text = (String) responseMap.get("text");
            if (text == null) {
                return TranscriptionResponse.builder()
                        .text("")
                        .provider(getProviderType())
                        .model(request.getModel())
                        .language(request.getLanguage())
                        .build();
            }
            return TranscriptionResponse.builder()
                    .text(text.trim())
                    .provider(getProviderType())
                    .model(request.getModel())
                    .language(request.getLanguage())
                    .build();
        }).doOnError(e -> log.error("SiliconFlow ASR transcribe failed: {}", e.getMessage()));
    }
}
