package com.adlin.orin.modules.knowledge.controller;

import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.service.DocumentManageService;
import com.adlin.orin.modules.knowledge.service.KnowledgeManageService;
import com.adlin.orin.modules.knowledge.dto.UnifiedKnowledgeDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import com.adlin.orin.modules.knowledge.service.meta.MetaKnowledgeService;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
@Tag(name = "Phase 4: Asset Management", description = "知识资产管理")
@CrossOrigin(origins = "*")
public class KnowledgeManageController {

    private final KnowledgeManageService knowledgeManageService;
    private final DocumentManageService documentManageService;
    private final MetaKnowledgeService metaKnowledgeService;
    private final com.adlin.orin.modules.knowledge.service.RetrievalService retrievalService;
    private final com.adlin.orin.modules.knowledge.service.StructuredService structuredService;
    private final com.adlin.orin.modules.knowledge.service.ProceduralService proceduralService;

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
        return knowledgeManageService.createKnowledgeBase(kb);
    }

    // ==================== 文档管理 API ====================

    @Operation(summary = "上传文档到知识库")
    @PostMapping(value = "/{kbId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeDocument uploadDocument(
            @PathVariable String kbId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String uploadedBy) {
        try {
            return documentManageService.uploadDocument(kbId, file, uploadedBy);
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

    @Operation(summary = "删除文档")
    @DeleteMapping("/documents/{docId}")
    public Map<String, String> deleteDocument(@PathVariable String docId) {
        documentManageService.deleteDocument(docId);
        return Map.of("status", "deleted", "documentId", docId);
    }

    @Operation(summary = "触发文档向量化")
    @PostMapping("/documents/{docId}/vectorize")
    public KnowledgeDocument triggerVectorization(@PathVariable String docId) {
        return documentManageService.triggerVectorization(docId);
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
    public List<com.adlin.orin.modules.knowledge.component.VectorStoreProvider.DocumentChunk> getDocumentChunks(
            @PathVariable String docId) {
        // Assume default collection or look up based on doc's KB
        // For simplicity, using a default name or derived from logic
        String collectionName = "default";
        return knowledgeManageService.getDocumentChunks(collectionName, docId);
    }

    @Operation(summary = "检索效果测试")
    @PostMapping("/retrieve/test")
    public List<com.adlin.orin.modules.knowledge.component.VectorStoreProvider.SearchResult> testRetrieval(
            @RequestBody Map<String, Object> payload) {
        String query = (String) payload.get("query");
        String kbId = (String) payload.get("kbId");
        Integer topK = (Integer) payload.getOrDefault("topK", 3);

        return retrievalService.hybridSearch(kbId, query, topK);
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

    @Operation(summary = "获取记忆知识")
    @GetMapping("/agents/{agentId}/meta/memory")
    public List<Map<String, Object>> getAgentMemory(@PathVariable String agentId) {
        return metaKnowledgeService.getAgentMemory(agentId);
    }

    private final com.adlin.orin.modules.agent.service.AgentManageService agentManageService;

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
}
