package com.adlin.orin.common.service;

import com.adlin.orin.common.storage.ObjectStorageProvider;
import com.adlin.orin.common.storage.StorageBackend;
import com.adlin.orin.common.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component("localObjectStorageProvider")
@RequiredArgsConstructor
public class LocalFileStorageServiceImpl implements ObjectStorageProvider {

    private static final String PREFIX = "local:";
    private final StorageProperties storageProperties;

    private Path root() {
        Path p = Paths.get(storageProperties.getLocal().getRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize local storage root: " + p, e);
        }
        return p;
    }

    private String keyFromLocator(String locatorOrKey) {
        if (locatorOrKey == null) {
            return null;
        }
        if (locatorOrKey.startsWith(PREFIX)) {
            return locatorOrKey.substring(PREFIX.length());
        }
        String normalized = locatorOrKey.replace("\\", "/");
        String configuredRoot = storageProperties.getLocal().getRoot();
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            String normalizedRoot = configuredRoot.replace("\\", "/");
            normalizedRoot = normalizedRoot.startsWith("./") ? normalizedRoot.substring(2) : normalizedRoot;
            if (normalized.startsWith(normalizedRoot + "/")) {
                return normalized.substring(normalizedRoot.length() + 1);
            }
        }
        Path p = Paths.get(locatorOrKey);
        if (p.isAbsolute()) {
            try {
                return root().relativize(p.normalize()).toString();
            } catch (Exception ignore) {
                return p.getFileName().toString();
            }
        }
        return locatorOrKey;
    }

    private Path resolve(String locatorOrKey) {
        String key = keyFromLocator(locatorOrKey);
        Path p = root().resolve(key).normalize();
        if (!p.startsWith(root())) {
            throw new IllegalArgumentException("Invalid local storage key");
        }
        return p;
    }

    @Override
    public StorageBackend backend() {
        return StorageBackend.LOCAL;
    }

    @Override
    public String put(String objectKey, InputStream stream, long size, String contentType, Map<String, String> metadata)
            throws IOException {
        Path target = resolve(objectKey);
        Files.createDirectories(target.getParent());
        Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        return target.toString();
    }

    @Override
    public InputStream get(String locatorOrKey) throws IOException {
        return Files.newInputStream(resolve(locatorOrKey));
    }

    @Override
    public boolean exists(String locatorOrKey) {
        try {
            return Files.exists(resolve(locatorOrKey));
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void delete(String locatorOrKey) {
        try {
            Files.deleteIfExists(resolve(locatorOrKey));
        } catch (IOException ex) {
            log.warn("Failed to delete local object {}: {}", locatorOrKey, ex.getMessage());
        }
    }

    @Override
    public String presignGetUrl(String locatorOrKey, Duration ttl) {
        return null;
    }

    @Override
    public Map<String, Object> health() {
        Path root = root();
        boolean writable = Files.isWritable(root);
        return Map.of(
                "backend", backend().name().toLowerCase(),
                "root", root.toString(),
                "writable", writable,
                "up", writable
        );
    }
}
