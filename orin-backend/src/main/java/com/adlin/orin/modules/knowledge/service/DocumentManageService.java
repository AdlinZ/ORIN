package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文档管理服务
 * 支持多模态文件上传、解析和向量化
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentManageService {

    private final KnowledgeDocumentRepository documentRepository;
    private final com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentChunkRepository chunkRepository;
    private final com.adlin.orin.modules.knowledge.component.VectorStoreProvider vectorService;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;
    private final MultimodalContentParserService multimodalParserService;
    private final com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository knowledgeBaseRepository;

    // 文件存储根目录 (从配置读取)
    @Value("${knowledge.upload.path:storage/uploads/documents}")
    private String uploadDir;

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
        Path uploadPath = Paths.get(uploadDir, knowledgeBaseId);
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

        // 确定媒体类型和文件类别
        String mediaType = determineMediaType(fileExtension, mimeType);
        String fileCategory = multimodalParserService.getFileCategory(fileExtension);
        // 创建文档记录
        KnowledgeDocument document = KnowledgeDocument.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .fileName(originalFilename)
                .originalFilename(originalFilename)
                .fileType(fileExtension)
                .fileCategory(fileCategory)
                .fileSize(file.getSize())
                .storagePath(filePath.toString())
                .contentPreview(contentPreview)
                .parseStatus("PENDING")
                .vectorStatus("PENDING")
                .mediaType(mediaType)
                .parseStatus("PENDING")
                .charCount(contentPreview != null ? contentPreview.length() : 0)
                .uploadedBy(uploadedBy != null ? uploadedBy : "system")
                .metadata(metadata)
                .build();

        document = documentRepository.save(document);
        log.info("Uploaded document: {} to knowledge base: {}", originalFilename, knowledgeBaseId);

        // 自动触发解析和向量化 - 使用 TransactionSynchronization 确保事务提交后再执行
        final String docId = document.getId();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log.info("Transaction committed, triggering parsing for document: {}", docId);
                        triggerParsing(docId, true);
                    }
                }
        );

        return document;
    }

    /**
     * 获取知识库的所有文档
     */
    public List<KnowledgeDocument> getDocuments(String knowledgeBaseId) {
        return documentRepository.findByKnowledgeBaseIdOrderByUploadTimeDesc(knowledgeBaseId);
    }

    /**
     * 获取所有文档
     */
    public List<KnowledgeDocument> getAllDocuments() {
        return documentRepository.findAll();
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
     * 使用正确的删除顺序，确保一致性：
     * 1. 先删除向量记录（最重要）
     * 2. 再删除数据库记录
     * 3. 最后删除物理文件（失败不影响一致性）
     */
    @Transactional
    public void deleteDocument(String documentId) {
        KnowledgeDocument document = getDocument(documentId);
        String kbId = document.getKnowledgeBaseId();
        List<String> docIds = List.of(documentId);

        // 1. 先删除向量记录（最关键，必须先执行）
        try {
            vectorService.deleteDocuments(kbId, docIds);
            log.debug("Deleted vector records for document: {}", documentId);
        } catch (Exception e) {
            log.error("Failed to delete vector records for document: {}, continuing with other deletions",
                    documentId, e);
            // 不抛出异常，继续执行后续删除
        }

        // 2. 删除数据库记录
        try {
            documentRepository.delete(document);
            log.debug("Deleted database record for document: {}", documentId);
        } catch (Exception e) {
            log.error("Failed to delete database record for document: {}", documentId, e);
            // 如果数据库删除失败，记录补偿任务
            // 这里可以记录到补偿表，后续重试
            throw new RuntimeException("Failed to delete document from database: " + documentId, e);
        }

        // 3. 最后删除物理文件（原始文件和索引文件）
        // 这些删除失败不影响一致性，记录警告即可
        List<String> filesToDelete = List.of(
                document.getStoragePath(),
                document.getParsedTextPath()
        );

        for (String filePath : filesToDelete) {
            if (filePath != null && !filePath.isEmpty()) {
                try {
                    Path path = Paths.get(filePath);
                    if (Files.deleteIfExists(path)) {
                        log.debug("Deleted physical file: {}", filePath);
                    }
                } catch (Exception e) {
                    log.warn("Failed to delete physical file: {}, error: {}", filePath, e.getMessage());
                    // 不抛出异常，物理文件可由定时清理任务处理
                }
            }
        }

        log.info("Deleted document: {} from knowledge base: {}", documentId, kbId);
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
     * 触发文档解析（多模态）
     * @param documentId 文档ID
     * @param autoVectorize 是否自动触发向量化，默认true
     */
    @Transactional
    public KnowledgeDocument triggerParsing(String documentId, boolean autoVectorize) {
        KnowledgeDocument document = getDocument(documentId);

        // 更新解析状态
        document.setParseStatus("PARSING");
        document = documentRepository.save(document);

        final String documentIdFinal = documentId;
        final String kbId = document.getKnowledgeBaseId();
        final String fileType = document.getFileType();
        final String filePath = document.getStoragePath();
        final boolean shouldVectorize = autoVectorize;

        log.info("Starting multimodal parsing for document: {}, autoVectorize: {}", documentId, autoVectorize);

        new Thread(() -> {
            org.springframework.transaction.support.TransactionTemplate transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(
                    transactionManager);

            // 获取知识库配置，用于传递 OCR/ASR 模型和富文本解析设置
            Map<String, String> parseConfig = new HashMap<>();
            try {
                var kb = knowledgeBaseRepository.findById(kbId).orElse(null);
                log.info("Parsing: Found knowledge base: {}, ocrModel: {}, asrModel: {}",
                        kbId, kb != null ? kb.getOcrModel() : "null", kb != null ? kb.getAsrModel() : "null");
                if (kb != null) {
                    if (kb.getOcrModel() != null && !kb.getOcrModel().isEmpty()) {
                        parseConfig.put("ocr_model", kb.getOcrModel());
                        log.info("Using ocr_model from knowledge base: {}", kb.getOcrModel());
                    }
                    if (kb.getAsrModel() != null && !kb.getAsrModel().isEmpty()) {
                        parseConfig.put("asr_model", kb.getAsrModel());
                        log.info("Using asr_model from knowledge base: {}", kb.getAsrModel());
                    }
                    // 富文本解析设置
                    Boolean richText = kb.getRichTextEnabled();
                    if (richText != null) {
                        parseConfig.put("richText", richText.toString());
                        log.info("Using richText from knowledge base: {}", richText);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load knowledge base config: {}", e.getMessage());
            }

            try {
                // 首先更新状态为 PARSING，让前端知道正在解析
                transactionTemplate.executeWithoutResult(status -> {
                    KnowledgeDocument doc = documentRepository.findById(documentIdFinal).orElse(null);
                    if (doc != null) {
                        doc.setParseStatus("PARSING");
                        documentRepository.save(doc);
                    }
                });
                log.info("Parsing: Status updated to PARSING for document: {}", documentIdFinal);

                // 使用 multimodal parser 解析内容，传入配置
                log.info("Parsing: Using MultimodalContentParserService for document: {}", documentIdFinal);
                String content = multimodalParserService.parseToText(filePath, fileType, parseConfig);

                // 检查是否是解析失败的错误信息
                boolean isParseFailed = content == null || content.isEmpty()
                        || content.contains("[图片解析失败") || content.contains("[音频解析失败")
                        || content.contains("[视频解析失败") || content.contains("[PDF解析失败")
                        || content.contains("[DOCX解析失败") || content.contains("[DOC解析失败");

                if (isParseFailed) {
                    log.error("Parsing: FAILED - parsing returned error for document: {}, content: {}", documentIdFinal, content);
                    transactionTemplate.executeWithoutResult(status -> {
                        KnowledgeDocument doc = documentRepository.findById(documentIdFinal).orElse(null);
                        if (doc != null) {
                            doc.setParseStatus("FAILED");
                            String errorMsg = content != null ? (content.length() > 500 ? content.substring(0, 500) : content) : "解析失败";
                            doc.setContentPreview(errorMsg);
                            doc.setCharCount(errorMsg.length()); // 更新 charCount
                            documentRepository.save(doc);
                        }
                    });
                    return;
                }

                // 保存解析后的文本
                String parsedPath = multimodalParserService.saveParsedText(kbId, documentIdFinal, content);

                // 生成预览
                String preview = (content != null && content.length() > 500) ? content.substring(0, 500) : content;

                // 更新解析状态和文本路径
                final String finalContent = content;
                transactionTemplate.executeWithoutResult(status -> {
                    KnowledgeDocument doc = documentRepository.findById(documentIdFinal).orElse(null);
                    if (doc != null) {
                        doc.setParseStatus("PARSED");
                        doc.setParsedTextPath(parsedPath);
                        doc.setContentPreview(preview);
                        doc.setCharCount(finalContent != null ? finalContent.length() : 0);
                        documentRepository.save(doc);
                    }
                });

                log.info("Parsing completed for document: {}, content length: {}", documentIdFinal, content != null ? content.length() : 0);

                // 根据参数决定是否触发向量化
                if (shouldVectorize && content != null) {
                    triggerVectorizationInternal(documentIdFinal, content);
                }

            } catch (Exception e) {
                log.error("Parsing CRITICAL FAILURE for document: " + documentIdFinal, e);
                try {
                    String errorMsg = "解析异常: " + e.getMessage();
                    transactionTemplate.executeWithoutResult(status -> {
                        KnowledgeDocument doc = documentRepository.findById(documentIdFinal).orElse(null);
                        if (doc != null) {
                            doc.setParseStatus("FAILED");
                            doc.setContentPreview(errorMsg.length() > 500 ? errorMsg.substring(0, 500) : errorMsg);
                            doc.setCharCount(errorMsg.length());
                            documentRepository.save(doc);
                        }
                    });
                } catch (Exception ex) {
                    log.error("Failed to update parse status to FAILED for document: " + documentIdFinal, ex);
                }
            }
        }, "ParsingThread-" + documentId).start();

        log.info("Triggered parsing background task for document: {}", documentId);
        return document;
    }
    
    /**
     * 内部向量化方法（接受已解析的内容）
     */
    private void triggerVectorizationInternal(String documentId, String content) {
        final String documentIdFinal = documentId;
        
        // 查找文档获取 kbId
        KnowledgeDocument doc = documentRepository.findById(documentIdFinal).orElse(null);
        if (doc == null) {
            log.error("Document not found for vectorization: {}", documentIdFinal);
            return;
        }
        
        String kbId = doc.getKnowledgeBaseId();
        
        // 更新状态为 INDEXING
        doc.setVectorStatus("INDEXING");
        documentRepository.save(doc);
        
        org.springframework.transaction.support.TransactionTemplate transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(
                transactionManager);
        
        try {
            if (content == null || content.isEmpty()) {
                log.error("Vectorization: FAILED - content is null or empty for document: {}", documentIdFinal);
                transactionTemplate.executeWithoutResult(status -> {
                    documentRepository.updateVectorStatus(documentIdFinal, "FAILED");
                });
                return;
            }

            String docTitle = doc.getFileName();
            log.info("Vectorization: Starting hierarchical split for document: {}, content length: {}", documentIdFinal, content.length());

            // 1. Hierarchical Split: Parent-Child chunking
            com.adlin.orin.modules.knowledge.util.HierarchicalTextSplitter.HierarchicalChunks hierarchicalChunks =
                    com.adlin.orin.modules.knowledge.util.HierarchicalTextSplitter.splitHierarchical(
                            content, documentIdFinal, docTitle);

            log.info("Vectorization: Hierarchical split done, parent chunks: {}, child chunks: {}",
                    hierarchicalChunks.getParents().size(), hierarchicalChunks.getChildren().size());

            // Build entities for both parent and child chunks
            List<com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk> allChunks = new java.util.ArrayList<>();

            // Add parent chunks
            for (com.adlin.orin.modules.knowledge.util.HierarchicalTextSplitter.ParentChunk parent :
                    hierarchicalChunks.getParents()) {
                allChunks.add(com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk.builder()
                        .id(parent.getId())
                        .documentId(documentIdFinal)
                        .chunkIndex(parent.getPosition())
                        .content(parent.getContent())
                        .charCount(parent.getContent().length())
                        .chunkType("parent")
                        .parentId(null)
                        .childrenIds(parent.getChildrenIds() != null ?
                                java.util.Arrays.toString(parent.getChildrenIds().toArray()) : "[]")
                        .title(parent.getTitle())
                        .source(docTitle)
                        .position(parent.getPosition())
                        .build());
            }

            // Add child chunks
            for (com.adlin.orin.modules.knowledge.util.HierarchicalTextSplitter.ChildChunk child :
                    hierarchicalChunks.getChildren()) {
                allChunks.add(com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk.builder()
                        .id(child.getId())
                        .documentId(documentIdFinal)
                        .chunkIndex(child.getPosition())
                        .content(child.getContent())
                        .charCount(child.getContent().length())
                        .chunkType("child")
                        .parentId(child.getParentId())
                        .title(child.getSource())
                        .source(docTitle)
                        .position(child.getPosition())
                        .build());
            }

            // 2. DB Operations in transaction (Delete old, Save new)
            transactionTemplate.executeWithoutResult(status -> {
                chunkRepository.deleteByDocumentId(documentIdFinal);
                chunkRepository.saveAll(allChunks);
            });

            // 3. Vector Store Operation (Milvus) - Outside DB transaction
            log.info("Vectorization: Deleting old vectors from Milvus for document: {}", documentIdFinal);
            vectorService.deleteDocuments(kbId, java.util.Collections.singletonList(documentIdFinal));
            log.info("Vectorization: Adding chunks to Milvus, kbId: {}, chunk count: {}", kbId, allChunks.size());
            vectorService.addChunks(kbId, allChunks);
            log.info("Vectorization: Chunks added to Milvus successfully!");

            // 4. Update status and count
            final int childChunkCount = hierarchicalChunks.getChildren().size();
            final int totalCharCount = content.length();
            transactionTemplate.executeWithoutResult(status -> {
                documentRepository.updateVectorStatus(documentIdFinal, "SUCCESS");
                KnowledgeDocument finalDoc = documentRepository.findById(documentIdFinal).orElse(null);
                if (finalDoc != null) {
                    finalDoc.setChunkCount(childChunkCount);
                    finalDoc.setCharCount(totalCharCount);
                    finalDoc.setVectorIndexId("milvus_partition_" + kbId);
                    documentRepository.save(finalDoc);
                }
            });

            log.info("Vectorization completed for document: {}, parent chunks: {}, child chunks: {}",
                    documentIdFinal, hierarchicalChunks.getParents().size(), childChunkCount);

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
    }

    /**
     * 触发文档向量化（保留原有方法，调用解析后的内容）
     */
    @Transactional
    public KnowledgeDocument triggerVectorization(String documentId) {
        KnowledgeDocument document = getDocument(documentId);
        
        // 如果已经有解析后的文本，直接向量化
        if ("PARSED".equals(document.getParseStatus()) && document.getParsedTextPath() != null) {
            try {
                String content = multimodalParserService.readParsedText(
                    document.getKnowledgeBaseId(), documentId);
                if (content != null && !content.isEmpty()) {
                    triggerVectorizationInternal(documentId, content);
                    return document;
                }
            } catch (Exception e) {
                log.warn("Failed to read parsed text, will re-parse", e);
            }
        }
        
        // 如果未解析，先触发解析
        if (!"PARSED".equals(document.getParseStatus())) {
            triggerParsing(documentId, true);
            return document;
        }

        // 更新状态为 INDEXING
        document.setVectorStatus("INDEXING");
        document = documentRepository.save(document);

        final String documentIdFinal = documentId;
        final String kbId = document.getKnowledgeBaseId();

        log.info("Starting asynchronous vectorization for document: {}", documentId);
        new Thread(() -> {
            org.springframework.transaction.support.TransactionTemplate transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(
                    transactionManager);
            try {
                // 读取文件内容
                log.info("Vectorization: Reading file content for document: {}", documentIdFinal);
                String content = null;
                KnowledgeDocument currentDocForSplit = documentRepository.findById(documentIdFinal).orElse(null);
                if (currentDocForSplit != null) {
                    log.info("Vectorization: Document found, storage path: {}", currentDocForSplit.getStoragePath());
                    content = readFileContent(currentDocForSplit);
                    log.info("Vectorization: Content read, length: {}", content != null ? content.length() : 0);
                } else {
                    log.error("Vectorization: Document NOT FOUND in database: {}", documentIdFinal);
                }

                if (content == null || content.isEmpty()) {
                    log.error("Vectorization: FAILED - content is null or empty for document: {}", documentIdFinal);
                    transactionTemplate.executeWithoutResult(status -> {
                        documentRepository.updateVectorStatus(documentIdFinal, "FAILED");
                    });
                    return;
                }

                // Get document title for metadata
                String docTitle = currentDocForSplit != null ? currentDocForSplit.getFileName() : "Document";
                log.info("Vectorization: Starting hierarchical split for document: {}, content length: {}",
                        documentIdFinal, content.length());

                // 1. Hierarchical Split: Parent-Child chunking
                com.adlin.orin.modules.knowledge.util.HierarchicalTextSplitter.HierarchicalChunks hierarchicalChunks = com.adlin.orin.modules.knowledge.util.HierarchicalTextSplitter
                        .splitHierarchical(
                                content, documentIdFinal, docTitle);

                log.info("Vectorization: Hierarchical split done, parent chunks: {}, child chunks: {}",
                        hierarchicalChunks.getParents().size(), hierarchicalChunks.getChildren().size());

                // Build entities for both parent and child chunks
                List<com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk> allChunks = new java.util.ArrayList<>();

                // Add parent chunks
                for (com.adlin.orin.modules.knowledge.util.HierarchicalTextSplitter.ParentChunk parent : hierarchicalChunks
                        .getParents()) {
                    allChunks.add(com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk.builder()
                            .id(parent.getId())
                            .documentId(documentIdFinal)
                            .chunkIndex(parent.getPosition())
                            .content(parent.getContent())
                            .charCount(parent.getContent().length())
                            .chunkType("parent")
                            .parentId(null)
                            .childrenIds(parent.getChildrenIds() != null
                                    ? java.util.Arrays.toString(parent.getChildrenIds().toArray())
                                    : "[]")
                            .title(parent.getTitle())
                            .source(docTitle)
                            .position(parent.getPosition())
                            .build());
                }

                // Add child chunks
                for (com.adlin.orin.modules.knowledge.util.HierarchicalTextSplitter.ChildChunk child : hierarchicalChunks
                        .getChildren()) {
                    allChunks.add(com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk.builder()
                            .id(child.getId())
                            .documentId(documentIdFinal)
                            .chunkIndex(child.getPosition())
                            .content(child.getContent())
                            .charCount(child.getContent().length())
                            .chunkType("child")
                            .parentId(child.getParentId())
                            .title(child.getSource())
                            .source(docTitle)
                            .position(child.getPosition())
                            .build());
                }

                // 2. DB Operations in transaction (Delete old, Save new)
                transactionTemplate.executeWithoutResult(status -> {
                    chunkRepository.deleteByDocumentId(documentIdFinal);
                    chunkRepository.saveAll(allChunks);
                });

                // 3. Vector Store Operation (Milvus) - Outside DB transaction
                // Only child chunks will be embedded (parent chunks get zero vectors as
                // placeholder)
                log.info("Vectorization: Deleting old vectors from Milvus for document: {}", documentIdFinal);
                vectorService.deleteDocuments(kbId, java.util.Collections.singletonList(documentIdFinal));
                log.info("Vectorization: Adding chunks to Milvus, kbId: {}, chunk count: {}", kbId, allChunks.size());
                vectorService.addChunks(kbId, allChunks);
                log.info("Vectorization: Chunks added to Milvus successfully!");

                // 4. Update status and count in transaction
                final int childChunkCount = hierarchicalChunks.getChildren().size();
                final int totalCharCount = content != null ? content.length() : 0;
                transactionTemplate.executeWithoutResult(status -> {
                    documentRepository.updateVectorStatus(documentIdFinal, "SUCCESS");
                    KnowledgeDocument finalDoc = documentRepository.findById(documentIdFinal).orElse(null);
                    if (finalDoc != null) {
                        finalDoc.setChunkCount(childChunkCount); // Report child chunk count as actual vectors
                        finalDoc.setCharCount(totalCharCount); // Update with actual content length
                        finalDoc.setVectorIndexId("milvus_partition_" + kbId);
                        documentRepository.save(finalDoc);
                    }
                });

                log.info("Vectorization completed for document: {}, parent chunks: {}, child chunks: {}",
                        documentIdFinal, hierarchicalChunks.getParents().size(), childChunkCount);

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
     * 批量触发所有待处理文档的向量化
     */
    public int triggerPendingVectorization() {
        List<KnowledgeDocument> pendingDocs = getPendingDocuments();
        int count = 0;
        for (KnowledgeDocument doc : pendingDocs) {
            try {
                triggerVectorization(doc.getId());
                count++;
            } catch (Exception e) {
                log.error("Failed to trigger vectorization for document: {}", doc.getId(), e);
            }
        }
        log.info("Triggered vectorization for {} pending documents", count);
        return count;
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

    /**
     * 确定媒体类型
     */
    private String determineMediaType(String fileExtension, String mimeType) {
        // 优先使用 MIME 类型
        if (mimeType != null) {
            if (mimeType.startsWith("image/"))
                return "image";
            if (mimeType.startsWith("audio/"))
                return "audio";
            if (mimeType.startsWith("video/"))
                return "video";
            if (mimeType.equals("application/pdf"))
                return "pdf";
        }

        // 根据文件扩展名判断
        String ext = fileExtension.toLowerCase();
        if (Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "tif").contains(ext)) {
            return "image";
        }
        if (Set.of("mp3", "wav", "m4a", "aac", "ogg", "flac", "wma", "aiff").contains(ext)) {
            return "audio";
        }
        if (Set.of("mp4", "avi", "mov", "mkv", "wmv", "flv", "webm", "m4v", "mpeg", "mpg").contains(ext)) {
            return "video";
        }
        if (Set.of("pdf").contains(ext)) {
            return "pdf";
        }
        return "text";
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

            // 支持老版本 DOC 格式 (Word 97-2003)
            if (fileType.equals("doc")) {
                try (java.io.InputStream is = file.getInputStream();
                        org.apache.poi.hwpf.HWPFDocument doc = new org.apache.poi.hwpf.HWPFDocument(is)) {
                    org.apache.poi.hwpf.extractor.WordExtractor extractor = new org.apache.poi.hwpf.extractor.WordExtractor(
                            doc);
                    String[] paragraphs = extractor.getParagraphText();
                    StringBuilder text = new StringBuilder();
                    for (String para : paragraphs) {
                        text.append(para).append("\n");
                        if (text.length() > 500)
                            break;
                    }
                    extractor.close();
                    return text.length() > 500 ? text.substring(0, 500) : text.toString();
                } catch (Exception e) {
                    log.warn("Failed to extract DOC content: {}", e.getMessage());
                    return "[DOC Extraction Failed]";
                }
            }

            return null;
        } catch (IOException e) {
            log.warn("Failed to extract content preview", e);
            return null;
        }
    }

    public String readFileContent(KnowledgeDocument doc) throws IOException {
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
        } else if (fileType.equalsIgnoreCase("doc")) {
            try (java.io.InputStream is = Files.newInputStream(path);
                    org.apache.poi.hwpf.HWPFDocument document = new org.apache.poi.hwpf.HWPFDocument(is)) {
                org.apache.poi.hwpf.extractor.WordExtractor extractor = new org.apache.poi.hwpf.extractor.WordExtractor(
                        document);
                String[] paragraphs = extractor.getParagraphText();
                StringBuilder text = new StringBuilder();
                for (String para : paragraphs) {
                    text.append(para).append("\n");
                }
                extractor.close();
                return text.toString();
            } catch (Exception e) {
                log.warn("Failed to read DOC content: {}", e.getMessage());
                return doc.getContentPreview();
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
     * 获取文档解析内容
     */
    public Map<String, Object> getDocumentContent(String documentId) {
        KnowledgeDocument doc = getDocument(documentId);

        Map<String, Object> result = new HashMap<>();
        result.put("fileName", doc.getFileName());
        result.put("charCount", doc.getCharCount());
        String mediaType = doc.getMediaType();
        if (mediaType == null || mediaType.isEmpty()) {
            mediaType = determineMediaType(doc.getFileType(), null);
        }
        result.put("mediaType", mediaType);
        result.put("id", doc.getId());

        // 优先读取解析后的文件 (parsedTextPath)
        if (doc.getParsedTextPath() != null && Files.exists(Paths.get(doc.getParsedTextPath()))) {
            try {
                String content = Files.readString(Paths.get(doc.getParsedTextPath()));
                result.put("text", content);
                result.put("charCount", content.length());
            } catch (IOException e) {
                log.warn("Failed to read parsed content: {}", e.getMessage());
                // Fallback to content preview
                result.put("text", doc.getContentPreview() != null ? doc.getContentPreview() : "");
            }
        } else {
            // Fallback to original content extraction
            try {
                String content = readFileContent(doc);
                if (content != null) {
                    result.put("text", content);
                } else {
                    // readFileContent 不支持图片类型，fallback 到 contentPreview
                    result.put("text", doc.getContentPreview() != null ? doc.getContentPreview() : "");
                }
            } catch (IOException e) {
                result.put("text", doc.getContentPreview() != null ? doc.getContentPreview() : "");
            }
        }

        return result;
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
        } else if (extension.equals("doc")) {
            try (java.io.InputStream is = file.getInputStream();
                    org.apache.poi.hwpf.HWPFDocument document = new org.apache.poi.hwpf.HWPFDocument(is)) {
                org.apache.poi.hwpf.extractor.WordExtractor extractor = new org.apache.poi.hwpf.extractor.WordExtractor(
                        document);
                String[] paragraphs = extractor.getParagraphText();
                StringBuilder text = new StringBuilder();
                for (String para : paragraphs) {
                    text.append(para).append("\n");
                }
                extractor.close();
                return text.toString();
            } catch (Exception e) {
                log.warn("Failed to parse DOC: {}", e.getMessage());
                return "Failed to parse DOC content.";
            }
        }
        return "";
    }

    /**
     * 获取文档的二进制流 (用于原始文件显示)
     */
    public org.springframework.core.io.Resource getDocumentResource(String documentId) {
        KnowledgeDocument doc = getDocument(documentId);
        Path path = Paths.get(doc.getStoragePath());
        if (!Files.exists(path)) {
            throw new RuntimeException("Original file not found at " + doc.getStoragePath());
        }
        return new org.springframework.core.io.FileSystemResource(path);
    }
}
