package com.adlin.orin.modules.settings.support;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared response contract for configuration secrets.
 *
 * <p>The fixed mask means “a value is configured, but it is not returned”. When
 * the same mask is submitted by an edit form, the persistence layer must keep
 * the existing value instead of writing the mask.</p>
 */
public final class SecretConfigSanitizer {

    public static final String MASKED_VALUE = "********";

    private SecretConfigSanitizer() {
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("apikey")
                || normalized.contains("accesskey")
                || normalized.contains("privatekey")
                || normalized.endsWith("token");
    }

    public static boolean isMaskedValue(Object value) {
        return MASKED_VALUE.equals(value == null ? null : String.valueOf(value));
    }

    public static String redact(String key, String value) {
        if (!isSensitiveKey(key) || value == null || value.isBlank()) {
            return value;
        }
        return MASKED_VALUE;
    }

    public static Map<String, Object> redactCopy(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        source.forEach((key, value) -> result.put(
                key,
                isSensitiveKey(key) && value != null && !String.valueOf(value).isBlank()
                        ? MASKED_VALUE
                        : value
        ));
        return result;
    }
}
