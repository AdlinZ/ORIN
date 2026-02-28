package com.adlin.orin.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class LocalFileStorageServiceImpl implements FileStorageService {

    private final Path fileStorageLocation;

    // Allowed file extensions for upload (configure via application.properties)
    @Value("${file.upload.allowed-extensions:.jpg,.jpeg,.png,.gif,.pdf,.doc,.docx,.txt,.md,.json,.xml,.csv,.xls,.xlsx,.ppt,.pptx}")
    private Set<String> allowedExtensions;

    // Blocked file extensions (dangerous file types)
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".exe", ".bat", ".cmd", ".sh", ".bash", ".ps1", ".vbs", ".js", ".jar",
            ".class", ".jsp", ".asp", ".php", ".phtml", ".htaccess", ".htpasswd",
            ".sql", ".db", ".sqlite", ".mdb", ".env", ".config", ".ini"
    );

    public LocalFileStorageServiceImpl() {
        this.fileStorageLocation = Paths.get("storage/uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    @Override
    public String storeFile(MultipartFile file, String subDir) throws IOException {
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());

        // Validate filename
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }

        // Check for path traversal
        if (originalFileName.contains("..") || originalFileName.contains("/") || originalFileName.contains("\\")) {
            throw new IllegalArgumentException("Filename contains invalid path sequence");
        }

        String extension = "";
        int i = originalFileName.lastIndexOf('.');
        if (i > 0) {
            extension = originalFileName.substring(i).toLowerCase(); // includes dot, normalize to lowercase
        }

        // Check if extension is blocked
        if (BLOCKED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File type " + extension + " is not allowed for security reasons");
        }

        // Check if extension is in allowed list (if configured)
        if (allowedExtensions != null && !allowedExtensions.isEmpty() && !allowedExtensions.contains(extension)) {
            throw new IllegalArgumentException("File type " + extension + " is not in the allowed list");
        }

        // Generate safe filename with UUID
        String fileName = UUID.randomUUID().toString() + extension;

        try {
            Path targetDir = this.fileStorageLocation.resolve(subDir);
            Files.createDirectories(targetDir);

            Path targetLocation = targetDir.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // Return relative path including subDir
            return Paths.get(subDir, fileName).toString();
        } catch (IOException ex) {
            throw new IOException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }

    @Override
    public void deleteFile(String path) {
        if (path == null)
            return;
        try {
            Path filePath = this.fileStorageLocation.resolve(path).normalize();
            Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            log.error("Could not delete file: {}", path, ex);
        }
    }
}
