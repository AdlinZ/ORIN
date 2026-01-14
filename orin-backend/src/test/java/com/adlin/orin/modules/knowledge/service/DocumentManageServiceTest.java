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
                .vectorStatus("PENDING")
                .build();

        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(KnowledgeDocument.class))).thenReturn(doc);

        KnowledgeDocument result = documentManageService.triggerVectorization("doc-1");

        assertEquals("INDEXING", result.getVectorStatus());
        verify(documentRepository).save(doc);
    }
}
