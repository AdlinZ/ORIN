package com.adlin.orin.modules.monitor.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemPropertySanitizerTest {

    @Test
    void masksCredentialsBeforeTheyReachTheManagementUi() {
        Map<String, String> sanitized = SystemPropertySanitizer.sanitizeForRead(Map.of(
                "spring.datasource.password", "database-secret",
                "milvus.token", "service-token",
                "storage.minio.access-key", "access-id",
                "spring.rabbitmq.host", "mq.internal"));

        assertEquals(SystemPropertySanitizer.REDACTED_VALUE, sanitized.get("spring.datasource.password"));
        assertEquals(SystemPropertySanitizer.REDACTED_VALUE, sanitized.get("milvus.token"));
        assertEquals(SystemPropertySanitizer.REDACTED_VALUE, sanitized.get("storage.minio.access-key"));
        assertEquals("mq.internal", sanitized.get("spring.rabbitmq.host"));
    }

    @Test
    void retainsAnExistingSecretWhenTheUiPostsItsMaskedPlaceholder() {
        assertTrue(SystemPropertySanitizer.preservesExistingValue(
                "spring.datasource.password", SystemPropertySanitizer.REDACTED_VALUE));
        assertFalse(SystemPropertySanitizer.preservesExistingValue(
                "spring.datasource.password", "a-new-secret"));
        assertFalse(SystemPropertySanitizer.preservesExistingValue(
                "spring.rabbitmq.host", SystemPropertySanitizer.REDACTED_VALUE));
    }
}
