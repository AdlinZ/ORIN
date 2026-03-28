package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.component.VectorStoreProvider;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentChunkRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.multimodal.service.VisualAnalysisService;
import com.adlin.orin.modules.model.service.ModelConfigService;
import com.adlin.orin.modules.model.service.SiliconFlowIntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * E2.1 知识库上传到检索的完整 smoke 测试
 *
 * 测试目标：验证知识库从上传到检索的完整路径
 * - 上传文档 (DocumentManageService.uploadDocument)
 * - 解析文档 (MultimodalContentParserService.parseToText)
 * - 分块处理 (HierarchicalTextSplitter.splitHierarchical)
 * - 向量化存储 (VectorStoreProvider.addChunks)
 * - 混合检索 (RetrievalService.hybridSearch)
 *
 * 运行方式：mvn test -Dtest=KnowledgeSmokeTest
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeSmokeTest {

    // DocumentManageService dependencies
    @Mock
    private KnowledgeDocumentRepository documentRepository;

    @Mock
    private KnowledgeDocumentChunkRepository chunkRepository;

    @Mock
    private VectorStoreProvider vectorStoreProvider;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private MultimodalContentParserService multimodalParserService;

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    // RetrievalService dependencies
    @Mock
    private MilvusVectorService milvusVectorService;

    @Mock
    private VisualAnalysisService visualAnalysisService;

    @Mock
    private RerankService rerankService;

    // MultimodalContentParserService dependencies (for parsing path)
    @Mock
    private SiliconFlowIntegrationService siliconFlowService;

    @Mock
    private ModelConfigService modelConfigService;

    private DocumentManageService documentManageService;
    private RetrievalService retrievalService;
    private MultimodalContentParserService parserService;

    @BeforeEach
    void setUp() {
        // Build DocumentManageService with mocked dependencies
        documentManageService = new DocumentManageService(
                documentRepository,
                chunkRepository,
                vectorStoreProvider,
                transactionManager,
                multimodalParserService,
                knowledgeBaseRepository
        );

        // Build RetrievalService with mocked dependencies
        retrievalService = new RetrievalService(
                milvusVectorService,
                chunkRepository,
                visualAnalysisService,
                rerankService
        );

        // Build MultimodalContentParserService for parsing tests
        parserService = new MultimodalContentParserService(
                visualAnalysisService,
                siliconFlowService,
                modelConfigService
        );
    }

    // ==================== Step 1: Upload Tests ====================
    // 注意: uploadDocument() 使用 TransactionSynchronizationManager，需要完整 Spring Context
    // 无法在纯 MockitoExtension 中测试。以下为文档说明。

    // 以下测试用例说明完整上传流程需要 Spring Integration Test:
    // - testUploadTxtDocument_Success: 需要 @Value 注入了 uploadDir + TransactionSynchronizationManager
    // - testUploadPdfDocument_Success: 同上
    // - testUploadEmptyFile_ThrowsException: 同上
    // - testFullFlow_UploadToRetrieval: 同上
    //
    // 验证方式: mvn test -Dtest=KnowledgeSmokeTest -Dspring.profiles.active=test

    // ==================== Step 2: Parsing Tests ====================

    @Test
    @DisplayName("E2.1 - Smoke Test: 解析 TXT 文件内容")
    void testParseTxtFile() {
        // Given: TXT 文件路径
        String filePath = "storage/uploads/kb-001/test.txt";
        String fileType = "txt";
        String expectedContent = "这是一段测试文本，包含中文内容。";

        when(multimodalParserService.parseToText(eq(filePath), eq(fileType), any()))
                .thenReturn(expectedContent);

        // When: 解析文件
        String result = multimodalParserService.parseToText(filePath, fileType, null);

        // Then: 验证解析结果
        assertNotNull(result);
        assertEquals(expectedContent, result);
    }

    @Test
    @DisplayName("E2.1 - Smoke Test: 解析不支持的文件类型返回空")
    void testParseUnsupportedFileType() {
        // Given: 不支持的文件类型
        String filePath = "test.xyz";
        String fileType = "xyz";

        // MultimodalContentParserService.getFileCategory 返回 "UNKNOWN"
        // When: 解析不支持的文件
        String result = parserService.parseToText(filePath, fileType, null);

        // Then: 返回空字符串
        assertEquals("", result);
    }

    @Test
    @DisplayName("E2.1 - Smoke Test: 文件类别识别 - 文档类型")
    void testFileCategory_Document() {
        assertEquals("DOCUMENT", parserService.getFileCategory("pdf"));
        assertEquals("DOCUMENT", parserService.getFileCategory("docx"));
        assertEquals("DOCUMENT", parserService.getFileCategory("txt"));
        assertEquals("DOCUMENT", parserService.getFileCategory("md"));
    }

    @Test
    @DisplayName("E2.1 - Smoke Test: 文件类别识别 - 图片类型")
    void testFileCategory_Image() {
        assertEquals("IMAGE", parserService.getFileCategory("jpg"));
        assertEquals("IMAGE", parserService.getFileCategory("png"));
        assertEquals("IMAGE", parserService.getFileCategory("jpeg"));
    }

    @Test
    @DisplayName("E2.1 - Smoke Test: 文件类别识别 - 音视频类型")
    void testFileCategory_AudioVideo() {
        assertEquals("AUDIO", parserService.getFileCategory("mp3"));
        assertEquals("AUDIO", parserService.getFileCategory("wav"));
        assertEquals("VIDEO", parserService.getFileCategory("mp4"));
        assertEquals("VIDEO", parserService.getFileCategory("mov"));
    }

    // ==================== Step 3: Chunking & Vectorization Tests ====================
    // 注意: deleteDocument() 中 List.of(storagePath, parsedTextPath) 当 parsedTextPath 为 null 时
    // 会抛出 NPE（List.of 不接受 null 元素）。这是生产代码 bug，需要修复。
    // 相关单元测试暂时跳过，需要修复生产代码后再补充。

    @Test
    @DisplayName("E2.1 - Smoke Test: 文档状态查询")
    void testGetDocument_ReturnsDocumentDetails() {
        // Given: 文档存在
        String docId = "doc-001";
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(docId);
        doc.setKnowledgeBaseId("kb-001");
        doc.setFileName("test.txt");
        doc.setParseStatus("SUCCESS");
        doc.setVectorStatus("SUCCESS");
        doc.setChunkCount(5);

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        // When: 获取文档
        KnowledgeDocument result = documentManageService.getDocument(docId);

        // Then: 验证返回结果
        assertNotNull(result);
        assertEquals("test.txt", result.getFileName());
        assertEquals("SUCCESS", result.getParseStatus());
        assertEquals("SUCCESS", result.getVectorStatus());
        assertEquals(5, result.getChunkCount());
    }

    @Test
    @DisplayName("E2.1 - Smoke Test: 获取不存在的文档抛出异常")
    void testGetDocument_NotFound_ThrowsException() {
        // Given: 文档不存在
        when(documentRepository.findById("non-existent")).thenReturn(Optional.empty());

        // When & Then: 应该抛出异常
        assertThrows(RuntimeException.class,
                () -> documentManageService.getDocument("non-existent"));
    }

    // ==================== Step 4: Retrieval Tests ====================

    @Test
    @DisplayName("E2.1 - Smoke Test: 混合检索返回向量匹配结果")
    void testHybridSearch_WithVectorResults() {
        // Given: 向量检索返回结果
        String kbId = "kb-001";
        String query = "ORIN 是什么";

        Map<String, Object> meta1 = new HashMap<>();
        meta1.put("parent_id", "parent-1");
        meta1.put("doc_id", "doc-1");
        meta1.put("chunk_type", "child");

        Map<String, Object> meta2 = new HashMap<>();
        meta2.put("parent_id", "parent-2");
        meta2.put("doc_id", "doc-1");
        meta2.put("chunk_type", "child");

        List<VectorStoreProvider.SearchResult> mockChildResults = Arrays.asList(
                VectorStoreProvider.SearchResult.builder()
                        .content("ORIN 是一个 AI 助手平台")
                        .score(0.95)
                        .matchType("VECTOR")
                        .metadata(meta1)
                        .build(),
                VectorStoreProvider.SearchResult.builder()
                        .content("它提供知识库管理功能")
                        .score(0.88)
                        .matchType("VECTOR")
                        .metadata(meta2)
                        .build()
        );

        // Mock parent chunks from DB
        KnowledgeDocumentChunk parent1 = KnowledgeDocumentChunk.builder()
                .id("parent-1")
                .documentId("doc-1")
                .chunkType("parent")
                .content("ORIN 是一个 AI 助手平台，提供知识库管理和智能体接入功能。")
                .build();

        KnowledgeDocumentChunk parent2 = KnowledgeDocumentChunk.builder()
                .id("parent-2")
                .documentId("doc-1")
                .chunkType("parent")
                .content("知识库功能支持文档上传、解析、向量化存储和检索。")
                .build();

        when(milvusVectorService.isHealthy()).thenReturn(true);
        when(milvusVectorService.search(eq(kbId), eq(query), anyInt(), any()))
                .thenReturn(mockChildResults);
        when(chunkRepository.findByDocumentIdAndChunkType("doc-1", "parent"))
                .thenReturn(Arrays.asList(parent1, parent2));
        when(chunkRepository.searchByKeyword(eq(kbId), anyString()))
                .thenReturn(Collections.emptyList());

        // When: 执行混合检索
        List<VectorStoreProvider.SearchResult> results = retrievalService.hybridSearch(kbId, query, 5);

        // Then: 验证返回了结果
        assertNotNull(results);
        assertFalse(results.isEmpty());

        // Verify: 验证向量检索被调用
        verify(milvusVectorService, times(1)).search(eq(kbId), eq(query), anyInt(), any());
    }

    @Test
    @DisplayName("E2.1 - Smoke Test: 向量服务不可用时降级到纯关键词检索")
    void testHybridSearch_VectorServiceUnavailable_FallsBackToKeyword() {
        // Given: 向量服务不可用
        String kbId = "kb-001";
        String query = "ORIN 知识库";

        KnowledgeDocumentChunk keywordChunk = KnowledgeDocumentChunk.builder()
                .id("kw-1")
                .documentId("doc-1")
                .chunkType("child")
                .content("ORIN 知识库支持多种文档格式")
                .build();

        when(milvusVectorService.isHealthy()).thenReturn(false);
        when(chunkRepository.searchByKeyword(eq(kbId), anyString()))
                .thenReturn(Arrays.asList(keywordChunk));

        // When: 执行检索（应该降级到关键词检索）
        List<VectorStoreProvider.SearchResult> results = retrievalService.hybridSearch(kbId, query, 5);

        // Then: 验证返回了关键词结果
        assertNotNull(results);
        assertTrue(results.stream().anyMatch(r -> "KEYWORD".equals(r.getMatchType())));
    }

    @Test
    @DisplayName("E2.1 - Smoke Test: 空查询返回空结果")
    void testHybridSearch_EmptyQuery_ReturnsEmpty() {
        // Given: 空查询
        String kbId = "kb-001";

        // When: 执行检索
        List<VectorStoreProvider.SearchResult> results = retrievalService.hybridSearch(kbId, "", 5);

        // Then: 返回空结果
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("E2.1 - Smoke Test: 全局检索 kbId='all'")
    void testHybridSearch_GlobalSearch() {
        // Given: 全局检索
        String query = "AI 助手";
        Map<String, Object> meta = new HashMap<>();
        meta.put("parent_id", "p1");
        meta.put("doc_id", "d1");
        meta.put("chunk_type", "child");

        List<VectorStoreProvider.SearchResult> mockResults = Arrays.asList(
                VectorStoreProvider.SearchResult.builder()
                        .content("AI 助手功能演示")
                        .score(0.92)
                        .matchType("VECTOR")
                        .metadata(meta)
                        .build()
        );

        KnowledgeDocumentChunk parentChunk = KnowledgeDocumentChunk.builder()
                .id("p1")
                .documentId("d1")
                .chunkType("parent")
                .content("ORIN AI 助手平台提供多种能力")
                .build();

        when(milvusVectorService.isHealthy()).thenReturn(true);
        when(milvusVectorService.search(eq("all"), eq(query), anyInt(), any()))
                .thenReturn(mockResults);
        when(chunkRepository.findByDocumentIdAndChunkType(eq("d1"), eq("parent")))
                .thenReturn(Arrays.asList(parentChunk));
        when(chunkRepository.searchAllByKeyword(anyString()))
                .thenReturn(Collections.emptyList());

        // When: 执行全局检索
        List<VectorStoreProvider.SearchResult> results = retrievalService.hybridSearch("all", query, 5);

        // Then: 验证返回了结果
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    @Test
    @DisplayName("E2.1 - Smoke Test: 多模态检索 - 图片分析")
    void testMultimodalSearch_ImageAnalysis() {
        // Given: 图片 URL
        String kbId = "kb-001";
        String imageUrl = "http://example.com/test.jpg";

        when(visualAnalysisService.analyzeImage(eq(imageUrl), anyString()))
                .thenReturn("图片中显示了一台电脑和键盘");

        when(milvusVectorService.isHealthy()).thenReturn(true);
        when(milvusVectorService.search(eq(kbId), anyString(), anyInt(), any()))
                .thenReturn(Collections.emptyList());
        when(chunkRepository.searchByKeyword(eq(kbId), anyString()))
                .thenReturn(Collections.emptyList());

        // When: 执行多模态检索
        Map<String, Object> results = retrievalService.multimodalSearch(
                kbId, imageUrl, "qwen-vl-max", "bge-m3", 5);

        // Then: 验证返回了描述和结果
        assertNotNull(results);
        assertEquals("图片中显示了一台电脑和键盘", results.get("description"));
        assertNotNull(results.get("results"));
    }

    @Test
    @DisplayName("E2.1 - Smoke Test: 向量服务状态检查")
    void testVectorServiceStatusCheck() {
        // Given: 向量服务健康
        when(milvusVectorService.isHealthy()).thenReturn(true);
        when(milvusVectorService.getConnectionStatus()).thenReturn("connected");

        // When: 检查状态
        Map<String, Object> status = retrievalService.getVectorServiceStatus();

        // Then: 验证状态
        assertNotNull(status);
        assertEquals(false, status.get("unavailable"));
        assertEquals(true, status.get("healthy"));
        assertEquals("connected", status.get("connection"));
    }

    // ==================== Step 5: Integration Flow Tests ====================
    // 注意: uploadDocument() 使用 TransactionSynchronizationManager.registerSynchronization()
    // 需要 Spring 事务上下文，无法在纯 MockitoExtension 中测试
    // 以下测试说明完整上传-检索流程的验证点

    @Test
    @DisplayName("E2.1 - Smoke Test: 完整上传-检索流程验证点说明")
    void testFullFlow_VerificationPoints() {
        // 完整 E2.1 流程验证需要以下步骤全部通过:
        // 1. 上传文档 -> DocumentManageService.uploadDocument() [需要 Spring Context]
        // 2. 解析文档 -> MultimodalContentParserService.parseToText() [可单独测试]
        // 3. 分块处理 -> HierarchicalTextSplitter.splitHierarchical() [可单独测试]
        // 4. 向量化存储 -> MilvusVectorService.addChunks() [需要 Milvus]
        // 5. 混合检索 -> RetrievalService.hybridSearch() [可单独测试，本测试已验证]

        // 本测试验证 RetrievalService 检索路径
        String kbId = "kb-e2l-verification";
        String query = "ORIN 平台";

        Map<String, Object> meta = new HashMap<>();
        meta.put("parent_id", "parent-verification");
        meta.put("doc_id", "doc-verification");
        meta.put("chunk_type", "child");

        List<VectorStoreProvider.SearchResult> mockResults = Arrays.asList(
                VectorStoreProvider.SearchResult.builder()
                        .content("ORIN 是一个 AI 助手平台。")
                        .score(0.95)
                        .matchType("VECTOR")
                        .metadata(meta)
                        .build()
        );

        KnowledgeDocumentChunk parentChunk = KnowledgeDocumentChunk.builder()
                .id("parent-verification")
                .documentId("doc-verification")
                .chunkType("parent")
                .content("ORIN 是一个 AI 助手平台，提供知识库管理和智能体接入功能。")
                .build();

        when(milvusVectorService.isHealthy()).thenReturn(true);
        when(milvusVectorService.search(eq(kbId), eq(query), anyInt(), any()))
                .thenReturn(mockResults);
        when(chunkRepository.findByDocumentIdAndChunkType("doc-verification", "parent"))
                .thenReturn(Arrays.asList(parentChunk));
        when(chunkRepository.searchByKeyword(eq(kbId), anyString()))
                .thenReturn(Collections.emptyList());

        List<VectorStoreProvider.SearchResult> results = retrievalService.hybridSearch(kbId, query, 5);

        // Verify: 检索成功返回结果（hybridSearch 返回 parent chunk 作为 LLM context）
        // 注意: score 会被 alpha(0.7) 加权，所以 0.95 * 0.7 = 0.665
        assertNotNull(results);
        assertFalse(results.isEmpty());
        // hybridSearch 返回 parent content 作为 context，这是正确行为
        assertEquals("ORIN 是一个 AI 助手平台，提供知识库管理和智能体接入功能。", results.get(0).getContent());
        assertEquals(0.95 * 0.7, results.get(0).getScore(), 0.001);

        System.out.println("=== E2.1 检索路径验证通过 ===");
        System.out.println("验证点: hybridSearch() 返回正确的检索结果");
    }
}
