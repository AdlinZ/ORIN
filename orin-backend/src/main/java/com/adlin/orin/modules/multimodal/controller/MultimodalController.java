package com.adlin.orin.modules.multimodal.controller;

import com.adlin.orin.modules.multimodal.entity.MultimodalFile;
import com.adlin.orin.modules.multimodal.service.MultimodalFileService;
import com.adlin.orin.modules.multimodal.service.VisualAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/multimodal")
@RequiredArgsConstructor
@Tag(name = "Multimodal File Management", description = "多模态文件管理")
public class MultimodalController {

    private final MultimodalFileService fileService;
    private final VisualAnalysisService visualAnalysisService;

    @Operation(summary = "Get available AI models from SiliconFlow")
    @GetMapping("/models")
    public List<Map<String, String>> getModels() {
        return visualAnalysisService.getAvailableModels();
    }

    @Operation(summary = "上传多模态文件")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MultimodalFile uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String uploadedBy) {
        try {
            return fileService.uploadFile(file, uploadedBy);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    @Operation(summary = "获取所有文件列表")
    @GetMapping("/files")
    public List<MultimodalFile> getAllFiles() {
        return fileService.getAllFiles();
    }

    @Operation(summary = "按类型获取文件")
    @GetMapping("/files/type/{fileType}")
    public List<MultimodalFile> getFilesByType(@PathVariable String fileType) {
        return fileService.getFilesByType(fileType.toUpperCase());
    }

    @Operation(summary = "获取文件详情")
    @GetMapping("/files/{fileId}")
    public MultimodalFile getFile(@PathVariable String fileId) {
        return fileService.getFile(fileId);
    }

    @Operation(summary = "下载文件")
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileId) {
        try {
            MultimodalFile file = fileService.getFile(fileId);
            Path filePath = Paths.get(file.getStoragePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + file.getFileName() + "\"")
                        .contentType(MediaType.parseMediaType(file.getMimeType()))
                        .body(resource);
            } else {
                throw new RuntimeException("File not found or not readable");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file: " + e.getMessage(), e);
        }
    }

    @Operation(summary = "获取缩略图")
    @GetMapping("/files/{fileId}/thumbnail")
    public ResponseEntity<Resource> getThumbnail(@PathVariable String fileId) {
        try {
            MultimodalFile file = fileService.getFile(fileId);
            if (file.getThumbnailPath() == null) {
                throw new RuntimeException("Thumbnail not available");
            }

            Path thumbnailPath = Paths.get(file.getThumbnailPath());
            Resource resource = new UrlResource(thumbnailPath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(resource);
            } else {
                throw new RuntimeException("Thumbnail not found");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get thumbnail: " + e.getMessage(), e);
        }
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/files/{fileId}")
    public Map<String, String> deleteFile(@PathVariable String fileId) {
        fileService.deleteFile(fileId);
        return Map.of("status", "deleted", "fileId", fileId);
    }

    @Operation(summary = "获取文件统计信息")
    @GetMapping("/stats")
    public MultimodalFileService.FileStats getStats() {
        return fileService.getStats();
    }
}
