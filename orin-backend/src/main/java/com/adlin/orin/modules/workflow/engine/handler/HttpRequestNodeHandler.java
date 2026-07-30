package com.adlin.orin.modules.workflow.engine.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component("httpRequestNodeHandler")
@RequiredArgsConstructor
public class HttpRequestNodeHandler implements NodeHandler {

    private final RestTemplate restTemplate;

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        String url = stringValue(nodeData.get("url"));
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL required for HTTP request node");
        }

        HttpMethod method = HttpMethod.valueOf(
                stringValue(nodeData.getOrDefault("method", "GET")).toUpperCase());
        HttpHeaders headers = new HttpHeaders();
        Object rawHeaders = nodeData.get("headers");
        if (rawHeaders instanceof Map<?, ?> headerMap) {
            headerMap.forEach((key, value) -> headers.add(String.valueOf(key), String.valueOf(value)));
        }

        Object body = resolveBody(nodeData);
        log.info("HttpRequestNode executing {} {}", method, url);
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                method,
                new HttpEntity<>(body, headers),
                String.class);

        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("statusCode", response.getStatusCode().value());
        outputs.put("headers", response.getHeaders());
        outputs.put("body", response.getBody());
        outputs.put("success", response.getStatusCode().is2xxSuccessful());
        return NodeExecutionResult.success(outputs);
    }

    private Object resolveBody(Map<String, Object> nodeData) {
        Object body = nodeData.get("body");
        if (body != null) {
            return body;
        }

        Object bodyContent = nodeData.get("body_content");
        if (bodyContent != null && !String.valueOf(bodyContent).isBlank()) {
            return bodyContent;
        }

        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
