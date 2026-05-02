package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentChunkRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.component.VectorStoreProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock
    private MilvusVectorService vectorService;

    @Mock
    private KnowledgeDocumentChunkRepository chunkRepository;

    @Mock
    private KnowledgeDocumentRepository documentRepository;

    @Mock
    private StorageManagementService storageManagementService;

    @Mock
    private com.adlin.orin.modules.multimodal.service.VisualAnalysisService visualAnalysisService;

    @Mock
    private RerankService rerankService;

    @InjectMocks
    private RetrievalService retrievalService;

    private List<VectorStoreProvider.SearchResult> mockChildResults;
    private List<KnowledgeDocumentChunk> mockParentChunks;

    @BeforeEach
    void setUp() {
        // Mock vector search results (child chunks)
        Map<String, Object> meta1 = new HashMap<>();
        meta1.put("parent_id", "parent-1");
        meta1.put("doc_id", "doc-1");
        meta1.put("chunk_type", "child");

        Map<String, Object> meta2 = new HashMap<>();
        meta2.put("parent_id", "parent-2");
        meta2.put("doc_id", "doc-1");
        meta2.put("chunk_type", "child");

        mockChildResults = Arrays.asList(
            VectorStoreProvider.SearchResult.builder()
                .content("Child content 1")
                .score(0.95)
                .matchType("VECTOR")
                .metadata(meta1)
                .build(),
            VectorStoreProvider.SearchResult.builder()
                .content("Child content 2")
                .score(0.90)
                .matchType("VECTOR")
                .metadata(meta2)
                .build()
        );

        // Mock parent chunks from database
        mockParentChunks = Arrays.asList(
            KnowledgeDocumentChunk.builder()
                .id("parent-1")
                .documentId("doc-1")
                .chunkType("parent")
                .content("Parent content 1 - This is the full parent context")
                .title("Document Section 1")
                .source("test.pdf")
                .build(),
            KnowledgeDocumentChunk.builder()
                .id("parent-2")
                .documentId("doc-1")
                .chunkType("parent")
                .content("Parent content 2 - Another section")
                .title("Document Section 2")
                .source("test.pdf")
                .build()
        );
    }

    @Test
    void testHybridSearch_WithVectorResults() {
        // Setup mocks
        when(vectorService.isHealthy()).thenReturn(true);
        when(vectorService.search(eq("kb-001"), anyString(), anyInt(), any()))
            .thenReturn(mockChildResults);

        when(chunkRepository.findByDocumentIdAndChunkType(eq("doc-1"), eq("parent")))
            .thenReturn(mockParentChunks);

        when(chunkRepository.searchByKeyword(anyString(), anyString()))
            .thenReturn(Collections.emptyList());

        // Execute
        List<VectorStoreProvider.SearchResult> results = retrievalService.hybridSearch(
            "kb-001", "test query", 5);

        // Verify
        assertNotNull(results);
        verify(vectorService, times(1)).search(eq("kb-001"), anyString(), anyInt(), any());
    }

    @Test
    void testHybridSearch_EmptyVectorResults_FallsbackToKeyword() {
        // Setup mocks - healthy vector service but returns empty results
        when(vectorService.isHealthy()).thenReturn(true);
        when(vectorService.search(anyString(), anyString(), anyInt(), any()))
            .thenReturn(Collections.emptyList());

        // Mock keyword search results
        KnowledgeDocumentChunk keywordChunk = KnowledgeDocumentChunk.builder()
            .id("keyword-1")
            .documentId("doc-1")
            .chunkType("child")
            .content("This content matches keywords")
            .build();
        when(chunkRepository.searchByKeyword(anyString(), anyString()))
            .thenReturn(Arrays.asList(keywordChunk));

        // Execute
        List<VectorStoreProvider.SearchResult> results = retrievalService.hybridSearch(
            "kb-001", "test query", 5);

        // Verify - should return keyword results
        assertNotNull(results);
        assertTrue(results.stream().anyMatch(r -> "KEYWORD".equals(r.getMatchType())));
    }

    @Test
    void testHybridSearch_GlobalSearch() {
        // Setup mocks for global search
        when(vectorService.isHealthy()).thenReturn(true);
        when(vectorService.search(eq("all"), anyString(), anyInt(), any()))
            .thenReturn(mockChildResults);

        // Use lenient for findByDocumentIdAndChunkType since it may or may not be called
        // depending on whether vector results have matching parent chunks
        lenient().when(chunkRepository.findByDocumentIdAndChunkType(anyString(), eq("parent")))
            .thenReturn(mockParentChunks);

        // Execute
        List<VectorStoreProvider.SearchResult> results = retrievalService.hybridSearch(
            "all", "test query", 5);

        // Verify
        assertNotNull(results);
        verify(vectorService, times(1)).search(eq("all"), anyString(), anyInt(), any());
    }

    @Test
    void testHybridSearch_DefaultParameters() {
        // Test overloaded method with default parameters
        when(vectorService.isHealthy()).thenReturn(true);
        when(vectorService.search(anyString(), anyString(), anyInt(), isNull()))
            .thenReturn(Collections.emptyList());

        when(chunkRepository.searchByKeyword(anyString(), anyString()))
            .thenReturn(Collections.emptyList());

        // Execute with default parameters (no embedding model)
        List<VectorStoreProvider.SearchResult> results = retrievalService.hybridSearch(
            "kb-001", "test query", 3);

        // Verify - empty vector results should trigger keyword fallback
        assertNotNull(results);
        verify(vectorService, times(1)).isHealthy();
    }

    @Test
    void testMultimodalSearch_WithKB() {
        // Setup mocks
        when(visualAnalysisService.analyzeImage(anyString(), anyString()))
            .thenReturn("A picture showing a cat sitting on a desk");

        when(vectorService.isHealthy()).thenReturn(true);
        when(vectorService.search(anyString(), anyString(), anyInt(), any()))
            .thenReturn(mockChildResults);

        when(chunkRepository.findByDocumentIdAndChunkType(anyString(), eq("parent")))
            .thenReturn(mockParentChunks);

        when(chunkRepository.searchByKeyword(anyString(), anyString()))
            .thenReturn(Collections.emptyList());

        // Execute
        Map<String, Object> results = retrievalService.multimodalSearch(
            "kb-001", "http://example.com/image.jpg", "qwen-vl-max", "bge-m3", 5);

        // Verify
        assertNotNull(results);
        assertEquals("A picture showing a cat sitting on a desk", results.get("description"));
        assertNotNull(results.get("results"));
        verify(visualAnalysisService, times(1)).analyzeImage(anyString(), anyString());
    }

    @Test
    void testMultimodalSearch_NoKB() {
        // Setup mocks - VLM analysis only
        when(visualAnalysisService.analyzeImage(anyString(), anyString()))
            .thenReturn("Image description");

        // Execute with no KB
        Map<String, Object> results = retrievalService.multimodalSearch(
            null, "http://example.com/image.jpg", "qwen-vl-max", "bge-m3", 5);

        // Verify
        assertNotNull(results);
        assertEquals("Image description", results.get("description"));
        assertTrue(((List<?>)results.get("results")).isEmpty());
    }

    @Test
    void testMultimodalSearch_NoneKB() {
        // Setup mocks
        when(visualAnalysisService.analyzeImage(anyString(), anyString()))
            .thenReturn("Image description");

        // Execute with "none" KB
        Map<String, Object> results = retrievalService.multimodalSearch(
            "none", "http://example.com/image.jpg", "qwen-vl-max", "bge-m3", 5);

        // Verify - should only do VLM analysis
        assertNotNull(results);
        assertEquals("Image description", results.get("description"));
        assertTrue(((List<?>)results.get("results")).isEmpty());
    }
}
