package com.adlin.orin.modules.multimodal.service;

import com.adlin.orin.common.service.FileStorageService;
import com.adlin.orin.modules.multimodal.entity.MultimodalFile;
import com.adlin.orin.modules.multimodal.repository.MultimodalFileRepository;
import org.slf4j.MDC;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import com.adlin.orin.modules.knowledge.entity.KnowledgeTask;
import com.adlin.orin.modules.knowledge.event.TaskCreatedEvent;
import com.adlin.orin.common.enums.TaskStatus;
import com.adlin.orin.modules.knowledge.repository.KnowledgeTaskRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 多模态文件管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultimodalFileService {

    private final MultimodalFileRepository fileRepository;
    private final KnowledgeTaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FileStorageService fileStorageService;

    private static final int THUMBNAIL_SIZE = 200;

    /**
     * 上传多模态文件
     */
    @Transactional
    public MultimodalFile uploadFile(MultipartFile file, String uploadedBy) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("Invalid filename");
        }

        // 确定文件类型
        String mimeType = file.getContentType();
        String fileType = determineFileType(mimeType);

        String fileExtension = getFileExtension(originalFilename);
        var stored = fileStorageService.storeFileDetailed(file, "multimodal/" + fileType.toLowerCase());

        // 创建文件记录
        MultimodalFile.MultimodalFileBuilder builder = MultimodalFile.builder()
                .fileName(originalFilename)
                .fileType(fileType)
                .mimeType(mimeType)
                .fileSize(file.getSize())
                .storagePath(stored.locator())
                .objectKey(stored.objectKey())
                .primaryBackend(stored.primaryBackend())
                .replicaBackends(stored.replicaBackends())
                .replicationStatus(stored.replicationStatus())
                .lastReplicatedAt(LocalDateTime.now())
                .lastReplicationError(stored.replicationError())
                .checksum(stored.checksum())
                .uploadedBy(uploadedBy != null ? uploadedBy : "system");

        // 处理图片
        if ("IMAGE".equals(fileType)) {
            processImage(file, fileExtension, builder);
        }

        MultimodalFile multimodalFile = builder.build();
        multimodalFile = fileRepository.save(multimodalFile);

        // Trigger Async Analysis
        triggerAnalysisTask(multimodalFile);

        log.info("Uploaded multimodal file: {} (type: {})", originalFilename, fileType);
        return multimodalFile;
    }

    /**
     * 上传二进制文件 (内部调用)
     */
    @Transactional
    public MultimodalFile uploadFile(byte[] data, String originalFilename, String mimeType, String uploadedBy)
            throws IOException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("File content is empty");
        }

        String fileType = determineFileType(mimeType);

        String fileExtension = getFileExtension(originalFilename);
        var stored = fileStorageService.storeBytesDetailed(data, originalFilename, "multimodal/" + fileType.toLowerCase(), mimeType);

        // 创建文件记录
        MultimodalFile.MultimodalFileBuilder builder = MultimodalFile.builder()
                .fileName(originalFilename)
                .fileType(fileType)
                .mimeType(mimeType)
                .fileSize((long) data.length)
                .storagePath(stored.locator())
                .objectKey(stored.objectKey())
                .primaryBackend(stored.primaryBackend())
                .replicaBackends(stored.replicaBackends())
                .replicationStatus(stored.replicationStatus())
                .lastReplicatedAt(LocalDateTime.now())
                .lastReplicationError(stored.replicationError())
                .checksum(stored.checksum())
                .uploadedBy(uploadedBy != null ? uploadedBy : "system");

        // 暂时不处理二进制图片的缩略图生成

        MultimodalFile multimodalFile = builder.build();
        multimodalFile = fileRepository.save(multimodalFile);

        // Trigger Async Analysis
        triggerAnalysisTask(multimodalFile);

        log.info("Uploaded generated file: {} (type: {})", originalFilename, fileType);
        return multimodalFile;

    }

    private void triggerAnalysisTask(MultimodalFile file) {
        if ("IMAGE".equals(file.getFileType())) {
            KnowledgeTask task = KnowledgeTask.builder()
                    .assetId(file.getId())
                    .assetType("MULTIMODAL_FILE")
                    .taskType("CAPTIONING")
                    .status(TaskStatus.PENDING)
                    .build();
            taskRepository.save(task);
            eventPublisher.publishEvent(new TaskCreatedEvent(this, task.getId(), MDC.get("traceId")));

            file.setEmbeddingStatus("PENDING");
            fileRepository.save(file);
        }
    }

    /**
     * 获取所有文件
     */
    public List<MultimodalFile> getAllFiles() {
        return fileRepository.findAll();
    }

    /**
     * 按类型获取文件
     */
    public List<MultimodalFile> getFilesByType(String fileType) {
        return fileRepository.findByFileTypeOrderByUploadedAtDesc(fileType);
    }

    /**
     * 获取文件详情
     */
    public MultimodalFile getFile(String fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found: " + fileId));
    }

    /**
     * 删除文件
     */
    @Transactional
    public void deleteFile(String fileId) {
        MultimodalFile file = getFile(fileId);

        // 删除物理文件
        try {
            fileStorageService.deleteFile(file.getStoragePath());
            if (file.getThumbnailPath() != null) {
                fileStorageService.deleteFile(file.getThumbnailPath());
            }
        } catch (Exception e) {
            log.warn("Failed to delete physical file: {}", file.getStoragePath(), e);
        }

        fileRepository.delete(file);
        log.info("Deleted multimodal file: {}", fileId);
    }

    /**
     * 获取文件统计
     */
    public FileStats getStats() {
        long totalFiles = fileRepository.count();
        long imageCount = fileRepository.countByFileType("IMAGE");
        long audioCount = fileRepository.countByFileType("AUDIO");
        long videoCount = fileRepository.countByFileType("VIDEO");
        long documentCount = fileRepository.countByFileType("DOCUMENT");

        return new FileStats(totalFiles, imageCount, audioCount, videoCount, documentCount);
    }

    // ========== 辅助方法 ==========

    private String determineFileType(String mimeType) {
        if (mimeType == null)
            return "DOCUMENT";

        if (mimeType.startsWith("image/"))
            return "IMAGE";
        if (mimeType.startsWith("audio/"))
            return "AUDIO";
        if (mimeType.startsWith("video/"))
            return "VIDEO";
        return "DOCUMENT";
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    private void processImage(MultipartFile file, String extension, MultimodalFile.MultimodalFileBuilder builder) {
        try {
            // 读取图片尺寸
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image != null) {
                builder.width(image.getWidth());
                builder.height(image.getHeight());

                // 生成缩略图并回写到对象存储
                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    Thumbnails.of(new ByteArrayInputStream(file.getBytes()))
                            .size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                            .outputFormat("jpg")
                            .toOutputStream(out);
                    var thumbStored = fileStorageService.storeBytesDetailed(
                            out.toByteArray(),
                            "thumb-" + UUID.randomUUID() + "." + extension,
                            "multimodal/thumbs",
                            "image/jpeg");
                    builder.thumbnailPath(thumbStored.locator());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to process image thumbnail", e);
        }
    }

    public InputStream openFileStream(String fileId) throws IOException {
        MultimodalFile file = getFile(fileId);
        return openFileStream(file);
    }

    public InputStream openThumbnailStream(String fileId) throws IOException {
        MultimodalFile file = getFile(fileId);
        return openThumbnailStream(file);
    }

    public InputStream openFileStream(MultimodalFile file) throws IOException {
        String locator = resolveFileLocator(file);
        if (!StringUtils.hasText(locator)) {
            throw new IOException("Storage locator is blank");
        }
        return fileStorageService.openStream(locator);
    }

    public InputStream openThumbnailStream(MultimodalFile file) throws IOException {
        String locator = resolveThumbnailLocator(file);
        if (!StringUtils.hasText(locator)) {
            throw new RuntimeException("Thumbnail not available");
        }
        return fileStorageService.openStream(locator);
    }

    public String getDownloadUrl(String fileId, Duration ttl) {
        MultimodalFile file = getFile(fileId);
        return getDownloadUrl(file, ttl);
    }

    public String getThumbnailUrl(String fileId, Duration ttl) {
        MultimodalFile file = getFile(fileId);
        return getThumbnailUrl(file, ttl);
    }

    public String getDownloadUrl(MultimodalFile file, Duration ttl) {
        String locator = resolveFileLocator(file);
        if (!StringUtils.hasText(locator)) {
            return null;
        }
        return fileStorageService.generateDownloadUrl(locator, ttl);
    }

    public String getThumbnailUrl(MultimodalFile file, Duration ttl) {
        String locator = resolveThumbnailLocator(file);
        if (!StringUtils.hasText(locator)) {
            return null;
        }
        return fileStorageService.generateDownloadUrl(locator, ttl);
    }

    public String resolveFileLocator(MultimodalFile file) {
        if (file == null) {
            return null;
        }
        if (StringUtils.hasText(file.getStoragePath())) {
            return file.getStoragePath();
        }
        // Backward compatibility: legacy records may only keep objectKey.
        if (StringUtils.hasText(file.getObjectKey())) {
            return file.getObjectKey();
        }
        return null;
    }

    public String resolveThumbnailLocator(MultimodalFile file) {
        if (file == null) {
            return null;
        }
        return StringUtils.hasText(file.getThumbnailPath()) ? file.getThumbnailPath() : null;
    }

    /**
     * 文件统计信息
     */
    public record FileStats(
            long totalFiles,
            long imageCount,
            long audioCount,
            long videoCount,
            long documentCount) {
    }
}
