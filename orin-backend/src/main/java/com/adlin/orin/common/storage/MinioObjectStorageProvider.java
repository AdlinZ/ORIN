package com.adlin.orin.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("minioObjectStorageProvider")
@RequiredArgsConstructor
public class MinioObjectStorageProvider implements ObjectStorageProvider {

    private static final String PREFIX = "minio:";
    private final StorageProperties storageProperties;
    private volatile MinioClient minioClient;

    private MinioClient client() {
        if (minioClient == null) {
            synchronized (this) {
                if (minioClient == null) {
                    minioClient = MinioClient.builder()
                            .endpoint(storageProperties.getMinio().getEndpoint())
                            .credentials(storageProperties.getMinio().getAccessKey(), storageProperties.getMinio().getSecretKey())
                            .build();
                }
            }
        }
        return minioClient;
    }

    private String bucket() {
        return storageProperties.getMinio().getBucket();
    }

    private String keyFromLocator(String locatorOrKey) {
        if (locatorOrKey == null) {
            return null;
        }
        return locatorOrKey.startsWith(PREFIX) ? locatorOrKey.substring(PREFIX.length()) : locatorOrKey;
    }

    private void ensureBucket() throws Exception {
        boolean exists = client().bucketExists(BucketExistsArgs.builder().bucket(bucket()).build());
        if (!exists) {
            client().makeBucket(MakeBucketArgs.builder().bucket(bucket()).build());
        }
    }

    @Override
    public StorageBackend backend() {
        return StorageBackend.MINIO;
    }

    @Override
    public String put(String objectKey, InputStream stream, long size, String contentType, Map<String, String> metadata)
            throws IOException {
        try {
            ensureBucket();
            Map<String, String> userMeta = metadata == null ? Map.of() : metadata;
            client().putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket())
                            .object(keyFromLocator(objectKey))
                            .stream(stream, size, -1)
                            .contentType(contentType == null ? "application/octet-stream" : contentType)
                            .userMetadata(userMeta)
                            .build());
            return PREFIX + keyFromLocator(objectKey);
        } catch (Exception e) {
            throw new IOException("Failed to put object to MinIO", e);
        }
    }

    @Override
    public InputStream get(String locatorOrKey) throws IOException {
        try {
            return client().getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket())
                            .object(keyFromLocator(locatorOrKey))
                            .build());
        } catch (Exception e) {
            throw new IOException("Failed to get object from MinIO", e);
        }
    }

    @Override
    public boolean exists(String locatorOrKey) {
        try {
            client().statObject(StatObjectArgs.builder().bucket(bucket()).object(keyFromLocator(locatorOrKey)).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void delete(String locatorOrKey) {
        try {
            client().removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket())
                            .object(keyFromLocator(locatorOrKey))
                            .build());
        } catch (Exception e) {
            log.warn("Failed to delete object from MinIO {}: {}", locatorOrKey, e.getMessage());
        }
    }

    @Override
    public String presignGetUrl(String locatorOrKey, Duration ttl) {
        try {
            int seconds = (int) Math.max(1, Math.min(ttl.getSeconds(), 60L * 60L * 24L * 7L));
            return client().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket())
                            .object(keyFromLocator(locatorOrKey))
                            .expiry(seconds)
                            .build());
        } catch (Exception e) {
            log.warn("Failed to presign MinIO object {}: {}", locatorOrKey, e.getMessage());
            return null;
        }
    }

    @Override
    public Map<String, Object> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("backend", backend().name().toLowerCase());
        status.put("bucket", bucket());
        try {
            ensureBucket();
            status.put("up", true);
        } catch (Exception e) {
            status.put("up", false);
            status.put("error", e.getMessage());
        }
        return status;
    }
}

