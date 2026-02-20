package com.adlin.orin.modules.model.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinimaxIntegrationService {

    private final RestTemplate difyRestTemplate;

    /**
     * 测试 MiniMax API 连接性
     */
    public boolean testConnection(String endpointUrl, String apiKey, String model) {
        String trimmedUrl = endpointUrl != null ? endpointUrl.trim() : "https://api.minimaxi.chat/v1";
        String trimmedKey = apiKey != null ? apiKey.trim() : "";

        try {
            if (trimmedUrl.endsWith("/")) {
                trimmedUrl = trimmedUrl.substring(0, trimmedUrl.length() - 1);
            }

            // Using chat/completions for testing as it's more direct
            String url = trimmedUrl + "/text/chatcompletion_v2";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(trimmedKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model != null && !model.isEmpty() ? model : "abab6.5g-chat");
            requestBody.put("messages", Arrays.asList(
                    Map.of("role", "user", "content", "hi")));
            requestBody.put("max_tokens", 10);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("MiniMax connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 发送聊天消息
     */
    public Optional<Object> sendMessage(String endpointUrl, String apiKey, String model,
            List<Map<String, Object>> messages, double temperature) {
        try {
            String trimmedUrl = endpointUrl != null ? endpointUrl.trim() : "https://api.minimaxi.chat/v1";
            if (trimmedUrl.endsWith("/")) {
                trimmedUrl = trimmedUrl.substring(0, trimmedUrl.length() - 1);
            }
            String url = trimmedUrl + "/text/chatcompletion_v2";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.POST, entity, new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to send message to MiniMax: ", e);
        }
        return Optional.empty();
    }

    /**
     * 语音合成 (TTS)
     */
    public Optional<byte[]> generateAudio(String endpointUrl, String apiKey, String model, String input,
            Map<String, Object> params) {
        try {
            // MiniMax T2A V2 endpoint
            String url = "https://api.minimaxi.chat/v1/t2a_v2";
            if (endpointUrl != null && endpointUrl.contains("api-uw.minimax.io")) {
                url = "https://api-uw.minimax.io/v1/t2a_v2";
            } else if (endpointUrl != null && endpointUrl.contains("api.minimax.io")) {
                url = "https://api.minimax.io/v1/t2a_v2";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model != null && !model.isEmpty() ? model : "speech-01-hd");
            requestBody.put("text", input);

            // MiniMax specific tts parameters structure
            if (params.containsKey("voice_id") || params.containsKey("voice")) {
                requestBody.put("voice_id", params.getOrDefault("voice_id", params.get("voice")));
            } else {
                requestBody.put("voice_id", "male-qn-qingse"); // Default voice
            }

            if (params.containsKey("speed"))
                requestBody.put("speed", params.get("speed"));
            if (params.containsKey("vol") || params.containsKey("volume")) {
                requestBody.put("vol", params.getOrDefault("vol", params.get("volume")));
            }
            if (params.containsKey("pitch"))
                requestBody.put("pitch", params.get("pitch"));

            // Audio settings
            Map<String, Object> audioConfig = new HashMap<>();
            audioConfig.put("audio_sample_rate", 32000);
            audioConfig.put("bitrate", 128000);
            audioConfig.put("format", params.getOrDefault("format", "mp3"));
            requestBody.put("audio_setting", audioConfig);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<byte[]> response = difyRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    byte[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.error("MiniMax TTS failed: ", e);
        }
        return Optional.empty();
    }
}
