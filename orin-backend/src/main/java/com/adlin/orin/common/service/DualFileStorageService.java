package com.adlin.orin.common.service;

import com.adlin.orin.common.storage.ObjectStorageProvider;
import com.adlin.orin.common.storage.ReplicationStatus;
import com.adlin.orin.common.storage.StorageBackend;
import com.adlin.orin.common.storage.StorageProperties;
import com.adlin.orin.common.storage.StorageProviderRegistry;
import com.adlin.orin.common.storage.StorageReplicationTask;
import com.adlin.orin.common.storage.StorageReplicationTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class DualFileStorageService implements FileStorageService {

    private final StorageProperties storageProperties;
    private final StorageProviderRegistry providerRegistry;
    private final StorageReplicationTaskRepository replicationTaskRepository;

    @Override
    public String storeFile(MultipartFile file, String subDir) throws IOException {
        return storeFileDetailed(file, subDir).locator();
    }

    @Override
    public FileStorageService.StoredFile storeFileDetailed(MultipartFile file, String subDir) throws IOException {
        return storeBytesDetailed(
                file.getBytes(),
                file.getOriginalFilename(),
                subDir,
                file.getContentType());
    }

    @Override
    public FileStorageService.StoredFile storeBytesDetailed(byte[] content, String originalFilename, String subDir, String contentType)
            throws IOException {
        String objectKey = buildObjectKey(subDir, originalFilename);
        String checksum = sha256(content);
        StorageBackend primaryBackend = StorageBackend.from(storageProperties.getPrimary(), StorageBackend.LOCAL);
        StorageBackend secondaryBackend = StorageBackend.from(storageProperties.getSecondary(), StorageBackend.MINIO);
        ObjectStorageProvider primaryProvider = providerRegistry.provider(primaryBackend);
        ObjectStorageProvider secondaryProvider = providerRegistry.provider(secondaryBackend);

        String primaryLocator = primaryProvider.put(
                objectKey,
                new ByteArrayInputStream(content),
                content.length,
                normalizeContentType(contentType),
                Map.of("checksum", checksum));

        String replicationStatus = ReplicationStatus.SYNCED.name();
        String replicationError = null;
        String replicaBackends = secondaryBackend.name().toLowerCase();

        if ("dual".equalsIgnoreCase(storageProperties.getMode()) && primaryBackend != secondaryBackend) {
            try {
                secondaryProvider.put(
                        objectKey,
                        new ByteArrayInputStream(content),
                        content.length,
                        normalizeContentType(contentType),
                        Map.of("checksum", checksum));
            } catch (Exception ex) {
                replicationStatus = ReplicationStatus.PENDING_REPAIR.name();
                replicationError = trimError(ex.getMessage());
                if (storageProperties.isWriteAsyncRepair()) {
                    enqueueRepair(objectKey, primaryBackend, secondaryBackend, primaryLocator, null, replicationError);
                }
                log.warn("Secondary storage write failed for {}: {}", objectKey, ex.getMessage());
            }
        }

        return new FileStorageService.StoredFile(
                primaryLocator,
                objectKey,
                primaryBackend.name().toLowerCase(),
                replicaBackends,
                replicationStatus,
                replicationError,
                checksum,
                normalizeContentType(contentType),
                content.length);
    }

    @Override
    public InputStream openStream(String locator) throws IOException {
        if (locator == null || locator.isBlank()) {
            throw new IOException("Storage locator is blank");
        }
        StorageBackend preferred = backendFromLocator(locator, StorageBackend.from(storageProperties.getPrimary(), StorageBackend.LOCAL));
        ObjectStorageProvider primary = providerRegistry.provider(preferred);
        try {
            return primary.get(locator);
        } catch (Exception primaryError) {
            if (!storageProperties.isReadFallback()) {
                throw asIo(primaryError);
            }
            StorageBackend fallbackBackend = preferred == StorageBackend.LOCAL ? StorageBackend.MINIO : StorageBackend.LOCAL;
            ObjectStorageProvider fallback = providerRegistry.provider(fallbackBackend);
            try {
                return fallback.get(locator);
            } catch (Exception fallbackError) {
                throw asIo(fallbackError);
            }
        }
    }

    @Override
    public Path materializeToLocalTemp(String locator, String filenameHint) throws IOException {
        if (locator == null || locator.isBlank()) {
            throw new IOException("Storage locator is blank");
        }
        if (looksLikeLocalPath(locator)) {
            Path p = Path.of(locator);
            if (Files.exists(p)) {
                return p;
            }
        }
        String suffix = safeSuffix(filenameHint);
        Path tmp = Files.createTempFile("orin-storage-", suffix);
        try (InputStream in = openStream(locator)) {
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp;
    }

    @Override
    public boolean exists(String locator) {
        StorageBackend preferred = backendFromLocator(locator, StorageBackend.from(storageProperties.getPrimary(), StorageBackend.LOCAL));
        ObjectStorageProvider primary = providerRegistry.provider(preferred);
        if (primary.exists(locator)) {
            return true;
        }
        if (!storageProperties.isReadFallback()) {
            return false;
        }
        StorageBackend fallbackBackend = preferred == StorageBackend.LOCAL ? StorageBackend.MINIO : StorageBackend.LOCAL;
        return providerRegistry.provider(fallbackBackend).exists(locator);
    }

    @Override
    public String generateDownloadUrl(String locator, Duration ttl) {
        StorageBackend preferred = backendFromLocator(locator, StorageBackend.from(storageProperties.getPrimary(), StorageBackend.LOCAL));
        String url = providerRegistry.provider(preferred).presignGetUrl(locator, ttl);
        if (url != null) {
            return url;
        }
        if (!storageProperties.isReadFallback()) {
            return null;
        }
        StorageBackend fallbackBackend = preferred == StorageBackend.LOCAL ? StorageBackend.MINIO : StorageBackend.LOCAL;
        return providerRegistry.provider(fallbackBackend).presignGetUrl(locator, ttl);
    }

    @Override
    public Map<String, Object> healthSnapshot() {
        StorageBackend primaryBackend = StorageBackend.from(storageProperties.getPrimary(), StorageBackend.LOCAL);
        StorageBackend secondaryBackend = StorageBackend.from(storageProperties.getSecondary(), StorageBackend.MINIO);
        Map<String, Object> primary = providerRegistry.provider(primaryBackend).health();
        Map<String, Object> secondary = providerRegistry.provider(secondaryBackend).health();
        return Map.of(
                "mode", storageProperties.getMode(),
                "readFallback", storageProperties.isReadFallback(),
                "writeAsyncRepair", storageProperties.isWriteAsyncRepair(),
                "primary", primary,
                "secondary", secondary,
                "replicationQueue", Map.of(
                        "pending", replicationTaskRepository.countByStatus(ReplicationStatus.PENDING_REPAIR.name()),
                        "failed", replicationTaskRepository.countByStatus(ReplicationStatus.REPAIR_FAILED.name())
                )
        );
    }

    @Override
    public void deleteFile(String locator) {
        if (locator == null || locator.isBlank()) {
            return;
        }
        StorageBackend primaryBackend = StorageBackend.from(storageProperties.getPrimary(), StorageBackend.LOCAL);
        StorageBackend secondaryBackend = StorageBackend.from(storageProperties.getSecondary(), StorageBackend.MINIO);
        providerRegistry.provider(primaryBackend).delete(locator);
        if ("dual".equalsIgnoreCase(storageProperties.getMode()) && primaryBackend != secondaryBackend) {
            providerRegistry.provider(secondaryBackend).delete(locator);
        }
    }

    private void enqueueRepair(
            String objectKey,
            StorageBackend source,
            StorageBackend target,
            String sourceLocator,
            String targetLocator,
            String err) {
        StorageReplicationTask task = StorageReplicationTask.builder()
                .entityType("GENERIC_FILE")
                .entityId(null)
                .objectKey(objectKey)
                .sourceBackend(source.name())
                .targetBackend(target.name())
                .sourceLocator(sourceLocator)
                .targetLocator(targetLocator)
                .status(ReplicationStatus.PENDING_REPAIR.name())
                .maxRetries(storageProperties.getRepair().getMaxRetries())
                .lastError(trimError(err))
                .nextRetryAt(LocalDateTime.now())
                .build();
        replicationTaskRepository.save(task);
    }

    private String buildObjectKey(String subDir, String originalFilename) {
        String normalizedSubdir = (subDir == null ? "misc" : subDir).replace("\\", "/");
        normalizedSubdir = normalizedSubdir.startsWith("/") ? normalizedSubdir.substring(1) : normalizedSubdir;
        String safeName = sanitizeFilename(originalFilename);
        LocalDate now = LocalDate.now();
        return "%s/%d/%02d/%s-%s".formatted(
                normalizedSubdir,
                now.getYear(),
                now.getMonthValue(),
                UUID.randomUUID(),
                safeName
        );
    }

    private String sanitizeFilename(String filename) {
        String f = StringUtils.hasText(filename) ? filename : "file.bin";
        f = f.replace("\\", "_").replace("/", "_").replace("..", "_");
        return f.length() > 120 ? f.substring(f.length() - 120) : f;
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
    }

    private String trimError(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.length() <= 500 ? raw : raw.substring(0, 500);
    }

    private IOException asIo(Exception ex) {
        return ex instanceof IOException io ? io : new IOException(ex.getMessage(), ex);
    }

    private StorageBackend backendFromLocator(String locator, StorageBackend fallback) {
        if (locator == null) {
            return fallback;
        }
        if (locator.startsWith("minio:")) {
            return StorageBackend.MINIO;
        }
        if (looksLikeLocalPath(locator)) {
            return StorageBackend.LOCAL;
        }
        return fallback;
    }

    private boolean looksLikeLocalPath(String locator) {
        try {
            if (locator.startsWith("local:")) {
                return true;
            }
            Path p = Path.of(locator);
            return p.isAbsolute() || locator.startsWith("./") || locator.startsWith("../");
        } catch (Exception e) {
            return false;
        }
    }

    private String safeSuffix(String filenameHint) {
        if (!StringUtils.hasText(filenameHint) || !filenameHint.contains(".")) {
            return ".tmp";
        }
        String ext = filenameHint.substring(filenameHint.lastIndexOf('.'));
        return ext.length() <= 10 ? ext : ".tmp";
    }

    private String sha256(byte[] bytes) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception ex) {
            throw new IOException("Failed to compute checksum", ex);
        }
    }
}
