package com.adlin.orin.modules.endpoint.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses Endpoint configuration persisted by JSON-capable and compatibility databases.
 *
 * <p>Some compatibility drivers return a JSON column as a JSON string containing the
 * original object. Unwrap a bounded number of textual layers before requiring an object.
 */
public final class EndpointConfigParser {

    private static final int MAX_TEXTUAL_LAYERS = 8;

    private EndpointConfigParser() {
    }

    public static Map<String, Object> parse(ObjectMapper objectMapper, String rawConfig)
            throws IOException {
        JsonNode node = objectMapper.readTree(rawConfig);
        int layer = 0;
        while (node != null && node.isTextual() && layer < MAX_TEXTUAL_LAYERS) {
            node = objectMapper.readTree(node.textValue());
            layer++;
        }
        if (node == null || !node.isObject()) {
            throw new IOException("Endpoint config must be a JSON object");
        }
        return objectMapper.convertValue(node,
                new TypeReference<LinkedHashMap<String, Object>>() {
                });
    }
}
