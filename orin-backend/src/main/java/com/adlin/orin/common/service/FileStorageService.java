package com.adlin.orin.common.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

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
     * Delete a file
     */
    void deleteFile(String path);
}
