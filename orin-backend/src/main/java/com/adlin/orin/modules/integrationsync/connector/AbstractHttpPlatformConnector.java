package com.adlin.orin.modules.integrationsync.connector;

import com.adlin.orin.modules.integrationsync.model.*;
import com.adlin.orin.modules.integrationsync.spi.PlatformConnector;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractHttpPlatformConnector implements PlatformConnector {

    protected final RestTemplate restTemplate;
    protected final ObjectMapper objectMapper;

    protected HttpHeaders headers(IntegrationConnection connection) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String token = extractToken(connection.getAuthConfig());
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
            headers.set("Authorization", "Bearer " + token);
        }
        return headers;
    }

    protected String extractToken(String authConfig) {
        if (authConfig == null || authConfig.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> auth = objectMapper.readValue(authConfig, new TypeReference<>() {});
            for (String key : List.of("apiKey", "api_key", "token", "bearerToken", "accessToken")) {
                Object value = auth.get(key);
                if (value != null && !String.valueOf(value).isBlank()) {
                    return String.valueOf(value);
                }
            }
        } catch (Exception ignored) {
            // Existing integrations often store the raw API key rather than a JSON object.
        }
        return authConfig;
    }

    protected Map<String, Object> asMap(Object value) {
        return objectMapper.convertValue(value, new TypeReference<>() {});
    }

    protected List<Map<String, Object>> readListPayload(Map<String, Object> body, String... keys) {
        if (body == null) {
            return List.of();
        }
        for (String key : keys) {
            Object value = body.get(key);
            if (value instanceof List<?> list) {
                return list.stream().filter(Objects::nonNull).map(this::asMap).toList();
            }
        }
        if (body.get("data") instanceof Map<?, ?> dataMap) {
            Map<String, Object> data = asMap(dataMap);
            for (String key : keys) {
                Object nested = data.get(key);
                if (nested instanceof List<?> list) {
                    return list.stream().filter(Objects::nonNull).map(this::asMap).toList();
                }
            }
        }
        return List.of();
    }

    protected PushResult unsupportedPush(SyncPushRequest request, String message) {
        List<PushResult.PushResultItem> items = request.getResources().stream()
                .map(resource -> PushResult.PushResultItem.builder()
                        .orinResourceType(resource.getOrinResourceType())
                        .orinResourceId(resource.getOrinResourceId())
                        .externalResourceType(resource.getExternalResourceType())
                        .externalResourceId(resource.getExternalResourceId())
                        .status(SyncStatus.SKIPPED)
                        .message(message)
                        .contentHash(resource.getContentHash())
                        .build())
                .toList();
        return PushResult.builder()
                .success(false)
                .status(SyncStatus.PARTIAL)
                .message(message)
                .items(items)
                .build();
    }

    protected HealthCheckResult defaultHealthCheck(IntegrationConnection connection, String path) {
        if (connection.getBaseUrl() == null || connection.getBaseUrl().isBlank()) {
            return HealthCheckResult.builder().healthy(false).message("Missing baseUrl").build();
        }
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    connection.getBaseUrl() + path,
                    HttpMethod.GET,
                    new HttpEntity<>(headers(connection)),
                    String.class);
            return HealthCheckResult.builder()
                    .healthy(response.getStatusCode().is2xxSuccessful())
                    .message(response.getStatusCode().toString())
                    .details(Map.of("statusCode", response.getStatusCode().value()))
                    .build();
        } catch (Exception ex) {
            log.warn("{} health check failed: {}", platform(), ex.getMessage());
            return HealthCheckResult.builder().healthy(false).message(ex.getMessage()).build();
        }
    }
}
