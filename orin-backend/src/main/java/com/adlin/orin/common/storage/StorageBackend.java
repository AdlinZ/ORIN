package com.adlin.orin.common.storage;

public enum StorageBackend {
    LOCAL,
    MINIO;

    public static StorageBackend from(String value, StorageBackend fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return StorageBackend.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}

