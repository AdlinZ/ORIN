package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
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

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeManageService {

        private final AgentAccessProfileRepository profileRepository;
        private final KnowledgeBaseRepository knowledgeBaseRepository;
        private final RestTemplate restTemplate;
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

        public List<com.adlin.orin.modules.knowledge.dto.UnifiedKnowledgeDTO> getAllKnowledgeBases() {
                List<KnowledgeBase> bases = knowledgeBaseRepository.findAll();
                List<com.adlin.orin.modules.knowledge.dto.UnifiedKnowledgeDTO> dtoList = new ArrayList<>();
                for (KnowledgeBase kb : bases) {
                        dtoList.add(mapToDTO(kb, null));
                }
                return dtoList;
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
         * 从 Dify 同步知识库信息到本地
         */
        public List<KnowledgeBase> syncFromDify(String agentId) {
                log.info("Syncing knowledge bases for agent: {}", agentId);
                Optional<AgentAccessProfile> profileOpt = profileRepository.findById(agentId);

                if (profileOpt.isEmpty()) {
                        throw new RuntimeException("Agent not found: " + agentId);
                }

                AgentAccessProfile profile = profileOpt.get();
                if (profile.getDatasetApiKey() == null || profile.getDatasetApiKey().isEmpty()) {
                        log.warn("No dataset API key found for agent: {}", agentId);
                        return Collections.emptyList();
                }

                String apiKey = profile.getDatasetApiKey();
                String endpoint = profile.getEndpointUrl();
                // Handle URL construction safely
                String baseUrl = endpoint.endsWith("/v1") ? endpoint.substring(0, endpoint.length() - 3) : endpoint;
                String url = baseUrl + "/v1/datasets?page=1&limit=20";
                if (!url.startsWith("http")) {
                        url = "http://" + url;
                }

                List<KnowledgeBase> syncedKbs = new ArrayList<>();

                try {
                        HttpHeaders headers = new HttpHeaders();
                        headers.setBearerAuth(apiKey);
                        HttpEntity<?> entity = new HttpEntity<>(headers);

                        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                                        url,
                                        HttpMethod.GET,
                                        entity,
                                        new ParameterizedTypeReference<Map<String, Object>>() {
                                        });

                        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody()
                                                .get("data");

                                if (data != null) {
                                        for (Map<String, Object> m : data) {
                                                String kbId = (String) m.get("id");

                                                // Check if exists to preserve local status if needed (or just
                                                // overwrite)
                                                // For now we overwrite metadata but might want to keep status if we
                                                // manage it locally
                                                KnowledgeBase existing = knowledgeBaseRepository.findById(kbId)
                                                                .orElse(null);

                                                KnowledgeBase kb = KnowledgeBase.builder()
                                                                .id(kbId)
                                                                .name((String) m.get("name"))
                                                                .description((String) m.get("description"))
                                                                .docCount((Integer) m.get("document_count"))
                                                                .totalSizeMb(0.0) // API might not give this directly
                                                                .status(existing != null ? existing.getStatus()
                                                                                : "ENABLED") // Default to enabled or
                                                                                             // keep existing
                                                                .sourceAgentId(agentId)
                                                                .syncTime(LocalDateTime.now())
                                                                .createdAt(existing != null ? existing.getCreatedAt()
                                                                                : LocalDateTime.now())
                                                                .build();

                                                knowledgeBaseRepository.save(kb);
                                                syncedKbs.add(kb);
                                        }
                                }
                        }
                } catch (Exception e) {
                        log.error("Failed to sync datasets from Dify: {}", e.getMessage());
                        // Fallback: If sync fails, return what we have locally or empty
                        return knowledgeBaseRepository.findBySourceAgentId(agentId);
                }

                return syncedKbs;
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

        public List<com.adlin.orin.modules.knowledge.component.VectorStoreProvider.SearchResult> testRetrieval(
                        String kbId, String query, Integer topK) {
                // Logic to determine collection name from kbId
                String collectionName = "kb_" + kbId; // Example convention
                // For mock/simple implementation we might just use "default" or pass kbId
                return vectorStoreProvider.search(collectionName, query, topK);
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
                if (updates.getDescription() != null)
                        kb.setDescription(updates.getDescription());
                if (updates.getStatus() != null)
                        kb.setStatus(updates.getStatus());
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
                        unifiedList.add(mapToDTO(kb, agentId));
                }

                return unifiedList;
        }

        private com.adlin.orin.modules.knowledge.dto.UnifiedKnowledgeDTO mapToDTO(KnowledgeBase kb, String agentId) {
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

                return com.adlin.orin.modules.knowledge.dto.UnifiedKnowledgeDTO.builder()
                                .id(kb.getId())
                                .name(kb.getName())
                                .description(kb.getDescription())
                                .type(kb.getType())
                                .status(kb.getStatus())
                                .stats(stats)
                                .build();
        }

        /**
         * 使用AI生成知识库描述
         */
        public String generateDescription(String knowledgeBaseId, String modelName) {
                try {
                        // 获取知识库文档
                        List<com.adlin.orin.modules.knowledge.entity.KnowledgeDocument> docs =
                                documentService.getDocuments(knowledgeBaseId);

                        if (docs == null || docs.isEmpty()) {
                                throw new RuntimeException("知识库中没有文档，无法生成描述");
                        }

                        // 读取前几个文档的内容摘要（限制长度以避免超出token限制）
                        StringBuilder contentBuilder = new StringBuilder();
                        int maxDocs = Math.min(docs.size(), 3);
                        int maxChars = 8000;

                        for (int i = 0; i < maxDocs && contentBuilder.length() < maxChars; i++) {
                                com.adlin.orin.modules.knowledge.entity.KnowledgeDocument doc = docs.get(i);
                                String content = documentService.readFileContent(doc);
                                if (content != null && !content.isEmpty()) {
                                        // 取前2000字符作为摘要
                                        String summary = content.length() > 2000 ?
                                                content.substring(0, 2000) : content;
                                        contentBuilder.append("文档").append(i + 1)
                                                .append(" (").append(doc.getFileName()).append("):\n")
                                                .append(summary).append("\n\n");
                                }
                        }

                        if (contentBuilder.length() == 0) {
                                throw new RuntimeException("无法读取文档内容");
                        }

                        String documentContent = contentBuilder.toString();

                        // 调用LLM生成描述
                        return callLLMForDescription(modelName, documentContent);

                } catch (Exception e) {
                        log.error("生成知识库描述失败: {}", e.getMessage(), e);
                        throw new RuntimeException("生成描述失败: " + e.getMessage());
                }
        }

        /**
         * 调用LLM生成描述
         */
        private String callLLMForDescription(String modelName, String documentContent) {
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

                        // 构建prompt
                        String systemPrompt = "你是一个专业的知识库描述生成助手。请根据用户提供的文档内容，生成一个简洁、准确的知识库描述（100字以内），描述这个知识库的主题、内容和用途。直接输出描述内容，不要有额外说明。";

                        String userPrompt = "请为以下文档内容生成知识库描述：\n\n" + documentContent;

                        // 调用API
                        Map<String, Object> requestBody = new HashMap<>();
                        requestBody.put("model", model);
                        requestBody.put("messages", Arrays.asList(
                                Map.of("role", "system", "content", systemPrompt),
                                Map.of("role", "user", "content", userPrompt)));
                        requestBody.put("temperature", 0.7);
                        requestBody.put("max_tokens", 200);

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
                        if (content == null || content.isEmpty()) {
                                throw new RuntimeException("LLM返回内容为空");
                        }

                        return content.trim();

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
