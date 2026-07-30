package com.adlin.orin.modules.knowledge.controller;

import com.adlin.orin.common.enums.TaskStatus;
import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.entity.KnowledgeTask;
import com.adlin.orin.modules.knowledge.entity.KnowledgeType;
import com.adlin.orin.modules.knowledge.repository.KnowledgeTaskRepository;
import com.adlin.orin.modules.knowledge.service.DocumentManageService;
import com.adlin.orin.modules.knowledge.service.KnowledgeManageService;
import com.adlin.orin.modules.knowledge.service.MilvusVectorService;
import com.adlin.orin.modules.knowledge.dto.UnifiedKnowledgeDTO;
import com.adlin.orin.modules.knowledge.dto.KeywordTag;
import com.adlin.orin.modules.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.adlin.orin.modules.knowledge.service.meta.MetaKnowledgeService;
import com.adlin.orin.modules.knowledge.component.EmbeddingService;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
@Tag(name = "Phase 4: Asset Management", description = "知识资产管理")
public class KnowledgeManageController {

    private final KnowledgeManageService knowledgeManageService;
    private final DocumentManageService documentManageService;
    private final MetaKnowledgeService metaKnowledgeService;
    private final com.adlin.orin.modules.knowledge.service.RetrievalService retrievalService;
    private final com.adlin.orin.modules.knowledge.service.StructuredService structuredService;
    private final com.adlin.orin.modules.knowledge.service.ProceduralService proceduralService;
    private final com.adlin.orin.modules.agent.service.AgentManageService agentManageService;
    private final com.adlin.orin.modules.knowledge.service.ExternalSyncService externalSyncService;
    private final MilvusVectorService milvusVectorService;
    private final EmbeddingService embeddingService;
    private final AuditLogService auditLogService;
    private final com.adlin.orin.modules.knowledge.service.KeywordExtractService keywordExtractService;
    private final KnowledgeTaskRepository knowledgeTaskRepository;

    @Value("${milvus.host}")
    private String milvusHost;

    @Value("${milvus.port}")
    private int milvusPort;

    // ==================== Milvus Collection 管理 API ====================

    @Operation(summary = "获取 Collection 信息")
    @GetMapping("/collection/info")
    public Map<String, Object> getCollectionInfo() {
        return milvusVectorService.getCollectionInfo();
    }

    @Operation(summary = "获取 Milvus 详细数据")
    @GetMapping("/collection/detail")
    public Map<String, Object> getCollectionDetail() {
        return milvusVectorService.getCollectionDetail();
    }

    @Operation(summary = "获取知识库的向量详情")
    @GetMapping("/kb/{kbId}/vectors")
    public Map<String, Object> getKnowledgeBaseVectors(
            @PathVariable String kbId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return knowledgeManageService.getKnowledgeBaseVectors(kbId, page, size);
    }

    @Operation(summary = "重建 Collection")
    @PostMapping("/collection/recreate")
    public Map<String, Object> recreateCollection() {
        milvusVectorService.recreateCollection();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Collection 已重建");
        return result;
    }

    // ==================== 语义联想 API ====================

    @Operation(summary = "获取知识库关键词列表")
    @GetMapping("/semantic/keywords")
    public List<KeywordTag> getKeywords(
            @RequestParam(required = false) String datasetId,
            @RequestParam(defaultValue = "20") int limit) {
        if (datasetId != null && !datasetId.isEmpty()) {
            return keywordExtractService.extractKeywordsFromKnowledgeBase(datasetId, limit);
        }
        return keywordExtractService.getPopularKeywords(limit);
    }

    @Operation(summary = "获取搜索联想建议")
    @GetMapping("/semantic/search-suggest")
    public List<KeywordTag> getSearchSuggestions(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int limit) {
        return keywordExtractService.getSearchSuggestions(keyword, limit);
    }

    // ==================== 外部同步 API ====================

    @Operation(summary = "测试 Notion 连接")
    @PostMapping("/sync/notion/test")
    public Map<String, Object> testNotionConnection(@RequestBody Map<String, String> config) {
        return externalSyncService.testNotionConnection(config);
    }

    @Operation(summary = "列出 Notion 数据库")
    @PostMapping("/sync/notion/databases")
    public List<Map<String, String>> listNotionDatabases(@RequestBody Map<String, String> config) {
        return externalSyncService.listNotionDatabases(config);
    }

    @Operation(summary = "从 Notion 同步数据")
    @PostMapping("/{kbId}/sync/notion")
    public Map<String, Object> syncFromNotion(
            @PathVariable String kbId,
            @RequestBody Map<String, String> config) {
        return externalSyncService.syncFromNotion(kbId, config);
    }

    @Operation(summary = "测试 Web URL")
    @GetMapping("/sync/web/test")
    public Map<String, Object> testWebUrl(@RequestParam String url) {
        return externalSyncService.testWebUrl(url);
    }

    @Operation(summary = "从 Web 站点同步数据")
    @PostMapping("/{kbId}/sync/web")
    public Map<String, Object> syncFromWeb(
            @PathVariable String kbId,
            @RequestBody Map<String, Object> config) {
        return externalSyncService.syncFromWeb(kbId, config);
    }

    @Operation(summary = "测试数据库连接")
    @PostMapping("/sync/database/test")
    public Map<String, Object> testDatabaseConnection(@RequestBody Map<String, String> config) {
        return externalSyncService.testDatabaseConnection(config);
    }

    // ==================== RAGFlow 同步 API ====================

    @Operation(summary = "测试 RAGFlow 连接")
    @PostMapping("/sync/ragflow/test")
    public Map<String, Object> testRAGFlowConnection(@RequestBody Map<String, String> config) {
        return externalSyncService.testRAGFlowConnection(config);
    }

    @Operation(summary = "列出 RAGFlow 知识库")
    @PostMapping("/sync/ragflow/list")
    public List<Map<String, Object>> listRAGFlowKnowledgeBases(@RequestBody Map<String, String> config) {
        return externalSyncService.listRAGFlowKnowledgeBases(config);
    }

    @Operation(summary = "从 RAGFlow 同步知识库")
    @PostMapping("/{kbId}/sync/ragflow")
    public Map<String, Object> syncFromRAGFlow(
            @PathVariable String kbId,
            @RequestBody Map<String, String> config) {
        return externalSyncService.syncFromRAGFlow(kbId, config);
    }

    @Operation(summary = "从 RAGFlow 检索")
    @PostMapping("/retrieve/ragflow")
    public List<Map<String, Object>> retrievalFromRAGFlow(
            @RequestBody Map<String, String> config,
            @RequestParam String ragflowKbId,
            @RequestParam(defaultValue = "5") int topK) {
        String query = config.get("query");
        return externalSyncService.retrievalFromRAGFlow(config, ragflowKbId, query, topK);
    }

    @Operation(summary = "上传文档到 RAGFlow")
    @PostMapping("/{kbId}/documents/ragflow/upload")
    public Map<String, Object> uploadToRAGFlow(
            @PathVariable String kbId,
            @RequestParam String fileName,
            @RequestBody byte[] fileContent,
            @RequestParam Map<String, String> config) {
        return externalSyncService.uploadToRAGFlow(kbId, config, fileName, fileContent);
    }

    @Operation(summary = "连接外部数据库")
    @PostMapping("/{kbId}/sync/database/connect")
    public Map<String, Object> connectDatabase(
            @PathVariable String kbId,
            @RequestBody Map<String, String> config) {
        return externalSyncService.connectDatabase(kbId, config);
    }

    @Operation(summary = "获取数据库 Schema")
    @GetMapping("/{kbId}/sync/database/schema")
    public List<Map<String, Object>> getDatabaseSchema(@PathVariable String kbId) {
        return externalSyncService.getDatabaseSchema(kbId);
    }

    @Operation(summary = "同步数据库表")
    @PostMapping("/{kbId}/sync/database/table/{tableName}")
    public Map<String, Object> syncDatabaseTable(
            @PathVariable String kbId,
            @PathVariable String tableName) {
        return externalSyncService.syncDatabaseTable(kbId, tableName);
    }

    @Operation(summary = "获取所有知识库列表")
    @GetMapping("/list")
    public List<UnifiedKnowledgeDTO> getAllKnowledgeBases() {
        return knowledgeManageService.getAllKnowledgeBases();
    }

    @Operation(summary = "获取知识库详情")
    @GetMapping("/{kbId}")
    public KnowledgeBase getKnowledgeBase(@PathVariable String kbId) {
        return knowledgeManageService.getKnowledgeBaseById(kbId);
    }

    @Operation(summary = "获取智能体绑定的知识列表 (支持分类型 DOCUMENT/STRUCTURED/API)")
    @GetMapping("/agents/{agentId}")
    public List<?> getBoundKnowledge(
            @PathVariable String agentId,
            @RequestParam(required = false, defaultValue = "DOCUMENT") String type) {

        switch (type.toUpperCase()) {
            case "STRUCTURED":
                return structuredService.getDatabaseSchema(agentId);
            case "API":
                return proceduralService.getAgentSkills(agentId);
            case "DOCUMENT":
            default:
                return knowledgeManageService.getBoundKnowledge(agentId);
        }
    }

    @Operation(summary = "获取智能体绑定的统一知识列表 (Bento Grid 专用)")
    @GetMapping("/agents/{agentId}/unified")
    public List<UnifiedKnowledgeDTO> getUnifiedKnowledge(@PathVariable String agentId) {
        return knowledgeManageService.getUnifiedKnowledge(agentId);
    }

    @Operation(summary = "从 Dify 同步最新知识库")
    @PostMapping("/agents/{agentId}/sync")
    public List<KnowledgeBase> syncKnowledgeBases(@PathVariable String agentId) {
        return knowledgeManageService.syncFromDify(agentId);
    }

    @Operation(summary = "更新知识库状态")
    @PutMapping("/{kbId}/status")
    public KnowledgeBase updateStatus(@PathVariable String kbId, @RequestBody Map<String, Boolean> payload) {
        Boolean enabled = payload.get("enabled");
        if (enabled == null) {
            throw new IllegalArgumentException("Payload must contain 'enabled' boolean field");
        }
        return knowledgeManageService.updateStatus(kbId, enabled);
    }

    @Operation(summary = "创建本地/外部知识库")
    @PostMapping
    public KnowledgeBase createKnowledgeBase(@RequestBody com.adlin.orin.modules.knowledge.entity.KnowledgeBase kb) {
        KnowledgeBase result = knowledgeManageService.createKnowledgeBase(kb);
        // 审计日志
        auditLogService.logApiCall(
                "SYSTEM", null, "KNOWLEDGE", "KNOWLEDGE",
                "/knowledge", "POST", kb.getName(), null, null,
                "{\"name\":\"" + kb.getName() + "\"}", null, 200, null,
                null, null, null, true, null, null, null);
        return result;
    }

    @Operation(summary = "更新知识库基础信息")
    @PutMapping("/{kbId}")
    public KnowledgeBase updateKnowledgeBase(@PathVariable String kbId, @RequestBody KnowledgeBase kb) {
        KnowledgeBase result = knowledgeManageService.updateKnowledgeBase(kbId, kb);
        // 审计日志
        auditLogService.logApiCall(
                "SYSTEM", null, "KNOWLEDGE", "KNOWLEDGE",
                "/knowledge/" + kbId, "PUT", kb.getName(), null, null,
                null, null, 200, null,
                null, null, null, true, null, null, null);
        return result;
    }

    @Operation(summary = "删除知识库")
    @DeleteMapping("/{kbId}")
    public Map<String, String> deleteKnowledgeBase(@PathVariable String kbId) {
        knowledgeManageService.deleteKnowledgeBase(kbId);
        // 审计日志
        auditLogService.logApiCall(
                "SYSTEM", null, "KNOWLEDGE", "KNOWLEDGE",
                "/knowledge/" + kbId, "DELETE", kbId, null, null,
                null, null, 200, null,
                null, null, null, true, null, null, null);
        return Map.of("status", "success", "message", "Knowledge Base deleted successfully");
    }

    @Operation(summary = "AI生成知识库描述")
    @PostMapping("/{kbId}/generate-description")
    public Map<String, String> generateDescription(
            @PathVariable String kbId,
            @RequestBody Map<String, String> payload) {
        String modelName = payload.get("model");
        // 同时返回 title 和 description
        Map<String, String> result = knowledgeManageService.generateDescriptionAndTitle(kbId, modelName);
        return result;
    }

    // ==================== 文档管理 API ====================

    @Operation(summary = "上传文档到知识库")
    @PostMapping(value = "/{kbId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeDocument uploadDocument(
            @PathVariable String kbId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String uploadedBy) {
        try {
            KnowledgeDocument result = documentManageService.uploadDocument(kbId, file, uploadedBy);
            // 审计日志
            auditLogService.logApiCall(
                    "SYSTEM", null, "KNOWLEDGE", "KNOWLEDGE",
                    "/knowledge/" + kbId + "/documents/upload", "POST", file.getOriginalFilename(), null, null,
                    "{\"kbId\":\"" + kbId + "\",\"filename\":\"" + file.getOriginalFilename() + "\"}", null, 200, null,
                    null, null, null, true, null, null, null);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload document: " + e.getMessage(), e);
        }
    }

    @Operation(summary = "获取知识库的文档列表")
    @GetMapping("/{kbId}/documents")
    public List<KnowledgeDocument> getDocuments(@PathVariable String kbId) {
        return documentManageService.getDocuments(kbId);
    }

    @Operation(summary = "获取文档详情")
    @GetMapping("/documents/{docId}")
    public KnowledgeDocument getDocument(@PathVariable String docId) {
        return documentManageService.getDocument(docId);
    }

    @Operation(summary = "获取文档解析内容")
    @GetMapping("/documents/{docId}/content")
    public Map<String, Object> getDocumentContent(@PathVariable String docId) {
        return documentManageService.getDocumentContent(docId);
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/documents/{docId}")
    public Map<String, String> deleteDocument(@PathVariable String docId) {
        documentManageService.deleteDocument(docId);
        // 审计日志
        auditLogService.logApiCall(
                "SYSTEM", null, "KNOWLEDGE", "KNOWLEDGE",
                "/knowledge/documents/" + docId, "DELETE", docId, null, null,
                null, null, 200, null,
                null, null, null, true, null, null, null);
        return Map.of("status", "deleted", "documentId", docId);
    }

    @Operation(summary = "触发文档向量化")
    @PostMapping("/documents/{docId}/vectorize")
    public KnowledgeDocument triggerVectorization(@PathVariable String docId) {
        KnowledgeDocument result = documentManageService.triggerVectorization(docId);
        // 审计日志
        auditLogService.logApiCall(
                "SYSTEM", null, "KNOWLEDGE", "KNOWLEDGE",
                "/knowledge/documents/" + docId + "/vectorize", "POST", docId, null, null,
                null, null, 200, null,
                null, null, null, true, null, null, null);
        return result;
    }

    @Operation(summary = "触发文档解析（多模态）")
    @PostMapping("/documents/{docId}/parse")
    public KnowledgeDocument triggerParsing(@PathVariable String docId) {
        // 解析时不自动触发向量化，用户可以手动触发向量化
        KnowledgeDocument result = documentManageService.triggerParsing(docId, false);
        // 审计日志
        auditLogService.logApiCall(
            "SYSTEM", null, "KNOWLEDGE", "KNOWLEDGE",
            "/knowledge/documents/" + docId + "/parse", "POST", docId, null, null,
            null, null, 200, null,
            null, null, null, true, null, null, null);
        return result;
    }

    @Operation(summary = "更新向量化状态")
    @PutMapping("/documents/{docId}/vector-status")
    public KnowledgeDocument updateVectorStatus(
            @PathVariable String docId,
            @RequestBody Map<String, Object> payload) {
        String status = (String) payload.get("status");
        String vectorIndexId = (String) payload.get("vectorIndexId");
        Integer chunkCount = (Integer) payload.get("chunkCount");

        return documentManageService.updateVectorizationStatus(docId, status, vectorIndexId, chunkCount);
    }

    @Operation(summary = "更新文档基本信息")
    @PutMapping("/documents/{docId}")
    public KnowledgeDocument updateDocument(
            @PathVariable String docId,
            @RequestBody Map<String, Object> payload) {
        return documentManageService.updateDocument(docId, payload);
    }

    @Operation(summary = "获取支持的文件类型")
    @GetMapping("/supported-file-types")
    public Map<String, Object> getSupportedFileTypes() {
        Map<String, Object> result = new HashMap<>();
        
        // Document types
        result.put("documents", Map.of(
            "extensions", List.of("pdf", "docx", "doc", "txt", "md", "markdown"),
            "description", "支持 PDF、Word、TXT、Markdown 等文档格式"
        ));
        
        // Image types
        result.put("images", Map.of(
            "extensions", List.of("png", "jpg", "jpeg", "gif", "bmp", "webp"),
            "description", "支持常见图片格式，将通过 OCR 识别文字"
        ));
        
        // Audio types
        result.put("audio", Map.of(
            "extensions", List.of("mp3", "wav", "m4a", "aac", "ogg", "flac"),
            "description", "支持音频格式，将通过语音识别转写为文字"
        ));
        
        // Video types
        result.put("video", Map.of(
            "extensions", List.of("mp4", "mov", "avi", "mkv", "webm", "flv"),
            "description", "支持视频格式，将提取音频后转写为文字"
        ));
        
        return result;
    }

    @Operation(summary = "更新文档分段")
    @PutMapping("/documents/chunks/{chunkId}")
    public com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk updateChunk(
            @PathVariable String chunkId,
            @RequestBody Map<String, String> payload) {
        return documentManageService.updateChunk(chunkId, payload.get("content"));
    }

    @Operation(summary = "删除文档分段")
    @DeleteMapping("/documents/chunks/{chunkId}")
    public Map<String, String> deleteChunk(@PathVariable String chunkId) {
        documentManageService.deleteChunk(chunkId);
        return Map.of("status", "success");
    }

    @Operation(summary = "获取文档修改历史")
    @GetMapping("/documents/{docId}/history")
    public List<Map<String, String>> getDocumentHistory(@PathVariable String docId) {
        return documentManageService.getDocumentHistory(docId);
    }

    @Operation(summary = "获取知识库统计信息")
    @GetMapping("/{kbId}/stats")
    public DocumentManageService.DocumentStats getKnowledgeBaseStats(@PathVariable String kbId) {
        return documentManageService.getKnowledgeBaseStats(kbId);
    }

    @Operation(summary = "获取待向量化的文档列表")
    @GetMapping("/documents/pending")
    public List<KnowledgeDocument> getPendingDocuments() {
        return documentManageService.getPendingDocuments();
    }

    // ==================== 向量检索调优 API ====================

    @Operation(summary = "获取文档的分片详情")
    @GetMapping("/documents/{docId}/chunks")
    public List<com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk> getDocumentChunks(
            @PathVariable String docId) {
        KnowledgeDocument doc = documentManageService.getDocument(docId);
        String collectionName = "kb_" + doc.getKnowledgeBaseId();
        return knowledgeManageService.getDocumentChunks(collectionName, docId);
    }

    @Operation(summary = "获取文档的分块统计信息 (Parent-Child)")
    @GetMapping("/documents/{docId}/chunks/stats")
    public Map<String, Object> getChunkStats(@PathVariable String docId) {
        return knowledgeManageService.getChunkStats(docId);
    }

    @Operation(summary = "获取文档的检索信息（包括检索内容示例）")
    @GetMapping("/documents/{docId}/retrieval-info")
    public Map<String, Object> getRetrievalInfo(@PathVariable String docId) {
        return knowledgeManageService.getRetrievalInfo(docId);
    }

    @Operation(summary = "获取指定分块的向量数据")
    @GetMapping("/chunks/{chunkId}/vector")
    public Map<String, Object> getChunkVector(@PathVariable String chunkId,
            @RequestParam(required = false) String kbId) {
        // 如果没有传入 kbId，需要从 chunkId 查询对应的知识库
        if (kbId == null || kbId.isEmpty()) {
            // 从数据库查询 chunk 对应的 docId 和知识库
            kbId = knowledgeManageService.getKbIdByChunkId(chunkId);
        }
        if (kbId == null) {
            return Map.of("success", false, "error", "无法确定知识库ID");
        }
        return milvusVectorService.getChunkVector(kbId, chunkId);
    }

    @Operation(summary = "检索效果测试 (支持多模态与模型测试)")
    @PostMapping("/retrieve/test")
    public Object testRetrieval(
            @RequestBody Map<String, Object> payload) {
        String query = (String) payload.get("query");
        String kbId = (String) payload.get("kbId");

        // 获取知识库的默认检索配置
        Map<String, Object> kbConfig = knowledgeManageService.getRetrievalConfig(kbId);

        // 如果请求没有指定参数，使用知识库的默认配置
        Integer topK = (Integer) payload.get("topK");
        if (topK == null && kbConfig != null && kbConfig.containsKey("topK")) {
            topK = asInteger(kbConfig.get("topK"));
        }
        if (topK == null) {
            topK = 3; // 系统默认
        }

        Double alpha = payload.get("alpha") != null ? ((Number) payload.get("alpha")).doubleValue() : null;
        if (alpha == null && kbConfig != null && kbConfig.containsKey("alpha")) {
            alpha = asDouble(kbConfig.get("alpha"));
        }

        Double threshold = payload.get("threshold") != null ? ((Number) payload.get("threshold")).doubleValue() : null;
        if (threshold == null && kbConfig != null && kbConfig.containsKey("similarityThreshold")) {
            threshold = asDouble(kbConfig.get("similarityThreshold"));
        }

        // Rerank 配置：优先使用请求参数，如果没有则使用知识库配置
        String rerankModel = (String) payload.get("rerankModel");
        Boolean enableRerank = payload.get("enableRerank") != null ? (Boolean) payload.get("enableRerank") : null;
        if (rerankModel == null && kbConfig != null && kbConfig.containsKey("rerankModel")) {
            rerankModel = (String) kbConfig.get("rerankModel");
        }
        if (enableRerank == null && kbConfig != null && kbConfig.containsKey("enableRerank")) {
            enableRerank = (Boolean) kbConfig.get("enableRerank");
        }
        // 如果知识库启用了 rerank 但没有指定模型，使用系统默认
        if ((rerankModel == null || rerankModel.isEmpty()) && enableRerank != null && enableRerank) {
            rerankModel = null; // 让 retrievalService 使用系统默认
        }

        String embeddingModel = (String) payload.get("embeddingModel");
        if ((embeddingModel == null || embeddingModel.isBlank()) && kbConfig != null
                && kbConfig.containsKey("embeddingModel")) {
            embeddingModel = String.valueOf(kbConfig.get("embeddingModel"));
        }
        String imageUrl = (String) payload.get("imageUrl");
        String vlmModel = (String) payload.get("vlmModel");

        String vectorConnection = milvusVectorService.getConnectionStatus();
        boolean vectorHealthy = "CONNECTED".equals(vectorConnection);
        Map<String, Object> kbVectorStats = buildKbVectorStats(kbId, vectorHealthy, vectorConnection);

        Object result;
        if (imageUrl != null && !imageUrl.isEmpty()) {
            result = retrievalService.multimodalSearch(kbId, imageUrl, vlmModel, embeddingModel, topK);
        } else {
            result = retrievalService.hybridSearch(kbId, query, topK, embeddingModel, alpha, threshold, rerankModel);
        }
        List<?> resultList = extractRetrievalResults(result);
        String retrievalMode = resolveRetrievalMode(resultList);
        String fallbackReason = resolveFallbackReason(retrievalMode, vectorHealthy, kbVectorStats);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", resultList);
        response.put("retrievalMode", retrievalMode);
        response.put("vectorHealthy", vectorHealthy);
        response.put("vectorConnection", vectorConnection);
        response.put("kbVectorStats", kbVectorStats);
        response.put("fallback", retrievalMode.contains("FALLBACK") || !vectorHealthy);
        response.put("fallbackReason", fallbackReason);
        response.put("topK", topK);
        response.put("threshold", threshold);
        response.put("alpha", alpha);
        response.put("rerankModel", rerankModel);

        // 审计日志 - RAG 检索
        auditLogService.logApiCall(
                "SYSTEM", null, "KNOWLEDGE", "KNOWLEDGE",
                "/knowledge/retrieve/test", "POST", kbId != null ? kbId : "all", null, null,
                "{\"query\":\"" + (query != null ? query.substring(0, Math.min(50, query.length())) : "")
                        + "\",\"topK\":" + topK + ",\"alpha\":" + alpha + ",\"threshold\":" + threshold
                        + ",\"rerankModel\":\"" + rerankModel + "\"}",
                null, 200, null,
                null, null, null, true, null, null, null);

        return response;
    }

    private List<?> extractRetrievalResults(Object result) {
        if (result instanceof List<?> list) {
            return list;
        }
        if (result instanceof Map<?, ?> map) {
            Object nested = map.get("results");
            if (nested instanceof List<?> list) {
                return list;
            }
        }
        return List.of();
    }

    private String resolveRetrievalMode(List<?> results) {
        if (results == null || results.isEmpty()) {
            return "EMPTY";
        }
        boolean hasVector = false;
        boolean hasKeyword = false;
        boolean hasTextIndex = false;
        boolean hasOther = false;
        for (Object item : results) {
            String matchType = null;
            if (item instanceof com.adlin.orin.modules.knowledge.component.VectorStoreProvider.SearchResult searchResult) {
                matchType = searchResult.getMatchType();
            } else if (item instanceof Map<?, ?> map && map.get("matchType") != null) {
                matchType = String.valueOf(map.get("matchType"));
            }
            String normalized = matchType != null ? matchType.toUpperCase() : "";
            if ("VECTOR".equals(normalized)) {
                hasVector = true;
            } else if ("KEYWORD".equals(normalized)) {
                hasKeyword = true;
            } else if ("TEXT_INDEX".equals(normalized)) {
                hasTextIndex = true;
            } else {
                hasOther = true;
            }
        }
        if (hasVector && (hasKeyword || hasTextIndex || hasOther)) {
            return "HYBRID";
        }
        if (hasVector) {
            return "VECTOR";
        }
        if (hasTextIndex) {
            return hasKeyword ? "TEXT_INDEX_KEYWORD_FALLBACK" : "TEXT_INDEX_FALLBACK";
        }
        if (hasKeyword) {
            return "KEYWORD_FALLBACK";
        }
        return "UNKNOWN";
    }

    private String resolveFallbackReason(String retrievalMode, boolean vectorHealthy, Map<String, Object> kbVectorStats) {
        if (!vectorHealthy) {
            return "Milvus 向量服务不可用，当前已降级为文本索引/关键词检索。";
        }
        if (retrievalMode != null && retrievalMode.contains("FALLBACK")) {
            if (hasMissingVectorData(kbVectorStats)) {
                return "当前知识库缺少可用向量数据，当前结果可能来自文本索引/关键词兜底。";
            }
            return "本次未命中向量召回，结果来自文本索引/关键词兜底。";
        }
        if ("EMPTY".equals(retrievalMode)) {
            if (hasMissingVectorData(kbVectorStats)) {
                return "当前知识库缺少可用向量数据，当前结果可能来自文本索引/关键词兜底。";
            }
            return "本次没有达到参数要求的召回结果。";
        }
        return "";
    }

    private boolean hasMissingVectorData(Map<String, Object> kbVectorStats) {
        if (kbVectorStats == null || kbVectorStats.isEmpty()) {
            return false;
        }
        return kbVectorStats.values().stream().filter(Objects::nonNull).anyMatch(value -> {
            if (value instanceof Map<?, ?> stat) {
                Object exists = stat.get("exists");
                Object vectorCount = stat.get("vectorCount");
                boolean noPartition = Boolean.FALSE.equals(exists);
                boolean noVectors = vectorCount instanceof Number number && number.longValue() == 0L;
                return noPartition || noVectors;
            }
            return false;
        });
    }

    private Map<String, Object> buildKbVectorStats(String kbId, boolean vectorHealthy, String vectorConnection) {
        Map<String, Object> stats = new LinkedHashMap<>();
        if (kbId == null || kbId.isBlank()) {
            return stats;
        }
        if ("all".equalsIgnoreCase(kbId)) {
            List<UnifiedKnowledgeDTO> bases = knowledgeManageService.getAllKnowledgeBases();
            for (UnifiedKnowledgeDTO kb : bases) {
                if (kb.getId() == null) {
                    continue;
                }
                if (kb.getType() != KnowledgeType.UNSTRUCTURED) {
                    continue;
                }
                stats.put(kb.getId(), vectorHealthy
                        ? getVectorStatsSafely(kb.getId(), kb.getName())
                        : getUnavailableVectorStats(kb.getName(), vectorConnection));
            }
            return stats;
        }
        stats.put(kbId, vectorHealthy ? getVectorStatsSafely(kbId, null)
                : getUnavailableVectorStats(null, vectorConnection));
        return stats;
    }

    private Map<String, Object> getUnavailableVectorStats(String kbName, String vectorConnection) {
        Map<String, Object> stat = new LinkedHashMap<>();
        if (kbName != null) {
            stat.put("name", kbName);
        }
        stat.put("exists", false);
        stat.put("vectorCount", -1L);
        stat.put("error", vectorConnection);
        return stat;
    }

    private Map<String, Object> getVectorStatsSafely(String kbId, String kbName) {
        Map<String, Object> stat = new LinkedHashMap<>();
        if (kbName != null) {
            stat.put("name", kbName);
        }
        try {
            stat.putAll(milvusVectorService.getVectorStats(kbId));
        } catch (Exception e) {
            stat.put("exists", false);
            stat.put("vectorCount", -1L);
            stat.put("error", e.getMessage());
        }
        return stat;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && !((String) value).isBlank()) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String && !((String) value).isBlank()) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @Operation(summary = "获取元知识 (Prompt模板)")
    @GetMapping("/agents/{agentId}/meta/prompts")
    public List<Map<String, Object>> getAgentPrompts(@PathVariable String agentId) {
        return metaKnowledgeService.getPromptTemplates(agentId);
    }

    @Operation(summary = "创建 Prompt 模板")
    @PostMapping("/agents/{agentId}/meta/prompts")
    public com.adlin.orin.modules.knowledge.entity.meta.PromptTemplate createPromptTemplate(
            @PathVariable String agentId,
            @RequestBody com.adlin.orin.modules.knowledge.entity.meta.PromptTemplate template) {
        template.setAgentId(agentId);
        return metaKnowledgeService.savePromptTemplate(template);
    }

    @Operation(summary = "删除 Prompt 模板")
    @DeleteMapping("/agents/{agentId}/meta/prompts/{id}")
    public Map<String, String> deletePrompt(@PathVariable String agentId, @PathVariable String id) {
        metaKnowledgeService.deletePromptTemplate(id);
        return Map.of("status", "success");
    }

    @Operation(summary = "获取记忆知识")
    @GetMapping("/agents/{agentId}/meta/memory")
    public List<Map<String, Object>> getAgentMemory(@PathVariable String agentId) {
        return metaKnowledgeService.getAgentMemory(agentId);
    }

    @Operation(summary = "删除单个记忆")
    @DeleteMapping("/agents/{agentId}/meta/memory/{id}")
    public Map<String, String> deleteMemory(@PathVariable String agentId, @PathVariable String id) {
        metaKnowledgeService.deleteMemoryEntry(id);
        return Map.of("status", "success");
    }

    @Operation(summary = "清除所有长期记忆")
    @DeleteMapping("/agents/{agentId}/meta/memory")
    public Map<String, String> clearMemory(@PathVariable String agentId) {
        metaKnowledgeService.clearLongTermMemory(agentId);
        return Map.of("status", "success");
    }

    @Operation(summary = "获取短期记忆会话列表")
    @GetMapping("/agents/{agentId}/meta/memory/sessions")
    public List<String> getShortTermSessions(@PathVariable String agentId) {
        return metaKnowledgeService.getShortTermSessions(agentId);
    }

    @Operation(summary = "获取短期记忆内容")
    @GetMapping("/agents/{agentId}/meta/memory/sessions/{sessionId}")
    public List<Map<String, Object>> getShortTermMemory(
            @PathVariable String agentId,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "50") int limit) {
        return metaKnowledgeService.getShortTermMemory(agentId, sessionId, limit);
    }

    @Operation(summary = "清除短期记忆")
    @DeleteMapping("/agents/{agentId}/meta/memory/sessions/{sessionId}")
    public Map<String, String> clearShortTermMemory(@PathVariable String agentId, @PathVariable String sessionId) {
        metaKnowledgeService.clearShortTermMemory(agentId, sessionId);
        return Map.of("status", "success");
    }

    // Field moved to top for consistency

    @Operation(summary = "从对话中提取记忆")
    @PostMapping("/agents/{agentId}/meta/extract_memory")
    public Map<String, String> extractMemory(
            @PathVariable String agentId,
            @RequestBody Map<String, String> payload) {
        String content = payload.get("content");
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("Content cannot be empty");
        }

        var metadata = agentManageService.getAgentMetadata(agentId);
        String modelName = metadata.getModelName();

        metaKnowledgeService.extractMemory(agentId, content, modelName);
        return Map.of("status", "success", "message", "Memory extracted successfully");
    }

    @Operation(summary = "解析文件为文本 (用于预览)")
    @PostMapping(value = "/documents/parse-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> parseText(@RequestParam("file") MultipartFile file) {
        try {
            String text = documentManageService.parseFileToText(file);
            return Map.of("text", text != null ? text : "");
        } catch (Exception e) {
            log.error("Failed to parse text from file", e);
            return Map.of("text", "Error: " + e.getMessage());
        }
    }

    @Operation(summary = "获取向量服务状态")
    @GetMapping("/vector/status")
    public Map<String, Object> getVectorServiceStatus() {
        Map<String, Object> result = new HashMap<>();

        String connection = milvusVectorService.getConnectionStatus();
        boolean healthy = "CONNECTED".equals(connection);
        result.put("healthy", healthy);
        result.put("connection", connection);

        if (healthy) {
            try {
                Map<String, Object> collectionInfo = milvusVectorService.getCollectionInfo();
                result.put("collection", collectionInfo);
            } catch (Exception e) {
                result.put("collectionError", e.getMessage());
            }
        }

        return result;
    }

    @Operation(summary = "诊断 Milvus 向量库状态")
    @GetMapping("/diagnose/milvus")
    public Map<String, Object> diagnoseMilvus() {
        Map<String, Object> result = new HashMap<>();

        // 添加向量服务健康状态
        result.put("vectorServiceHealthy", milvusVectorService.isHealthy());
        result.put("vectorServiceStatus", milvusVectorService.getConnectionStatus());

        // 先返回配置信息（不连接 Milvus）
        result.put("config", Map.of(
                "host", milvusHost,
                "port", milvusPort,
                "collection", "orin_knowledge_base"));

        // 添加 Embedding 服务诊断
        int embeddingDimension = 0;
        try {
            Map<String, Object> embeddingInfo = new HashMap<>();
            embeddingInfo.put("provider", embeddingService.getProviderName());
            embeddingInfo.put("model", embeddingService.getModelName());
            // 测试 embedding
            List<Float> testEmbedding = embeddingService.embed("测试文本");
            embeddingDimension = testEmbedding.size();
            embeddingInfo.put("status", "ok");
            embeddingInfo.put("dimension", embeddingDimension);
            result.put("embedding", embeddingInfo);
        } catch (Exception e) {
            log.error("Embedding diagnosis failed", e);
            result.put("embedding", Map.of(
                    "status", "error",
                    "error", e.getMessage()));
        }

        // 获取 Collection 维度并检查是否匹配
        try {
            Map<String, Object> collectionInfo = milvusVectorService.getCollectionInfo();
            result.put("collection", collectionInfo);

            // 检查维度是否匹配
            if (collectionInfo.get("exists") == Boolean.TRUE && collectionInfo.get("dimension") != null) {
                int collectionDimension = (int) collectionInfo.get("dimension");
                if (embeddingDimension > 0 && collectionDimension != embeddingDimension) {
                    String warning = String.format(
                            "维度不匹配! Embedding 模型维度: %d, Collection 维度: %d. 这会导致语义搜索返回空结果. 请重建 Collection 或更换 Embedding 模型.",
                            embeddingDimension, collectionDimension);
                    result.put("warning", warning);
                    result.put("dimensionMismatch", true);
                    log.error(warning);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get collection info for diagnosis: {}", e.getMessage());
        }

        try {
            // 获取所有文档
            List<KnowledgeDocument> allDocs = documentManageService.getAllDocuments();

            // 获取待向量化的文档
            List<KnowledgeDocument> pendingDocs = documentManageService.getPendingDocuments();
            result.put("pendingDocuments", pendingDocs.size());

            long successCount = allDocs.stream().filter(d -> "SUCCESS".equals(d.getVectorStatus())).count();
            long failedCount = allDocs.stream().filter(d -> "FAILED".equals(d.getVectorStatus())).count();

            result.put("documents", Map.of(
                    "total", allDocs.size(),
                    "success", successCount,
                    "failed", failedCount,
                    "pending", pendingDocs.size()));

            result.put("status", "ok");
        } catch (Exception e) {
            log.error("Diagnosis failed", e);
            result.put("status", "error");
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Operation(summary = "一键向量化所有待处理文档")
    @PostMapping("/documents/vectorize-all")
    public Map<String, Object> vectorizeAllPending() {
        int count = documentManageService.triggerPendingVectorization();
        return Map.of(
                "message", "已触发 " + count + " 个文档的向量化",
                "count", count);
    }

    @Operation(summary = "下载/查看原始文档文件")
    @GetMapping("/documents/{docId}/download")
    public org.springframework.http.ResponseEntity<?> downloadDocument(
            @PathVariable String docId) {
        KnowledgeDocument doc = documentManageService.getDocument(docId);
        String downloadUrl = documentManageService.getDocumentDownloadUrl(docId, java.time.Duration.ofMinutes(10));
        if (downloadUrl != null) {
            return org.springframework.http.ResponseEntity.status(302)
                    .location(java.net.URI.create(downloadUrl))
                    .build();
        }
        org.springframework.core.io.Resource resource = documentManageService.getDocumentResource(docId);

        String contentType = "application/octet-stream";
        if (doc.getFileType() != null) {
            String ext = doc.getFileType().toLowerCase();
            if (java.util.Set.of("jpg", "jpeg", "png", "gif", "webp").contains(ext))
                contentType = "image/" + ext;
            else if (ext.equals("pdf"))
                contentType = "application/pdf";
            else if (java.util.Set.of("mp3", "wav", "m4a").contains(ext))
                contentType = "audio/mpeg";
            else if (java.util.Set.of("mp4", "webm").contains(ext))
                contentType = "video/mp4";
        }

        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + doc.getFileName() + "\"")
                .body(resource);
    }

    // ==================== Knowledge Task 管理 API ====================

    @Operation(summary = "获取知识任务详情", description = "根据任务ID查询知识库任务详情和状态")
    @GetMapping("/task-detail/{taskId}")
    public ResponseEntity<Map<String, Object>> getKnowledgeTaskDetail(@PathVariable String taskId) {
        Optional<KnowledgeTask> taskOpt = knowledgeTaskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        KnowledgeTask task = taskOpt.get();
        Map<String, Object> result = new HashMap<>();
        result.put("id", task.getId());
        result.put("assetId", task.getAssetId());
        result.put("assetType", task.getAssetType());
        result.put("taskType", task.getTaskType());
        result.put("status", task.getStatus());
        result.put("statusDescription", task.getStatus().getDescription());
        result.put("retryCount", task.getRetryCount());
        result.put("maxRetries", task.getMaxRetries());
        result.put("errorMessage", task.getErrorMessage());
        result.put("startedAt", task.getStartedAt());
        result.put("completedAt", task.getCompletedAt());
        result.put("executionTimeMs", task.getExecutionTimeMs());
        result.put("createdAt", task.getCreatedAt());
        result.put("updatedAt", task.getUpdatedAt());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "查询知识库的任务列表", description = "查询指定资产的所有任务")
    @GetMapping("/tasks/asset/{assetId}")
    public ResponseEntity<List<Map<String, Object>>> getTasksByAsset(
            @PathVariable String assetId,
            @RequestParam(required = false) String assetType) {
        List<KnowledgeTask> tasks;
        if (assetType != null && !assetType.isEmpty()) {
            tasks = knowledgeTaskRepository.findByAssetIdAndAssetType(assetId, assetType);
        } else {
            tasks = knowledgeTaskRepository.findByAssetIdAndAssetType(assetId, null);
        }

        List<Map<String, Object>> result = tasks.stream().map(task -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", task.getId());
            item.put("assetId", task.getAssetId());
            item.put("assetType", task.getAssetType());
            item.put("taskType", task.getTaskType());
            item.put("status", task.getStatus());
            item.put("statusDescription", task.getStatus().getDescription());
            item.put("retryCount", task.getRetryCount());
            item.put("errorMessage", task.getErrorMessage());
            item.put("createdAt", task.getCreatedAt());
            item.put("updatedAt", task.getUpdatedAt());
            return item;
        }).toList();

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "查询待处理的知识任务", description = "查询当前待处理的任务列表")
    @GetMapping("/tasks/pending")
    public ResponseEntity<List<Map<String, Object>>> getPendingTasks() {
        List<KnowledgeTask> tasks = knowledgeTaskRepository.findByStatus(TaskStatus.PENDING);
        List<Map<String, Object>> result = tasks.stream().map(task -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", task.getId());
            item.put("assetId", task.getAssetId());
            item.put("assetType", task.getAssetType());
            item.put("taskType", task.getTaskType());
            item.put("status", task.getStatus());
            item.put("createdAt", task.getCreatedAt());
            return item;
        }).toList();
        return ResponseEntity.ok(result);
    }
}
