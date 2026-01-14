package com.adlin.orin.modules.knowledge.controller;

import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.service.DocumentManageService;
import com.adlin.orin.modules.knowledge.service.KnowledgeManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
@Tag(name = "Phase 4: Asset Management", description = "知识资产管理")
@CrossOrigin(origins = "*")
public class KnowledgeManageController {

    private final KnowledgeManageService knowledgeManageService;
    private final DocumentManageService documentManageService;

    @Operation(summary = "获取智能体绑定的知识库")
    @GetMapping("/agents/{agentId}")
    public List<KnowledgeBase> getBoundKnowledge(@PathVariable String agentId) {
        return knowledgeManageService.getBoundKnowledge(agentId);
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
}
