package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.adlin.orin.modules.knowledge.dto.UnifiedKnowledgeDTO;
import com.adlin.orin.modules.knowledge.service.meta.MetaKnowledgeService;

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
        private final DocumentManageService documentService;
        private final StructuredService structuredService;
        private final ProceduralService proceduralService;
        private final MetaKnowledgeService metaKnowledgeService;
        private final com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentChunkRepository chunkRepository;

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

                // 3. Delete from vector store
                try {
                        vectorStoreProvider.deleteKnowledgeBase(id);
                } catch (Throwable e) {
                        log.warn("Failed to delete knowledge base from vector store, but continuing with DB deletion: {}",
                                        e.getMessage());
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
}
