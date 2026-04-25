package com.adlin.orin.modules.playground.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaygroundRuntimeClient {

    private RestTemplate restTemplate;

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
}
