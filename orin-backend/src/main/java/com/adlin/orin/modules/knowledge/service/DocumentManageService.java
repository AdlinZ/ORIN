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
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

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

        final String documentIdFinal = documentId;
        final String kbId = document.getKnowledgeBaseId();
        final Integer chunkSizeCfg = document.getChunkSize();
        final Integer overlapCfg = document.getChunkOverlap();

        log.info("Starting asynchronous vectorization for document: {}", documentId);
        new Thread(() -> {
            org.springframework.transaction.support.TransactionTemplate transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(
                    transactionManager);
            try {
                // 读取文件内容
                String content = null;
                KnowledgeDocument currentDocForSplit = documentRepository.findById(documentIdFinal).orElse(null);
                if (currentDocForSplit != null) {
                    content = readFileContent(currentDocForSplit);
                }

                if (content == null || content.isEmpty()) {
                    transactionTemplate.executeWithoutResult(status -> {
                        documentRepository.updateVectorStatus(documentIdFinal, "FAILED");
                    });
                    return;
                }

                // 1. Split text
                int chunkSize = chunkSizeCfg != null ? chunkSizeCfg : 500;
                int overlap = overlapCfg != null ? overlapCfg : 50;
                List<String> chunksText = com.adlin.orin.modules.knowledge.util.SimpleTextSplitter.split(content,
                        chunkSize, overlap);

                List<com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk> chunks = new java.util.ArrayList<>();
                for (int i = 0; i < chunksText.size(); i++) {
                    chunks.add(com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk.builder()
                            .id(UUID.randomUUID().toString())
                            .documentId(documentIdFinal)
                            .chunkIndex(i)
                            .content(chunksText.get(i))
                            .charCount(chunksText.get(i).length())
                            .build());
                }

                // 2. DB Operations in transaction (Delete old, Save new)
                transactionTemplate.executeWithoutResult(status -> {
                    chunkRepository.deleteByDocumentId(documentIdFinal);
                    chunkRepository.saveAll(chunks);
                });

                // 3. Vector Store Operation (Milvus) - Outside DB transaction
                vectorService.deleteDocuments(kbId, java.util.Collections.singletonList(documentIdFinal));
                vectorService.addChunks(kbId, chunks);

                // 4. Update status and count in transaction
                transactionTemplate.executeWithoutResult(status -> {
                    documentRepository.updateVectorStatus(documentIdFinal, "SUCCESS");
                    KnowledgeDocument finalDoc = documentRepository.findById(documentIdFinal).orElse(null);
                    if (finalDoc != null) {
                        finalDoc.setChunkCount(chunks.size());
                        finalDoc.setVectorIndexId("milvus_partition_" + kbId);
                        documentRepository.save(finalDoc);
                    }
                });

                log.info("Vectorization completed for document: {}, chunks: {}", documentIdFinal, chunks.size());

            } catch (Throwable e) {
                log.error("Vectorization CRITICAL FAILURE for document: " + documentIdFinal, e);
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        documentRepository.updateVectorStatus(documentIdFinal, "FAILED");
                    });
                } catch (Exception ex) {
                    log.error("Failed to update status to FAILED for document: " + documentIdFinal, ex);
                }
            }
        }, "VectorizationThread-" + documentId).start();

        log.info("Triggered vectorization background task for document: {}", documentId);

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
     * 更新文档信息
     */
    @Transactional
    public KnowledgeDocument updateDocument(String documentId, java.util.Map<String, Object> payload) {
        KnowledgeDocument document = getDocument(documentId);
        if (payload.containsKey("name")) {
            document.setFileName((String) payload.get("name"));
        }
        if (payload.containsKey("enabled")) {
            if (!(Boolean) payload.get("enabled")) {
                document.setVectorStatus("DISABLED");
            } else if ("DISABLED".equals(document.getVectorStatus())) {
                document.setVectorStatus("PENDING");
            }
        }
        // Handle chunking configuration
        if (payload.containsKey("mode")) {
            document.setChunkMethod((String) payload.get("mode"));
        }
        if (payload.containsKey("chunkSize")) {
            Object size = payload.get("chunkSize");
            if (size instanceof Number) {
                document.setChunkSize(((Number) size).intValue());
            }
        }
        if (payload.containsKey("chunkOverlap")) {
            Object overlap = payload.get("chunkOverlap");
            if (overlap instanceof Number) {
                document.setChunkOverlap(((Number) overlap).intValue());
            }
        }
        return documentRepository.save(document);
    }

    /**
     * 更新文档片段
     */
    @Transactional
    public com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk updateChunk(String chunkId, String content) {
        com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk chunk = chunkRepository.findById(chunkId)
                .orElseThrow(() -> new RuntimeException("Chunk not found: " + chunkId));
        chunk.setContent(content);
        chunk.setCharCount(content.length());
        chunk = chunkRepository.save(chunk);

        // Option: we could also trigger vector deletion and re-insertion here for
        // accurate RAG.
        return chunk;
    }

    /**
     * 删除文档片段
     */
    @Transactional
    public void deleteChunk(String chunkId) {
        chunkRepository.deleteById(chunkId);
        // Option: Delete from vector DB
    }

    /**
     * 获取历史记录 (Dummy for now, can be read from audit log)
     */
    public List<java.util.Map<String, String>> getDocumentHistory(String documentId) {
        KnowledgeDocument doc = getDocument(documentId);
        List<java.util.Map<String, String>> history = new java.util.ArrayList<>();

        history.add(java.util.Map.of(
                "timestamp", doc.getUploadTime() != null ? doc.getUploadTime().toString() : "N/A",
                "type", "create",
                "description", "文档已创建"));

        if ("INDEXED".equals(doc.getVectorStatus()) || "SUCCESS".equals(doc.getVectorStatus())) {
            history.add(java.util.Map.of(
                    "timestamp",
                    doc.getUploadTime() != null ? doc.getUploadTime().plusMinutes(2).toString()
                            : java.time.LocalDateTime.now().toString(),
                    "type", "update",
                    "description", "已完成向量化索引处理"));
        }

        return history;
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

    /**
     * 解析文件为文本 (用于预览)
     */
    public String parseFileToText(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null)
            return "";
        String extension = getFileExtension(filename);

        if (extension.equals("txt") || extension.equals("md")) {
            return new String(file.getBytes());
        } else if (extension.equals("pdf")) {
            try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
                org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                return stripper.getText(document).trim();
            } catch (Exception e) {
                log.warn("Failed to parse PDF: {}", e.getMessage());
                return "Failed to parse PDF content.";
            }
        } else if (extension.equals("docx")) {
            try (java.io.InputStream is = file.getInputStream();
                    org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                            is)) {
                StringBuilder text = new StringBuilder();
                for (org.apache.poi.xwpf.usermodel.XWPFParagraph p : document.getParagraphs()) {
                    text.append(p.getText()).append("\n");
                }
                return text.toString();
            } catch (Exception e) {
                log.warn("Failed to parse DOCX: {}", e.getMessage());
                return "Failed to parse DOCX content.";
            }
        }
        return "";
    }
}
