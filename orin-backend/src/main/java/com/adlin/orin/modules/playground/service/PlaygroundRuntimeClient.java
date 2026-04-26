package com.adlin.orin.modules.playground.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaygroundRuntimeClient {

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static RestTemplate buildRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    @Value("${orin.ai-engine.url:http://localhost:8000}")
    private String aiEngineUrl;

    @Value("${orin.playground.runtime.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${orin.playground.runtime.read-timeout-ms:420000}")
    private int readTimeoutMs;

    @PostConstruct
    void initRestTemplate() {
        restTemplate = buildRestTemplate(connectTimeoutMs, readTimeoutMs);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> run(Map<String, Object> payload) {
        String path = "/api/playground/runtime/runs";
        String primaryUrl = aiEngineUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(
                    primaryUrl, entity, (Class<Map<String, Object>>) (Class<?>) Map.class);
            if (response.getBody() == null) {
                throw new IllegalStateException("ai-engine returned empty response body");
            }
            return response.getBody();
        } catch (Exception primaryError) {
            if (aiEngineUrl.contains("localhost")) {
                String fallbackUrl = aiEngineUrl.replace("localhost", "127.0.0.1") + path;
                log.warn("Playground runtime primary url failed ({}), retrying with {}", primaryUrl, fallbackUrl);
                ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(
                        fallbackUrl, entity, (Class<Map<String, Object>>) (Class<?>) Map.class);
                if (response.getBody() == null) {
                    throw new IllegalStateException("ai-engine fallback url returned empty response body");
                }
                return response.getBody();
            }
            throw primaryError;
        }
    }

    public Map<String, Object> runStream(Map<String, Object> payload, BiConsumer<String, Object> eventConsumer) {
        String path = "/api/playground/runtime/runs/stream";
        String primaryUrl = aiEngineUrl + path;
        try {
            return runStreamAt(primaryUrl, payload, eventConsumer);
        } catch (Exception primaryError) {
            if (aiEngineUrl.contains("localhost")) {
                String fallbackUrl = aiEngineUrl.replace("localhost", "127.0.0.1") + path;
                log.warn("Playground runtime stream primary url failed ({}), retrying with {}", primaryUrl, fallbackUrl);
                return runStreamAt(fallbackUrl, payload, eventConsumer);
            }
            throw primaryError;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runStreamAt(String url, Map<String, Object> payload, BiConsumer<String, Object> eventConsumer) {
        AtomicReference<Map<String, Object>> finalResult = new AtomicReference<>();
        restTemplate.execute(url, org.springframework.http.HttpMethod.POST, request -> {
            request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            objectMapper.writeValue(request.getBody(), payload);
        }, response -> {
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("ai-engine stream failed: HTTP " + response.getStatusCode());
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                String eventName = "message";
                StringBuilder data = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        handleSseFrame(eventName, data.toString(), eventConsumer, finalResult);
                        eventName = "message";
                        data.setLength(0);
                        continue;
                    }
                    if (line.startsWith(":")) {
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        eventName = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        if (data.length() > 0) data.append('\n');
                        data.append(line.substring(5).trim());
                    }
                }
                handleSseFrame(eventName, data.toString(), eventConsumer, finalResult);
            }
            return null;
        });
        Map<String, Object> result = finalResult.get();
        if (result == null) {
            throw new IllegalStateException("ai-engine stream returned no final result");
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void handleSseFrame(String eventName, String data,
                                BiConsumer<String, Object> eventConsumer,
                                AtomicReference<Map<String, Object>> finalResult) {
        if (data == null || data.isBlank()) return;
        Object parsed;
        try {
            parsed = objectMapper.readValue(data, Object.class);
        } catch (Exception e) {
            parsed = data;
        }
        if (eventConsumer != null && !"final".equals(eventName)) {
            eventConsumer.accept(eventName, parsed);
        }
        if ("final".equals(eventName) && parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            finalResult.set(result);
        }
        if ("error".equals(eventName)) {
            String message = "ai-engine stream error";
            if (parsed instanceof Map<?, ?> map && map.get("message") != null) {
                message = String.valueOf(map.get("message"));
            } else if (parsed != null) {
                message = String.valueOf(parsed);
            }
            throw new IllegalStateException(message);
        }
    }
}
