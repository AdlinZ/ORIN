package com.adlin.orin.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

public interface ObjectStorageProvider {
    StorageBackend backend();

    String put(String objectKey, InputStream stream, long size, String contentType, Map<String, String> metadata) throws IOException;

    InputStream get(String locatorOrKey) throws IOException;

    boolean exists(String locatorOrKey);

    void delete(String locatorOrKey);

    String presignGetUrl(String locatorOrKey, Duration ttl);

    Map<String, Object> health();
}

