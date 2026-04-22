package com.adlin.orin.common.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

public interface FileStorageService {
    /**
     * Store a file and return its relative path/identifier
     * 
     * @param file   The file to store
     * @param subDir The subdirectory (e.g. "avatars", "documents")
     * @return The stored file path
     */
    String storeFile(MultipartFile file, String subDir) throws IOException;

    /**
     * Store a file with detailed replication metadata.
     */
    StoredFile storeFileDetailed(MultipartFile file, String subDir) throws IOException;

    /**
     * Store bytes with detailed replication metadata.
     */
    StoredFile storeBytesDetailed(byte[] content, String originalFilename, String subDir, String contentType)
            throws IOException;

    /**
     * Open a stream from a storage locator or legacy local path.
     */
    InputStream openStream(String locator) throws IOException;

    /**
     * Resolve a storage locator to a local temporary file when a parser needs local
     * filesystem access.
     */
    Path materializeToLocalTemp(String locator, String filenameHint) throws IOException;

    /**
     * Check whether an object exists in storage.
     */
    boolean exists(String locator);

    /**
     * Generate a short-lived download URL (pre-signed for object storage).
     * Returns null when the active backend does not support URL signing.
     */
    String generateDownloadUrl(String locator, Duration ttl);

    /**
     * Basic health snapshot for storage backends and repair queue.
     */
    Map<String, Object> healthSnapshot();

    /**
     * Delete a file
     */
    void deleteFile(String path);

    /**
     * Stored file result with storage metadata.
     */
    record StoredFile(
            String locator,
            String objectKey,
            String primaryBackend,
            String replicaBackends,
            String replicationStatus,
            String replicationError,
            String checksum,
            String contentType,
            long size) {
    }
}
