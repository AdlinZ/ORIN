package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.service.sync.DifyKnowledgeSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.adlin.orin.modules.knowledge.dto.UnifiedKnowledgeDTO;
import com.adlin.orin.modules.knowledge.service.meta.MetaKnowledgeService;
import com.adlin.orin.modules.model.service.ModelConfigService;
import com.adlin.orin.modules.model.service.SiliconFlowIntegrationService;
import com.adlin.orin.modules.model.entity.ModelConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeManageService {

        private final AgentAccessProfileRepository profileRepository;
        private final KnowledgeBaseRepository knowledgeBaseRepository;
        private final RestTemplate restTemplate;
        private final DifyKnowledgeSyncService difyKnowledgeSyncService;
        private final com.adlin.orin.modules.knowledge.component.VectorStoreProvider vectorStoreProvider;
        private final MilvusVectorService milvusVectorService;
        private final DocumentManageService documentService;
        private final StructuredService structuredService;
        private final ProceduralService proceduralService;
        private final MetaKnowledgeService metaKnowledgeService;
        private final com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentChunkRepository chunkRepository;
        private final com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository documentRepository;
        private final ModelConfigService modelConfigService;
        private final SiliconFlowIntegrationService siliconFlowIntegrationService;
        private final ObjectMapper objectMapper = new ObjectMapper();

        public List<com.adlin.orin.modules.knowledge.dto.UnifiedKnowledgeDTO> getAllKnowledgeBases() {
                List<KnowledgeBase> bases = knowledgeBaseRepository.findAll();
                List<com.adlin.orin.modules.knowledge.dto.UnifiedKnowledgeDTO> dtoList = new ArrayList<>();
                for (KnowledgeBase kb : bases) {
                        dtoList.add(mapToDTO(kb, null, false));
                }
                return dtoList;
        }

        /**
         * 获取知识库的检索配置
         * @param kbId 知识库ID
         * @return 检索配置 Map，包含 topK, alpha, similarityThreshold, enableRerank, rerankModel，如果没有配置返回 null
         */
        public Map<String, Object> getRetrievalConfig(String kbId) {
                if (kbId == null || kbId.isEmpty()) {
                        return null;
                }
                Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findById(kbId);
                if (kbOpt.isEmpty()) {
                        return null;
                }
                KnowledgeBase kb = kbOpt.get();
                Map<String, Object> config = new HashMap<>();
                // 只有当知识库明确设置了值才返回（不为 null）
                if (kb.getTopK() != null) {
                        config.put("topK", kb.getTopK());
                }
                if (kb.getAlpha() != null) {
                        config.put("alpha", kb.getAlpha());
                }
                if (kb.getSimilarityThreshold() != null) {
                        config.put("similarityThreshold", kb.getSimilarityThreshold());
                }
                // Rerank 配置
                if (kb.getEnableRerank() != null) {
                        config.put("enableRerank", kb.getEnableRerank());
                }
                if (kb.getRerankModel() != null && !kb.getRerankModel().isEmpty()) {
                        config.put("rerankModel", kb.getRerankModel());
                }
                // Embedding 模型优先从 configuration JSON 中读取
                JsonNode configNode = parseConfigurationNode(kb.getConfiguration());
                if (configNode != null) {
                        String embeddingModel = null;
                        if (configNode.hasNonNull("embeddingModel")) {
                                embeddingModel = configNode.get("embeddingModel").asText(null);
                        } else if (configNode.hasNonNull("embedding_model")) {
                                embeddingModel = configNode.get("embedding_model").asText(null);
                        }
                        if (embeddingModel != null && !embeddingModel.isBlank()) {
                                config.put("embeddingModel", embeddingModel.trim());
                        }
                }
                return config.isEmpty() ? null : config;
        }

        private JsonNode parseConfigurationNode(String rawConfiguration) {
                if (rawConfiguration == null || rawConfiguration.isBlank()) {
                        return null;
                }
                try {
                        return objectMapper.readTree(rawConfiguration);
                } catch (Exception e) {
                        log.warn("Failed to parse knowledge configuration JSON: {}", e.getMessage());
                        return null;
                }
        }

        /**
         * 获取指定 Agent 绑定的知识库 (优先从本地库取，如果为空则尝试同步)
         */
        public List<KnowledgeBase> getBoundKnowledge(String agentId) {
                List<KnowledgeBase> localKbs = knowledgeBaseRepository.findBySourceAgentId(agentId);
                if (localKbs.isEmpty()) {
                        // Auto-sync if nothing found (First time experience)
                        return syncFromDify(agentId);
                }
                return localKbs;
        }

        /**
         * 从 Dify 同步知识库信息到本地（系统级）
         */
        public List<KnowledgeBase> syncFromDify(String agentId) {
                log.info("Syncing knowledge bases from Dify (system-level)");
                var result = difyKnowledgeSyncService.syncKnowledgeBases(true);
                if (!result.isSuccess()) {
                        log.warn("Sync from Dify failed: {}", result.getMessage());
                }
                return knowledgeBaseRepository.findAll();
        }

        public List<com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk> getDocumentChunks(
                        String collectionName, String docId) {
                return chunkRepository.findByDocumentIdOrderByChunkIndex(docId);
        }

        /**
         * 获取文档的分块统计信息 (Parent-Child)
         */
        public Map<String, Object> getChunkStats(String docId) {
                List<KnowledgeDocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndex(docId);

                int totalChunks = chunks.size();
                int parentCount = 0;
                int childCount = 0;

                for (KnowledgeDocumentChunk chunk : chunks) {
                        if ("parent".equals(chunk.getChunkType())) {
                                parentCount++;
                        } else {
                                childCount++;
                        }
                }

                Map<String, Object> stats = new HashMap<>();
                stats.put("totalChunks", totalChunks);
                stats.put("parentCount", parentCount);
                stats.put("childCount", childCount);
                stats.put("chunkingMode", "PARENT_CHILD");
                return stats;
        }

        /**
         * 根据 chunkId 获取对应的知识库 ID
         */
        public String getKbIdByChunkId(String chunkId) {
                Optional<KnowledgeDocumentChunk> chunkOpt = chunkRepository.findById(chunkId);
                if (chunkOpt.isEmpty()) {
                        return null;
                }
                String docId = chunkOpt.get().getDocumentId();
                Optional<KnowledgeDocument> docOpt = documentRepository.findById(docId);
                if (docOpt.isEmpty()) {
                        return null;
                }
                return docOpt.get().getKnowledgeBaseId();
        }

        /**
         * 获取文档的索引文件内容（parsedTextPath 指向的原始解析文本）
         */
        public Map<String, Object> getRetrievalInfo(String docId) {
                KnowledgeDocument doc = documentRepository.findById(docId)
                        .orElseThrow(() -> new RuntimeException("Document not found: " + docId));

                List<KnowledgeDocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndex(docId);

                Map<String, Object> info = new HashMap<>();

                // 基本信息
                info.put("documentId", doc.getId());
                info.put("fileName", doc.getFileName());
                info.put("mediaType", doc.getMediaType());
                info.put("vectorStatus", doc.getVectorStatus());
                info.put("parseStatus", doc.getParseStatus());

                // 分块统计
                int totalChunks = chunks.size();
                int parentCount = 0;
                int childCount = 0;
                for (KnowledgeDocumentChunk chunk : chunks) {
                        if ("parent".equals(chunk.getChunkType())) {
                                parentCount++;
                        } else {
                                childCount++;
                        }
                }
                info.put("totalChunks", totalChunks);
                info.put("parentCount", parentCount);
                info.put("childCount", childCount);

                // 读取索引文件内容（parsedTextPath 指向的原始解析文本）
                String parsedTextPath = doc.getParsedTextPath();
                String indexFileContent = null;
                if (parsedTextPath != null && !parsedTextPath.isEmpty()) {
                        try {
                                java.nio.file.Path path = java.nio.file.Paths.get(parsedTextPath);
                                if (java.nio.file.Files.exists(path)) {
                                        indexFileContent = java.nio.file.Files.readString(path);
                                }
                        } catch (Exception e) {
                                log.warn("Failed to read index file: {}", e.getMessage());
                        }
                }

                // 如果没有索引文件内容，回退到 contentPreview
                if (indexFileContent == null || indexFileContent.isEmpty()) {
                        indexFileContent = doc.getContentPreview();
                }

                // 索引内容示例：将索引文件内容分段显示
                List<Map<String, Object>> indexSamples = new ArrayList<>();
                if (indexFileContent != null && !indexFileContent.isEmpty()) {
                        info.put("indexCharCount", indexFileContent.length());
                        // 返回完整内容
                        info.put("fullContent", indexFileContent);

                        // 将内容按段落分割，每2000字符一段作为预览（保留用于兼容性）
                        int segmentSize = 2000;
                        int totalSegments = (int) Math.ceil((double) indexFileContent.length() / segmentSize);
                        int sampleCount = Math.min(5, totalSegments);

                        for (int i = 0; i < sampleCount; i++) {
                                int start = i * segmentSize;
                                int end = Math.min(start + segmentSize, indexFileContent.length());
                                String segmentContent = indexFileContent.substring(start, end);

                                Map<String, Object> sample = new HashMap<>();
                                sample.put("segmentIndex", i);
                                sample.put("content", segmentContent);
                                sample.put("charCount", segmentContent.length());
                                sample.put("isPreview", true);
                                indexSamples.add(sample);
                        }
                }
                info.put("indexSamples", indexSamples);

                // 向量化状态详情
                info.put("chunkMethod", doc.getChunkMethod());
                info.put("chunkSize", doc.getChunkSize());
                info.put("chunkOverlap", doc.getChunkOverlap());

                return info;
        }

        public List<com.adlin.orin.modules.knowledge.component.VectorStoreProvider.SearchResult> testRetrieval(
                        String kbId, String query, Integer topK) {
                // Logic to determine collection name from kbId
                String collectionName = "kb_" + kbId; // Example convention
                // For mock/simple implementation we might just use "default" or pass kbId
                return vectorStoreProvider.search(collectionName, query, topK);
        }

        /**
         * 获取知识库详情
         */
        public KnowledgeBase getKnowledgeBaseById(String id) {
                return knowledgeBaseRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Knowledge Base not found: " + id));
        }

        /**
         * 更新知识库状态
         */
        public KnowledgeBase updateStatus(String kbId, boolean enabled) {
                KnowledgeBase kb = knowledgeBaseRepository.findById(kbId)
                                .orElseThrow(() -> new RuntimeException("Knowledge Base not found: " + kbId));

                kb.setStatus(enabled ? "ENABLED" : "DISABLED");
                // In a real scenario, we might also want to call Dify API to disable it there
                // if supported

                return knowledgeBaseRepository.save(kb);
        }

        /**
         * 创建知识库
         */
        public KnowledgeBase createKnowledgeBase(KnowledgeBase kb) {
                if (kb.getId() == null) {
                        kb.setId(UUID.randomUUID().toString());
                }
                if (kb.getCreatedAt() == null) {
                        kb.setCreatedAt(LocalDateTime.now());
                }
                kb.setSyncTime(LocalDateTime.now());
                if (kb.getStatus() == null) {
                        kb.setStatus("ENABLED");
                }
                return knowledgeBaseRepository.save(kb);
        }

        /**
         * 更新知识库
         */
        public KnowledgeBase updateKnowledgeBase(String id, KnowledgeBase updates) {
                KnowledgeBase kb = knowledgeBaseRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Knowledge Base not found: " + id));
                if (updates.getName() != null)
                        kb.setName(updates.getName());
                if (updates.getType() != null)
                        kb.setType(updates.getType());
                if (updates.getDescription() != null)
                        kb.setDescription(updates.getDescription());
                if (updates.getDescriptionModel() != null)
                        kb.setDescriptionModel(updates.getDescriptionModel());
                if (updates.getStatus() != null)
                        kb.setStatus(updates.getStatus());

                // 解析配置
                if (updates.getParsingEnabled() != null)
                        kb.setParsingEnabled(updates.getParsingEnabled());
                if (updates.getOcrProvider() != null)
                        kb.setOcrProvider(updates.getOcrProvider());
                if (updates.getAsrProvider() != null)
                        kb.setAsrProvider(updates.getAsrProvider());
                if (updates.getOcrModel() != null)
                        kb.setOcrModel(updates.getOcrModel());
                if (updates.getAsrModel() != null)
                        kb.setAsrModel(updates.getAsrModel());
                if (updates.getRichTextEnabled() != null)
                        kb.setRichTextEnabled(updates.getRichTextEnabled());

                // 检索配置
                if (updates.getChunkSize() != null)
                        kb.setChunkSize(updates.getChunkSize());
                if (updates.getChunkOverlap() != null)
                        kb.setChunkOverlap(updates.getChunkOverlap());
                if (updates.getTopK() != null)
                        kb.setTopK(updates.getTopK());
                if (updates.getSimilarityThreshold() != null)
                        kb.setSimilarityThreshold(updates.getSimilarityThreshold());
                if (updates.getAlpha() != null)
                        kb.setAlpha(updates.getAlpha());
                if (updates.getEnableRerank() != null)
                        kb.setEnableRerank(updates.getEnableRerank());
                if (updates.getRerankModel() != null)
                        kb.setRerankModel(updates.getRerankModel());
                if (updates.getConfiguration() != null)
                        kb.setConfiguration(updates.getConfiguration());

                return knowledgeBaseRepository.save(kb);
        }

        /**
         * 删除知识库
         */
        @org.springframework.transaction.annotation.Transactional
        public void deleteKnowledgeBase(String id) {
                KnowledgeBase kb = knowledgeBaseRepository.findById(id)
                                .orElseThrow(() -> new com.adlin.orin.common.exception.ResourceNotFoundException(
                                                "KnowledgeBase", id));

                // 1. Delete all documents associated with this KB (Bulk Optimized)
                documentService.deleteByKnowledgeBaseId(id);

                // 2. Specialized deletion based on type
                if (kb.getSourceAgentId() != null) {
                        String agentId = kb.getSourceAgentId();
                        switch (kb.getType()) {
                                case PROCEDURAL:
                                        proceduralService.deleteAgentSkills(agentId);
                                        break;
                                case META_MEMORY:
                                        metaKnowledgeService.clearLongTermMemory(agentId);
                                        metaKnowledgeService.clearPromptTemplates(agentId);
                                        metaKnowledgeService.clearAllShortTermMemory(agentId);
                                        break;
                                case STRUCTURED:
                                        // Structured data is mostly in-memory or temp tables in current prototype
                                        break;
                                default:
                                        break;
                        }
                }

                // 3. Delete from vector store (async to avoid blocking)
                // 同时清理 Milvus 分区和 VectorStoreProvider
                final String kbId = id;
                try {
                        // Run vector store deletion in background to avoid blocking
                        new Thread(() -> {
                                try {
                                        // 删除 Milvus 分区 (当前实际使用的向量库)
                                        milvusVectorService.deleteKnowledgeBase(kbId);
                                        log.info("Async deleted Milvus partition for KB: {}", kbId);
                                } catch (Exception e) {
                                        log.warn("Failed to async delete Milvus partition for KB {}: {}", kbId, e.getMessage());
                                }
                                try {
                                        // 删除 VectorStoreProvider (备用向量库)
                                        vectorStoreProvider.deleteKnowledgeBase(kbId);
                                        log.info("Async deleted vector store provider for KB: {}", kbId);
                                } catch (Exception e) {
                                        log.warn("Failed to async delete vector store provider for KB {}: {}", kbId, e.getMessage());
                                }
                        }).start();
                } catch (Throwable e) {
                        log.warn("Failed to schedule async vector store deletion: {}", e.getMessage());
                }

                // 4. Delete the KB itself
                knowledgeBaseRepository.deleteById(id);
                log.info("Deleted knowledge base: {} (Type: {})", id, kb.getType());
        }

        /**
         * 获取统一知识列表（业务分类 + 实时指标）
         */
        public List<UnifiedKnowledgeDTO> getUnifiedKnowledge(String agentId) {
                List<KnowledgeBase> bases = getBoundKnowledge(agentId);
                List<UnifiedKnowledgeDTO> unifiedList = new ArrayList<>();

                for (KnowledgeBase kb : bases) {
                        unifiedList.add(mapToDTO(kb, agentId, true));
                }

                return unifiedList;
        }

        private com.adlin.orin.modules.knowledge.dto.UnifiedKnowledgeDTO mapToDTO(
                        KnowledgeBase kb,
                        String agentId,
                        boolean includeVectorStats) {
                Map<String, Object> stats = new HashMap<>();
                String effectiveAgentId = agentId != null ? agentId : kb.getSourceAgentId();

                switch (kb.getType()) {
                        case UNSTRUCTURED:
                                var docStats = documentService.getKnowledgeBaseStats(kb.getId());
                                stats.put("documentCount", docStats.documentCount());
                                stats.put("chunkCount", docStats.totalCharCount());
                                break;
                        case STRUCTURED:
                                if (effectiveAgentId != null) {
                                        var schemas = structuredService.getDatabaseSchema(effectiveAgentId);
                                        stats.put("tableCount", schemas.size());
                                }
                                break;
                        case PROCEDURAL:
                                if (effectiveAgentId != null) {
                                        var skills = proceduralService.getAgentSkills(effectiveAgentId);
                                        stats.put("skillCount", skills.size());
                                }
                                break;
                        case META_MEMORY:
                                if (effectiveAgentId != null) {
                                        var memories = metaKnowledgeService.getAgentMemory(effectiveAgentId);
                                        stats.put("memoryEntryCount", memories.size());
                                }
                                break;
                }

                // 获取 Milvus 向量状态 (仅对 UNSTRUCTURED 类型)
                Map<String, Object> vectorStats = null;
                if (includeVectorStats
                                && kb.getType() == com.adlin.orin.modules.knowledge.entity.KnowledgeType.UNSTRUCTURED) {
                        try {
                                vectorStats = milvusVectorService.getVectorStats(kb.getId());
                        } catch (Exception e) {
                                log.warn("Failed to get vector stats for KB {}: {}", kb.getId(), e.getMessage());
                                vectorStats = Map.of("exists", false, "error", e.getMessage());
                        }
                }

                return com.adlin.orin.modules.knowledge.dto.UnifiedKnowledgeDTO.builder()
                                .id(kb.getId())
                                .name(kb.getName())
                                .description(kb.getDescription())
                                .type(kb.getType())
                                .status(kb.getStatus())
                                .vectorStats(vectorStats)
                                .stats(stats)
                                .build();
        }

        /**
         * 使用AI生成知识库描述和标题
         * @return 包含 title 和 description 的 Map
         */
        public Map<String, String> generateDescriptionAndTitle(String knowledgeBaseId, String modelName) {
                try {
                        // 获取知识库文档
                        List<com.adlin.orin.modules.knowledge.entity.KnowledgeDocument> docs =
                                documentService.getDocuments(knowledgeBaseId);

                        if (docs == null || docs.isEmpty()) {
                                throw new RuntimeException("知识库中没有文档，无法生成描述");
                        }

                        log.info("Generating description for KB: {}, found {} documents", knowledgeBaseId, docs.size());
                        for (com.adlin.orin.modules.knowledge.entity.KnowledgeDocument doc : docs) {
                                log.info("Doc: {}, parsedTextPath: {}, parseStatus: {}",
                                        doc.getFileName(), doc.getParsedTextPath(), doc.getParseStatus());
                        }

                        StringBuilder contentBuilder = new StringBuilder();
                        int maxChars = 10000; // 限制总长度
                        int docsWithContent = 0;

                        // 步骤2：优先从 parsedTextPath（解析后的文本文件）读取
                        for (com.adlin.orin.modules.knowledge.entity.KnowledgeDocument doc : docs) {
                                if (contentBuilder.length() >= maxChars) break;

                                String content = null;
                                // 优先读取 parsedTextPath（步骤2解析后生成的文件）
                                String parsedTextPath = doc.getParsedTextPath();
                                log.info("Checking doc: {}, parsedTextPath: {}", doc.getFileName(), parsedTextPath);
                                if (parsedTextPath != null && !parsedTextPath.isEmpty()) {
                                        try {
                                                java.nio.file.Path path = java.nio.file.Paths.get(parsedTextPath);
                                                if (java.nio.file.Files.exists(path)) {
                                                        content = java.nio.file.Files.readString(path);
                                                        log.info("Read parsed text for {}: {} chars", doc.getFileName(), content.length());
                                                } else {
                                                        log.warn("Parsed file not found: {}", parsedTextPath);
                                                }
                                        } catch (Exception e) {
                                                log.warn("Failed to read parsed text for doc {}: {}", doc.getId(), e.getMessage());
                                        }
                                }

                                if (content != null && !content.isEmpty()) {
                                        docsWithContent++;
                                        // 读取完整内容，只在达到总长度限制时截断
                                        contentBuilder.append("========== 文档 ").append(docsWithContent).append(": ").append(doc.getFileName()).append(" ==========\n")
                                                .append(content).append("\n\n");
                                }
                        }

                        log.info("Read content from {} documents, total chars: {}", docsWithContent, contentBuilder.length());

                        // 如果没有 parsedTextPath，尝试从 chunks 读取（向量化后的分块数据）
                        if (contentBuilder.length() == 0) {
                                for (com.adlin.orin.modules.knowledge.entity.KnowledgeDocument doc : docs) {
                                        if (contentBuilder.length() >= maxChars) break;

                                        // 获取该文档的所有 chunks（优先获取 parent chunks，内容更完整）
                                        List<KnowledgeDocumentChunk> parentChunks = chunkRepository
                                                .findByDocumentIdAndChunkType(doc.getId(), "parent");
                                        List<KnowledgeDocumentChunk> childChunks = chunkRepository
                                                .findByDocumentIdAndChunkType(doc.getId(), "child");

                                        // 使用 parent chunks（更完整），如果没有则使用 child chunks
                                        List<KnowledgeDocumentChunk> chunksToUse = parentChunks.isEmpty() ? childChunks : parentChunks;

                                        if (!chunksToUse.isEmpty()) {
                                                contentBuilder.append("文档 (").append(doc.getFileName()).append("): ");
                                                for (KnowledgeDocumentChunk chunk : chunksToUse) {
                                                        if (contentBuilder.length() >= maxChars) break;
                                                        String chunkContent = chunk.getContent();
                                                        if (chunkContent != null && !chunkContent.isEmpty()) {
                                                                // 取前500字符作为摘要
                                                                String summary = chunkContent.length() > 500 ?
                                                                        chunkContent.substring(0, 500) : chunkContent;
                                                                contentBuilder.append(summary).append(" ");
                                                        }
                                                }
                                                contentBuilder.append("\n");
                                        }
                                }
                        }

                        // 最后回退到读取原始文件
                        if (contentBuilder.length() == 0) {
                                for (com.adlin.orin.modules.knowledge.entity.KnowledgeDocument doc : docs) {
                                        if (contentBuilder.length() >= maxChars) break;

                                        try {
                                                String content = documentService.readFileContent(doc);
                                                if (content != null && !content.isEmpty()) {
                                                        // 取前1000字符作为摘要
                                                        String summary = content.length() > 1000 ?
                                                                content.substring(0, 1000) : content;
                                                        contentBuilder.append("文档 (").append(doc.getFileName()).append("): ")
                                                                .append(summary).append("\n");
                                                }
                                        } catch (Exception e) {
                                                log.warn("Failed to read original file for doc {}: {}", doc.getId(), e.getMessage());
                                        }
                                }
                        }

                        if (contentBuilder.length() == 0) {
                                throw new RuntimeException("无法读取文档内容");
                        }

                        String documentContent = contentBuilder.toString();

                        // 调用LLM生成标题和描述
                        return callLLMForTitleAndDescription(modelName, documentContent, docsWithContent);

                } catch (Exception e) {
                        log.error("生成知识库描述失败: {}", e.getMessage(), e);
                        throw new RuntimeException("生成描述失败: " + e.getMessage());
                }
        }

        /**
         * 使用AI生成知识库描述（保留兼容性）
         */
        public String generateDescription(String knowledgeBaseId, String modelName) {
                Map<String, String> result = generateDescriptionAndTitle(knowledgeBaseId, modelName);
                String desc = result.get("description");
                // 同时更新 title
                if (result.get("title") != null && !result.get("title").isEmpty()) {
                        try {
                                KnowledgeBase kb = knowledgeBaseRepository.findById(knowledgeBaseId).orElse(null);
                                if (kb != null) {
                                        kb.setName(result.get("title"));
                                        knowledgeBaseRepository.save(kb);
                                }
                        } catch (Exception e) {
                                log.warn("Failed to update KB title: {}", e.getMessage());
                        }
                }
                return desc;
        }

        /**
         * 调用LLM生成知识库标题和描述
         * @return 包含 title 和 description 的 Map
         */
        private Map<String, String> callLLMForTitleAndDescription(String modelName, String documentContent, int docCount) {
                try {
                        ModelConfig config = modelConfigService.getConfig();
                        String endpoint = config.getSiliconFlowEndpoint();
                        String apiKey = config.getSiliconFlowApiKey();
                        String model = modelName != null && !modelName.isEmpty() ?
                                modelName : config.getSystemModel();

                        if (endpoint == null || endpoint.isEmpty()) {
                                endpoint = "https://api.siliconflow.cn/v1";
                        }
                        if (apiKey == null || apiKey.isEmpty()) {
                                throw new RuntimeException("未配置 SiliconFlow API Key");
                        }
                        if (model == null || model.isEmpty()) {
                                model = "Qwen/Qwen2-7B-Instruct";
                        }

                        // 构建prompt - 要求同时生成标题和描述，必须概括所有文档
                        String systemPrompt = "你是一个专业的知识库描述生成助手。" +
                                "你必须先分析用户提供的所有文档内容，列出每个文档的主要内容要点，然后生成一个统一的标题和描述。" +
                                "你必须概括所有文档的内容，不能遗漏任何一个文档。" +
                                "标题要求简洁有力，最多15个字。" +
                                "描述要求100字以内，描述这个知识库包含的所有文档的主题、内容和用途。" +
                                "请严格按照以下JSON格式输出，不要有额外说明：\n" +
                                "{\"title\": \"标题内容\", \"description\": \"描述内容\"}";

                        String userPrompt = "知识库包含 " + docCount + " 个文档，请先列出每个文档的内容摘要（按\"文档X: 内容要点\"的格式），然后根据所有文档生成统一的标题和描述。\n\n" + documentContent;

                        log.info("调用LLM - model: {}, prompt长度: {}", model, userPrompt.length());

                        // 调用API
                        Map<String, Object> requestBody = new HashMap<>();
                        requestBody.put("model", model);
                        requestBody.put("messages", Arrays.asList(
                                Map.of("role", "system", "content", systemPrompt),
                                Map.of("role", "user", "content", userPrompt)));
                        requestBody.put("temperature", 0.7);
                        requestBody.put("max_tokens", 300);

                        HttpHeaders headers = new HttpHeaders();
                        headers.setBearerAuth(apiKey.trim());
                        headers.setContentType(MediaType.APPLICATION_JSON);

                        String url = endpoint.trim();
                        if (!url.endsWith("/")) {
                                url += "/";
                        }
                        url += "chat/completions";

                        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                                url,
                                HttpMethod.POST,
                                entity,
                                new ParameterizedTypeReference<Map<String, Object>>() {
                                });

                        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                                throw new RuntimeException("LLM API调用失败");
                        }

                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                        if (choices == null || choices.isEmpty()) {
                                throw new RuntimeException("LLM未返回有效响应");
                        }

                        @SuppressWarnings("unchecked")
                        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                        if (message == null) {
                                throw new RuntimeException("LLM响应格式错误");
                        }

                        String content = (String) message.get("content");
                        log.info("LLM原始响应: {}", content);

                        if (content == null || content.isEmpty()) {
                                throw new RuntimeException("LLM返回内容为空");
                        }

                        // 解析JSON响应
                        String trimmedContent = content.trim();
                        log.info("LLM响应(trimmed): {}", trimmedContent);

                        // 尝试提取JSON
                        int jsonStart = trimmedContent.indexOf("{");
                        int jsonEnd = trimmedContent.lastIndexOf("}");
                        if (jsonStart >= 0 && jsonEnd > jsonStart) {
                                String jsonStr = trimmedContent.substring(jsonStart, jsonEnd + 1);
                                log.info("提取的JSON: {}", jsonStr);
                                JsonNode jsonNode = objectMapper.readTree(jsonStr);
                                String title = jsonNode.has("title") ? jsonNode.get("title").asText() : "";
                                String description = jsonNode.has("description") ? jsonNode.get("description").asText() : "";
                                log.info("解析结果: title='{}', description='{}'", title, description);
                                return Map.of("title", title, "description", description);
                        }

                        // 如果无法解析JSON，返回整个内容作为描述
                        log.warn("无法解析JSON，使用原始内容");
                        return Map.of("title", "知识库", "description", trimmedContent);

                } catch (Exception e) {
                        log.error("调用LLM失败: {}", e.getMessage(), e);
                        throw new RuntimeException("调用LLM失败: " + e.getMessage());
                }
        }

        /**
         * 获取知识库的向量详情
         */
        public Map<String, Object> getKnowledgeBaseVectors(String kbId, int page, int size) {
                Map<String, Object> result = new HashMap<>();

                // 获取知识库信息
                Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findById(kbId);
                if (kbOpt.isEmpty()) {
                        result.put("error", "知识库不存在");
                        return result;
                }
                KnowledgeBase kb = kbOpt.get();
                result.put("knowledgeBase", Map.of("id", kb.getId(), "name", kb.getName()));

                // 查询该知识库的分片
                List<com.adlin.orin.modules.knowledge.entity.KnowledgeDocument> docs =
                        documentRepository.findByKnowledgeBaseIdOrderByUploadTimeDesc(kbId);

                List<Map<String, Object>> chunks = new ArrayList<>();
                int totalChunks = 0;

                for (com.adlin.orin.modules.knowledge.entity.KnowledgeDocument doc : docs) {
                        List<com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk> docChunks =
                                chunkRepository.findByDocumentIdOrderByChunkIndex(doc.getId());

                        for (com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk chunk : docChunks) {
                                if (chunk.getChunkType() == null || "child".equals(chunk.getChunkType())) {
                                        totalChunks++;
                                        Map<String, Object> chunkInfo = new HashMap<>();
                                        chunkInfo.put("id", chunk.getId());
                                        chunkInfo.put("docId", chunk.getDocumentId());
                                        chunkInfo.put("fileName", doc.getFileName());
                                        chunkInfo.put("chunkIndex", chunk.getChunkIndex());
                                        chunkInfo.put("content", chunk.getContent());
                                        chunkInfo.put("charCount", chunk.getCharCount());
                                        chunkInfo.put("vectorId", chunk.getVectorId());
                                        chunkInfo.put("title", chunk.getTitle());
                                        chunks.add(chunkInfo);
                                }
                        }
                }

                // 分页
                int start = page * size;
                int end = Math.min(start + size, chunks.size());
                List<Map<String, Object>> pagedChunks = chunks.subList(
                        Math.min(start, chunks.size()),
                        Math.min(end, chunks.size()));

                result.put("totalChunks", totalChunks);
                result.put("totalDocs", docs.size());
                result.put("chunks", pagedChunks);
                result.put("page", page);
                result.put("size", size);

                return result;
        }
}
