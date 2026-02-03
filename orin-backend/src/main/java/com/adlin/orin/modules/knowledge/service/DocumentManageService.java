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
    private final com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentChunkRepository chunkRepository;
    private final com.adlin.orin.modules.knowledge.component.VectorStoreProvider vectorService;

    // 文件存储根目录 (可配置)
    private static final String UPLOAD_DIR = "storage/uploads/documents";

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
        if (document.getStoragePath() != null) {
            try {
                Path filePath = Paths.get(document.getStoragePath());
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("Failed to delete physical file: {}", document.getStoragePath(), e);
            }
        }

        // 删除数据库记录
        documentRepository.delete(document);

        // 删除向量记录
        vectorService.deleteDocuments(document.getKnowledgeBaseId(), List.of(documentId));

        log.info("Deleted document: {}", documentId);
    }

    /**
     * 批量删除知识库下的所有文档（优化版：不建议在删除 KB 时逐个调用 deleteDocument）
     */
    @Transactional
    public void deleteByKnowledgeBaseId(String kbId) {
        List<KnowledgeDocument> documents = documentRepository.findByKnowledgeBaseIdOrderByUploadTimeDesc(kbId);

        // 1. 批量删除物理文件
        for (KnowledgeDocument doc : documents) {
            if (doc.getStoragePath() != null) {
                try {
                    Files.deleteIfExists(Paths.get(doc.getStoragePath()));
                } catch (IOException e) {
                    log.warn("Failed to delete file during bulk cleanup: {}", doc.getStoragePath());
                }
            }
        }

        // 2. 批量删除数据库记录
        documentRepository.deleteByKnowledgeBaseId(kbId);
        log.info("Bulk deleted {} documents for KB: {}", documents.size(), kbId);
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
                // 读取文件内容 (简化版: 假设是文本文件，直接读)
                String content = null;
                try {
                    // Re-read file from path.
                    KnowledgeDocument currentDoc = documentRepository.findById(docId).orElse(null);
                    if (currentDoc != null) {
                        content = readFileContent(currentDoc);
                    }
                } catch (Exception e) {
                    log.error("Failed to read file content", e);
                }

                if (content == null || content.isEmpty()) {
                    documentRepository.updateVectorStatus(docId, "FAILED");
                    return;
                }

                // Split
                List<String> chunksText = com.adlin.orin.modules.knowledge.util.SimpleTextSplitter.split(content);

                // Save Chunks to DB
                List<com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk> chunks = new java.util.ArrayList<>();
                for (int i = 0; i < chunksText.size(); i++) {
                    chunks.add(com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk.builder()
                            .documentId(docId)
                            .chunkIndex(i)
                            .content(chunksText.get(i))
                            .charCount(chunksText.get(i).length())
                            .build());
                }

                // 需要在 Bean 上下文中处理
                // 由于我们在 Thread 中，无法获得事务支持 (Repository save is fine, but service logic isn't)
                // 且 vectorService 是 Spring Bean
                // 为了避免依赖注入问题 (lambda capture is fine for final fields), 这里的 fields 必须是 final
                // 并且被构造器初始化

                // 获取当前 doc 以得到 kbId
                KnowledgeDocument currentDoc = documentRepository.findById(docId).orElse(null);
                if (currentDoc != null) {
                    chunkRepository.saveAll(chunks);

                    // Call Milvus (Using Chunk objects)
                    vectorService.addChunks(currentDoc.getKnowledgeBaseId(), chunks);

                    documentRepository.updateVectorStatus(docId, "SUCCESS");

                    // Update stats
                    KnowledgeDocument finalDoc = currentDoc;
                    finalDoc.setChunkCount(chunks.size());
                    finalDoc.setVectorIndexId("milvus_partition_" + finalDoc.getKnowledgeBaseId());
                    documentRepository.save(finalDoc);

                    log.info("Vectorization completed for document: {}, chunks: {}", docId, chunks.size());
                }

            } catch (Exception e) {
                log.error("Vectorization failed for document: " + docId, e);
                documentRepository.updateVectorStatus(docId, "FAILED");
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

            // 支持 DOCX 格式
            if (fileType.equals("docx")) {
                try (java.io.InputStream is = file.getInputStream();
                        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                                is)) {
                    StringBuilder text = new StringBuilder();
                    // Extract text from paragraphs
                    for (org.apache.poi.xwpf.usermodel.XWPFParagraph p : doc.getParagraphs()) {
                        text.append(p.getText()).append("\n");
                        if (text.length() > 500)
                            break;
                    }
                    return text.length() > 500 ? text.substring(0, 500) : text.toString();
                } catch (Exception e) {
                    log.warn("Failed to extract DOCX content: {}", e.getMessage());
                    return "[DOCX Extraction Failed]";
                }
            }

            return null;
        } catch (IOException e) {
            log.warn("Failed to extract content preview", e);
            return null;
        }
    }

    private String readFileContent(KnowledgeDocument doc) throws IOException {
        String fileType = doc.getFileType();
        Path path = Paths.get(doc.getStoragePath());
        if (!Files.exists(path))
            return null;

        if (fileType.equalsIgnoreCase("txt") || fileType.equalsIgnoreCase("md")) {
            return Files.readString(path);
        } else if (fileType.equalsIgnoreCase("pdf")) {
            try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(path.toFile())) {
                org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                return stripper.getText(document).trim();
            }
        } else if (fileType.equalsIgnoreCase("docx")) {
            try (java.io.InputStream is = Files.newInputStream(path);
                    org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                            is)) {
                StringBuilder text = new StringBuilder();
                for (org.apache.poi.xwpf.usermodel.XWPFParagraph p : document.getParagraphs()) {
                    text.append(p.getText()).append("\n");
                }
                return text.toString();
            }
        }
        return doc.getContentPreview(); // Fallback
    }

    /**
     * 文档统计信息
     */
    public record DocumentStats(long documentCount, long totalCharCount) {
    }
}
