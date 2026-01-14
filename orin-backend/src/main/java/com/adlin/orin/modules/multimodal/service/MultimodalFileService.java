package com.adlin.orin.modules.multimodal.service;

import com.adlin.orin.modules.multimodal.entity.MultimodalFile;
import com.adlin.orin.modules.multimodal.repository.MultimodalFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

    private static final String UPLOAD_DIR = "/var/orin/uploads/multimodal";
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

        // 生成唯一文件名
        String fileExtension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + "." + fileExtension;

        // 创建存储目录
        Path uploadPath = Paths.get(UPLOAD_DIR, fileType.toLowerCase());
        Files.createDirectories(uploadPath);

        // 保存文件
        Path filePath = uploadPath.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // 创建文件记录
        MultimodalFile.MultimodalFileBuilder builder = MultimodalFile.builder()
                .fileName(originalFilename)
                .fileType(fileType)
                .mimeType(mimeType)
                .fileSize(file.getSize())
                .storagePath(filePath.toString())
                .uploadedBy(uploadedBy != null ? uploadedBy : "system");

        // 处理图片
        if ("IMAGE".equals(fileType)) {
            processImage(file, filePath, builder);
        }

        MultimodalFile multimodalFile = builder.build();
        multimodalFile = fileRepository.save(multimodalFile);

        log.info("Uploaded multimodal file: {} (type: {})", originalFilename, fileType);
        return multimodalFile;
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
            Files.deleteIfExists(Paths.get(file.getStoragePath()));
            if (file.getThumbnailPath() != null) {
                Files.deleteIfExists(Paths.get(file.getThumbnailPath()));
            }
        } catch (IOException e) {
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

    private void processImage(MultipartFile file, Path filePath, MultimodalFile.MultimodalFileBuilder builder) {
        try {
            // 读取图片尺寸
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image != null) {
                builder.width(image.getWidth());
                builder.height(image.getHeight());

                // 生成缩略图
                String thumbnailFilename = "thumb_" + filePath.getFileName().toString();
                Path thumbnailPath = filePath.getParent().resolve(thumbnailFilename);

                Thumbnails.of(filePath.toFile())
                        .size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                        .toFile(thumbnailPath.toFile());

                builder.thumbnailPath(thumbnailPath.toString());
                log.debug("Generated thumbnail for image: {}", filePath.getFileName());
            }
        } catch (IOException e) {
            log.warn("Failed to process image: {}", filePath, e);
        }
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
