package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * 文档管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentManageService {

    private final KnowledgeDocumentRepository documentRepository;

    // 文件存储根目录 (可配置)
    private static final String UPLOAD_DIR = "/var/orin/uploads/documents";

    /**
     * 上传文档
     */
    @Transactional
    public KnowledgeDocument uploadDocument(
            String knowledgeBaseId,
            MultipartFile file,
            String uploadedBy) throws IOException {

        // 验证文件
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("Invalid filename");
        }

        // 生成唯一文件名
        String fileExtension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + "." + fileExtension;
        String mimeType = file.getContentType();

        // 创建存储目录
        Path uploadPath = Paths.get(UPLOAD_DIR, knowledgeBaseId);
        Files.createDirectories(uploadPath);

        // 保存文件
        Path filePath = uploadPath.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // 读取内容预览
        String contentPreview = extractContentPreview(file, fileExtension);

        // Metadata processing
        String metadata = "{}";
        if (mimeType != null && mimeType.startsWith("image/")) {
            try {
                java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(filePath.toFile());
                if (image != null) {
                    metadata = String.format("{\"width\": %d, \"height\": %d, \"format\": \"%s\"}",
                            image.getWidth(), image.getHeight(), fileExtension);
                }
            } catch (Exception e) {
                log.warn("Failed to process image metadata", e);
            }
        } else if (mimeType != null && mimeType.startsWith("audio/")) {
            // Placeholder for audio metadata (e.g. duration)
            metadata = "{\"type\": \"audio\", \"format\": \"" + fileExtension + "\"}";
        }

        // 创建文档记录
        KnowledgeDocument document = KnowledgeDocument.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .fileName(originalFilename)
                .fileType(fileExtension)
                .fileSize(file.getSize())
                .storagePath(filePath.toString())
                .contentPreview(contentPreview)
                .vectorStatus("PENDING")
                .charCount(contentPreview != null ? contentPreview.length() : 0)
                .uploadedBy(uploadedBy != null ? uploadedBy : "system")
                .metadata(metadata)
                .build();

        document = documentRepository.save(document);
        log.info("Uploaded document: {} to knowledge base: {}", originalFilename, knowledgeBaseId);

        return document;
    }

    /**
     * 获取知识库的所有文档
     */
    public List<KnowledgeDocument> getDocuments(String knowledgeBaseId) {
        return documentRepository.findByKnowledgeBaseIdOrderByUploadTimeDesc(knowledgeBaseId);
    }

    /**
     * 获取文档详情
     */
    public KnowledgeDocument getDocument(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
    }

    /**
     * 删除文档
     */
    @Transactional
    public void deleteDocument(String documentId) {
        KnowledgeDocument document = getDocument(documentId);

        // 删除物理文件
        try {
            Path filePath = Paths.get(document.getStoragePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Failed to delete physical file: {}", document.getStoragePath(), e);
        }

        // 删除数据库记录
        documentRepository.delete(document);
        log.info("Deleted document: {}", documentId);
    }

    /**
     * 触发文档向量化
     */
    @Transactional
    public KnowledgeDocument triggerVectorization(String documentId) {
        KnowledgeDocument document = getDocument(documentId);

        // 更新状态为 INDEXING
        document.setVectorStatus("INDEXING");
        document = documentRepository.save(document);

        // 模拟异步向量化过程
        // 实际生产环境中，这里应该调用 Dify API 或提交任务到消息队列
        final String docId = documentId;
        new Thread(() -> {
            try {
                // 模拟处理耗时
                Thread.sleep(3000);

                // 重新获取文档（因为是在新线程中）
                // 注意：这里需要处理事务，简单起见我们使用 sleep 模拟
                // 在实际 Spring Bean 中，应该调用另一个 @Transactional 方法

                // 由于是在非事务线程中操作 Repository，这是允许的，但要注意并发
                documentRepository.findById(docId).ifPresent(doc -> {
                    doc.setVectorStatus("SUCCESS");
                    doc.setChunkCount((int) (Math.random() * 10) + 1); // 模拟分片数
                    doc.setVectorIndexId(UUID.randomUUID().toString());
                    documentRepository.save(doc);
                    log.info("Simulated vectorization completed for document: {}", docId);
                });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        log.info("Triggered vectorization for document: {}", documentId);

        return document;
    }

    /**
     * 更新向量化状态
     */
    @Transactional
    public KnowledgeDocument updateVectorizationStatus(
            String documentId,
            String status,
            String vectorIndexId,
            Integer chunkCount) {

        KnowledgeDocument document = getDocument(documentId);
        document.setVectorStatus(status);
        document.setVectorIndexId(vectorIndexId);
        if (chunkCount != null) {
            document.setChunkCount(chunkCount);
        }

        return documentRepository.save(document);
    }

    /**
     * 获取待向量化的文档列表
     */
    public List<KnowledgeDocument> getPendingDocuments() {
        return documentRepository.findByVectorStatusIn(List.of("PENDING", "FAILED"));
    }

    /**
     * 获取知识库统计信息
     */
    public DocumentStats getKnowledgeBaseStats(String knowledgeBaseId) {
        long documentCount = documentRepository.countByKnowledgeBaseId(knowledgeBaseId);
        Long totalChars = documentRepository.sumCharCountByKnowledgeBaseId(knowledgeBaseId);

        return new DocumentStats(
                documentCount,
                totalChars != null ? totalChars : 0L);
    }

    // ========== 辅助方法 ==========

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    private String extractContentPreview(MultipartFile file, String fileType) {
        try {
            // 简单实现：仅支持文本文件
            if (fileType.equals("txt") || fileType.equals("md")) {
                byte[] bytes = file.getBytes();
                String content = new String(bytes);
                return content.length() > 500 ? content.substring(0, 500) : content;
            }

            // 支持 PDF 格式的文本提取
            if (fileType.equals("pdf")) {
                try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader
                        .loadPDF(file.getBytes())) {
                    org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                    // 仅提取前几页作为预览
                    stripper.setSortByPosition(true);
                    stripper.setStartPage(1);
                    stripper.setEndPage(3);
                    String text = stripper.getText(document).trim();
                    return text.length() > 500 ? text.substring(0, 500) : text;
                } catch (Exception e) {
                    log.warn("Failed to extract PDF content: {}", e.getMessage());
                    return "[PDF Extraction Failed]";
                }
            }

            // TODO: [Plan] Add Apache POI dependency and implement DOCX parser
            return null;
        } catch (IOException e) {
            log.warn("Failed to extract content preview", e);
            return null;
        }
    }

    /**
     * 文档统计信息
     */
    public record DocumentStats(long documentCount, long totalCharCount) {
    }
}
