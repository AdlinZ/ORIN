package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentManageServiceTest {

    @Mock
    private KnowledgeDocumentRepository documentRepository;

    @InjectMocks
    private DocumentManageService documentManageService;

    @Test
    void testGetDocuments() {
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id("doc-1")
                .knowledgeBaseId("kb-1")
                .fileName("test.pdf")
                .build();

        when(documentRepository.findByKnowledgeBaseIdOrderByUploadTimeDesc("kb-1"))
                .thenReturn(Arrays.asList(doc));

        List<KnowledgeDocument> result = documentManageService.getDocuments("kb-1");

        assertEquals(1, result.size());
        assertEquals("test.pdf", result.get(0).getFileName());
    }

    @Test
    void testGetDocument() {
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id("doc-1")
                .fileName("test.pdf")
                .build();

        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        KnowledgeDocument result = documentManageService.getDocument("doc-1");

        assertNotNull(result);
        assertEquals("test.pdf", result.getFileName());
    }

    @Test
    void testTriggerVectorization() {
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id("doc-1")
                .knowledgeBaseId("kb-1")
                .parseStatus("PARSED")
                .parsedTextPath("/path/to/parsed.txt")
                .vectorStatus("PENDING")
                .build();

        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeDocument result = documentManageService.triggerVectorization("doc-1");

        assertEquals("INDEXING", result.getVectorStatus());
        verify(documentRepository).save(any(KnowledgeDocument.class));
    }

    @Test
    void updateVectorizationStatus_shouldPersistSafeFailureReason() {
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id("doc-1")
                .vectorStatus("INDEXING")
                .build();
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeDocument result = documentManageService.updateVectorizationStatus(
                "doc-1", "FAILED", null, null);

        assertEquals("FAILED", result.getVectorStatus());
        assertEquals("向量化处理失败，请检查 Embedding 与 Milvus 配置", result.getVectorError());
    }

    @Test
    void toSafeVectorError_shouldClassifyErrorsWithoutPersistingProviderPayload() {
        String result = DocumentManageService.toSafeVectorError(
                new RuntimeException("401 unauthorized api key sk-sensitive-value"));

        assertEquals("Embedding Provider 配置无效或不可用", result);
        assertFalse(result.contains("sk-sensitive-value"));
    }

    @Test
    void toSafeVectorError_shouldRecognizeDimensionMismatch() {
        assertEquals(
                "Embedding 向量维度与 Milvus Collection 不匹配",
                DocumentManageService.toSafeVectorError(
                        new RuntimeException("vector dimension mismatch")));
    }

    @Test
    void toSafeVectorError_shouldRecognizeMilvusUnavailable() {
        assertEquals(
                "Milvus 向量服务不可用或连接超时",
                DocumentManageService.toSafeVectorError(
                        new RuntimeException("milvus connection refused")));
    }

    @Test
    void toSafeVectorError_shouldRecognizeEmptyParsedContent() {
        assertEquals(
                "文档解析内容为空，无法向量化",
                DocumentManageService.toSafeVectorError(
                        new RuntimeException("parsed text is empty")));
    }
}
