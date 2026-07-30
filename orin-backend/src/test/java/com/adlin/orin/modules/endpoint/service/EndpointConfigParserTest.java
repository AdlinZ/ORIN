package com.adlin.orin.modules.endpoint.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EndpointConfigParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesJsonObject() throws Exception {
        Map<String, Object> config = EndpointConfigParser.parse(objectMapper,
                "{\"allowedApiKeyIds\":[\"key-1\"]}");

        assertEquals(List.of("key-1"), config.get("allowedApiKeyIds"));
    }

    @Test
    void unwrapsNestedJsonStringsFromCompatibilityDatabase() throws Exception {
        String config = "{\"allowedApiKeyIds\":[\"key-1\"]}";
        String doubleEncoded = objectMapper.writeValueAsString(
                objectMapper.writeValueAsString(config));

        Map<String, Object> parsed = EndpointConfigParser.parse(objectMapper, doubleEncoded);

        assertEquals(List.of("key-1"), parsed.get("allowedApiKeyIds"));
    }

    @Test
    void rejectsNonObjectPayload() {
        assertThrows(IOException.class,
                () -> EndpointConfigParser.parse(objectMapper, "\"[]\""));
    }
}
