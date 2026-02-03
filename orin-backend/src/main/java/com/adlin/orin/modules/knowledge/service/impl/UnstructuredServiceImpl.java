package com.adlin.orin.modules.knowledge.service.impl;

import com.adlin.orin.common.service.FileStorageService;
import com.adlin.orin.modules.knowledge.component.VectorStoreProvider;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.service.UnstructuredService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnstructuredServiceImpl implements UnstructuredService {

    private final KnowledgeDocumentRepository documentRepository;
    private final VectorStoreProvider vectorStoreProvider;
    private final FileStorageService fileStorageService;

    @Override
    public KnowledgeDocument uploadDocument(MultipartFile file, String kbId, String agentId) {
        log.info("Uploading document: {} for agent: {}", file.getOriginalFilename(), agentId);

        String storagePath = null;
        try {
            // Save physical copy to "knowledge" subdirectory (more specific than
            // "documents")
            storagePath = fileStorageService.storeFile(file, "knowledge");
        } catch (Exception e) {
            log.error("Failed to store physical file", e);
            throw new RuntimeException("Failed to store file", e);
        }

        // 1. Save metadata (Fixed Builder Fields)
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id(UUID.randomUUID().toString())
                .knowledgeBaseId(kbId)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .storagePath(storagePath) // Persist path
                .vectorStatus("PENDING")
                .uploadTime(LocalDateTime.now())
                .lastModified(LocalDateTime.now())
                .build();

        documentRepository.save(doc);

        // 2. Async Process (Parse -> Slice -> Embed)
        processDocumentAsync(doc, file);

        return doc;
    }

    @Async
    public void processDocumentAsync(KnowledgeDocument doc, MultipartFile file) {
        try {
            log.info("Starting processing for doc: {}", doc.getFileName());

            String content = "";
            String fileName = (file.getOriginalFilename() != null) ? file.getOriginalFilename().toLowerCase()
                    : "unknown";

            if (fileName.endsWith(".pdf")) {
                // PDF Parsing
                try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader
                        .loadPDF(file.getBytes())) {
                    if (!document.isEncrypted()) {
                        org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                        content = stripper.getText(document);
                    } else {
                        log.warn("Encrypted PDF not supported yet: {}", fileName);
                        throw new RuntimeException("Encrypted PDF not supported");
                    }
                }
            } else if (fileName.endsWith(".txt") || fileName.endsWith(".md") || fileName.endsWith(".json")) {
                // Plain Text Parsing
                content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } else {
                log.warn("Unsupported file type for parsing: {}", fileName);
                // Don't fail, just mark as skipped or store unsupported
                content = "[Unsupported File Type: " + fileName + "]";
            }

            // Simple chunking (e.g., every 500 chars)
            int chunkSize = 500;
            int length = content.length();
            java.util.List<com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk> chunks_list = new java.util.ArrayList<>();

            // Limit content preview
            doc.setContentPreview(content.substring(0, Math.min(length, 200)));

            for (int i = 0; i < length; i += chunkSize) {
                String chunkContent = content.substring(i, Math.min(length, i + chunkSize));
                chunks_list.add(com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk.builder()
                        .documentId(doc.getId())
                        .content(chunkContent)
                        .charCount(chunkContent.length())
                        .chunkIndex(chunks_list.size())
                        .build());
            }

            // Call provider to insert chunks
            vectorStoreProvider.addChunks(doc.getKnowledgeBaseId(), chunks_list);

            doc.setVectorStatus("COMPLETED");
            doc.setChunkCount(chunks_list.size());
            doc.setCharCount(length);
            documentRepository.save(doc);

            log.info("Document processed successfully: {}. Chunks: {}", doc.getFileName(), chunks_list.size());

        } catch (Exception e) {
            log.error("Failed to process document: {}", e.getMessage(), e);
            doc.setVectorStatus("FAILED");
            documentRepository.save(doc);
        }
    }

    @Override
    public void reIndexDocument(String documentId) {
        log.info("Re-indexing document: {}", documentId);
        // Logic to re-read file and update embeddings
    }

    @Override
    public List<String> retrieveContext(String agentId, String query, int limit) {
        String collectionName = "kb_" + agentId;
        List<VectorStoreProvider.SearchResult> results = vectorStoreProvider.search(collectionName, query, limit);
        return results.stream()
                .map(VectorStoreProvider.SearchResult::getContent)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteDocument(String documentId) {
        documentRepository.deleteById(documentId);
        // Also cleanup vector store
    }
}
