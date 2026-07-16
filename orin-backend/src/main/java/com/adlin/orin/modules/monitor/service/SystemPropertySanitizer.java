package com.adlin.orin.modules.monitor.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Prevents environment configuration secrets from crossing the management API
 * boundary while still allowing an unchanged masked field to be submitted.
 */
public final class SystemPropertySanitizer {

    public static final String REDACTED_VALUE = "••••••••";

    private SystemPropertySanitizer() {
    }

    public static Map<String, String> sanitizeForRead(Map<String, String> properties) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        properties.forEach((key, value) -> sanitized.put(
                key,
                isSensitiveKey(key) ? REDACTED_VALUE : value));
        return sanitized;
    }

    public static boolean preservesExistingValue(String key, String value) {
        return isSensitiveKey(key) && REDACTED_VALUE.equals(value);
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }

        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("api-key")
                || normalized.contains("api_key")
                || normalized.contains("apikey")
                || normalized.contains("access-key")
                || normalized.contains("access_key");
    }
}
