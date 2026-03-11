package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.component.VectorStoreProvider;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 检索服务
 * 负责 Hybrid Search (向量检索 + 关键词检索)
 * 支持 Parent-Child Hierarchical Retrieval
 */
@Service
@Slf4j
public class RetrievalService {

    private final MilvusVectorService vectorService;
    private final KnowledgeDocumentChunkRepository chunkRepository;
    private final com.adlin.orin.modules.multimodal.service.VisualAnalysisService visualAnalysisService;

    private static final double WEIGHT_KEYWORD = 0.3;
    private static final double WEIGHT_VECTOR = 0.7;

    // 降级模式标志：当向量服务不可用时为 true
    private volatile boolean vectorServiceUnavailable = false;

    public RetrievalService(
            MilvusVectorService vectorService,
            KnowledgeDocumentChunkRepository chunkRepository,
            com.adlin.orin.modules.multimodal.service.VisualAnalysisService visualAnalysisService) {
        this.vectorService = vectorService;
        this.chunkRepository = chunkRepository;
        this.visualAnalysisService = visualAnalysisService;
    }

    /**
     * 检查向量服务是否可用
     */
    public boolean isVectorServiceAvailable() {
        if (vectorServiceUnavailable) {
            // 尝试恢复：检查向量服务是否恢复
            try {
                if (vectorService.isHealthy()) {
                    vectorServiceUnavailable = false;
                    log.info("Vector service recovered, restoring normal operation");
                }
            } catch (Exception e) {
                log.debug("Vector service still unavailable: {}", e.getMessage());
            }
        }
        return !vectorServiceUnavailable;
    }

    /**
     * 获取向量服务状态信息
     */
    public Map<String, Object> getVectorServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("unavailable", vectorServiceUnavailable);
        try {
            status.put("healthy", vectorService.isHealthy());
            status.put("connection", vectorService.getConnectionStatus());
        } catch (Exception e) {
            status.put("healthy", false);
            status.put("error", e.getMessage());
        }
        return status;
    }

    /**
     * 混合检索
     *
     * @param kbId           知识库 ID
     * @param query          查询语句
     * @param topK           返回结果数量
     * @param embeddingModel 嵌入模型名称 (可选)
     * @return 检索结果列表 (Parent chunks for LLM context)
     */
    public List<VectorStoreProvider.SearchResult> hybridSearch(String kbId, String query, int topK) {
        return hybridSearch(kbId, query, topK, null);
    }

    /**
     * Hybrid Search with Parent-Child Retrieval
     *
     * Pipeline:
     * 1. Search vector DB using child chunks (with fallback to keyword-only if fails)
     * 2. Retrieve top-k child matches
     * 3. Use parent_id to fetch corresponding parent chunks from DB
     * 4. Deduplicate parent chunks
     * 5. Return parent chunks as context
     */
    public List<VectorStoreProvider.SearchResult> hybridSearch(String kbId, String query, int topK,
            String embeddingModel) {
        // 1. 首先执行关键词检索（始终可用）
        List<KnowledgeDocumentChunk> keywordChunks = keywordSearch(kbId, query, topK * 3);
        log.info("HybridSearch: kbId={}, query={}, keywordResults.size={}", kbId, query, keywordChunks.size());

        // 2. 尝试向量检索（可选增强）
        List<VectorStoreProvider.SearchResult> childResults = new ArrayList<>();
        boolean vectorSearchFailed = false;

        if (!vectorServiceUnavailable) {
            try {
                // 检查向量服务健康状态
                if (!vectorService.isHealthy()) {
                    log.warn("Vector service is not healthy, falling back to keyword-only search");
                    vectorServiceUnavailable = true;
                    vectorSearchFailed = true;
                } else {
                    childResults = vectorService.search(kbId, query, topK * 3, embeddingModel);
                    log.info("HybridSearch: vector results size={}", childResults.size());
                }
            } catch (Exception e) {
                log.warn("Vector search failed, falling back to keyword-only search: {}", e.getMessage());
                vectorSearchFailed = true;
                // 标记向量服务不可用，避免后续请求频繁尝试
                vectorServiceUnavailable = true;
            }
        } else {
            log.info("Vector service previously marked as unavailable, using keyword-only search");
            vectorSearchFailed = true;
        }

        // 如果向量检索失败或无可用结果，降级到纯关键词搜索
        if (childResults.isEmpty() || vectorSearchFailed) {
            return keywordOnlySearch(kbId, query, topK);
        }

        // 2. Extract parent IDs and ALL doc IDs from child results
        Set<String> parentIds = new LinkedHashSet<>();
        Set<String> docIds = new LinkedHashSet<>();
        Map<String, VectorStoreProvider.SearchResult> childResultMap = new HashMap<>();

        for (VectorStoreProvider.SearchResult child : childResults) {
            String parentId = (String) child.getMetadata().get("parent_id");
            String docId = (String) child.getMetadata().get("doc_id");
            if (parentId != null && !parentId.isEmpty()) {
                parentIds.add(parentId);
            }
            if (docId != null && !docId.isEmpty()) {
                docIds.add(docId);
            }
            childResultMap.put(child.getContent(), child);
        }

        // 3. Fetch parent chunks from database for ALL relevant docIds
        List<KnowledgeDocumentChunk> parentChunks = new ArrayList<>();
        if (!docIds.isEmpty()) {
            for (String docId : docIds) {
                List<KnowledgeDocumentChunk> chunks = chunkRepository.findByDocumentIdAndChunkType(docId, "parent");
                parentChunks.addAll(chunks);
            }
        }
        log.info("All parent chunks in DB: size={}, dbIds={}, milvusParentIds={}",
                parentChunks.size(),
                parentChunks.stream().map(c -> c.getId()).toList(),
                parentIds);

        // 4. Also include keyword-matched chunks (fallback)
        log.info("Keyword search results: size={}", keywordChunks.size());

        // 5. Build final results with parent content (or child content if parent not found)
        List<VectorStoreProvider.SearchResult> finalResults = new ArrayList<>();
        Set<String> seenParentIds = new LinkedHashSet<>();

        log.info("Processing parent chunks: total={}, parent type count={}",
                parentChunks.size(),
                parentChunks.stream().filter(p -> "parent".equals(p.getChunkType())).count());

        // First, add parent chunks from vector search
        boolean hasParentResults = false;
        for (KnowledgeDocumentChunk parent : parentChunks) {
            if (parent.getChunkType() != null && "parent".equals(parent.getChunkType())) {
                String parentId = parent.getId();
                if (seenParentIds.contains(parentId)) {
                    continue;
                }

                // Only include if parent_id is in the search results
                if (!parentIds.contains(parentId)) {
                    continue;
                }

                seenParentIds.add(parentId);
                hasParentResults = true;

                // Find the best matching child score for this parent
                double bestScore = 0.0;
                for (VectorStoreProvider.SearchResult child : childResults) {
                    String childParentId = (String) child.getMetadata().get("parent_id");
                    if (parentId.equals(childParentId)) {
                        if (child.getScore() > bestScore) {
                            bestScore = child.getScore();
                        }
                    }
                }

                Map<String, Object> meta = new HashMap<>();
                meta.put("doc_id", parent.getDocumentId());
                meta.put("chunk_id", parent.getId());
                meta.put("parent_id", parentId);
                meta.put("chunk_type", "parent");
                meta.put("title", parent.getTitle());
                meta.put("source", parent.getSource());

                // Use child content for matching, parent content for context
                VectorStoreProvider.SearchResult result = VectorStoreProvider.SearchResult.builder()
                        .content(parent.getContent()) // Return parent content for LLM context
                        .score(bestScore)
                        .matchType("VECTOR")
                        .metadata(meta)
                        .build();
                finalResults.add(result);
            }
        }

        // If no parent chunks found (e.g., old data without parent_id), fallback to child chunks
        if (!hasParentResults) {
            log.info("No parent chunks found, falling back to child chunks");
            for (VectorStoreProvider.SearchResult child : childResults) {
                Map<String, Object> meta = new HashMap<>(child.getMetadata());
                meta.put("chunk_type", "child");

                VectorStoreProvider.SearchResult result = VectorStoreProvider.SearchResult.builder()
                        .content(child.getContent())
                        .score(child.getScore())
                        .matchType("VECTOR")
                        .metadata(meta)
                        .build();
                finalResults.add(result);
            }
        }

        // 6. Add keyword-matched chunks (if not already included)
        // 使用 chunk_id 精确去重，而不是 content 字符串匹配
        int keywordAdded = 0;
        Set<String> includedChunkIds = finalResults.stream()
                .map(r -> (String) r.getMetadata().get("chunk_id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (KnowledgeDocumentChunk chunk : keywordChunks) {
            if (chunk.getContent() == null) continue;

            // 使用 chunk_id 精确去重
            String chunkId = chunk.getId();
            if (includedChunkIds.contains(chunkId)) {
                continue;
            }
            includedChunkIds.add(chunkId);

            Map<String, Object> meta = new HashMap<>();
            meta.put("doc_id", chunk.getDocumentId());
            meta.put("chunk_id", chunk.getId());
            meta.put("chunk_type", chunk.getChunkType());
            meta.put("parent_id", chunk.getParentId());

            VectorStoreProvider.SearchResult result = VectorStoreProvider.SearchResult.builder()
                    .content(chunk.getContent())
                    .score(WEIGHT_KEYWORD)
                    .matchType("KEYWORD")
                    .metadata(meta)
                    .build();
            finalResults.add(result);
            keywordAdded++;
        }

        log.info("Keyword chunks added: {}. Total final results before sort: {}", keywordAdded, finalResults.size());

        // 7. Sort by score and limit to topK
        finalResults.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        log.info("Returning final results: size={}, topK={}", finalResults.size(), topK);
        return finalResults.stream().limit(topK).collect(Collectors.toList());
    }

    /**
     * Keyword-only search fallback
     */
    private List<VectorStoreProvider.SearchResult> keywordOnlySearch(String kbId, String query, int topK) {
        log.warn("keywordOnlySearch called: kbId={}, query={}, topK={}", kbId, query, topK);
        List<KnowledgeDocumentChunk> keywordChunks = keywordSearch(kbId, query, topK);
        log.warn("keywordOnlySearch returned {} chunks", keywordChunks.size());
        List<VectorStoreProvider.SearchResult> results = new ArrayList<>();

        for (KnowledgeDocumentChunk chunk : keywordChunks) {
            if (chunk.getContent() == null) continue;

            Map<String, Object> meta = new HashMap<>();
            meta.put("doc_id", chunk.getDocumentId());
            meta.put("chunk_id", chunk.getId());
            meta.put("chunk_type", chunk.getChunkType());
            meta.put("parent_id", chunk.getParentId());

            results.add(VectorStoreProvider.SearchResult.builder()
                    .content(chunk.getContent())
                    .score(WEIGHT_KEYWORD)
                    .matchType("KEYWORD")
                    .metadata(meta)
                    .build());
        }

        return results;
    }

    /**
     * Keyword search helper - split query into words and search each
     */
    private List<KnowledgeDocumentChunk> keywordSearch(String kbId, String query, int topK) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Split query into words (keep words with length >= 1)
        String[] words = query.split("[\\s,，.。!?;；]+");
        Set<String> searchWords = new java.util.HashSet<>();
        for (String word : words) {
            if (word.length() >= 1) {
                searchWords.add(word.trim());
            }
        }

        log.info("Keyword search words: {}", searchWords);

        if (searchWords.isEmpty()) {
            return new ArrayList<>();
        }

        // Search for each word and collect results
        Map<String, KnowledgeDocumentChunk> chunkMap = new LinkedHashMap<>();
        for (String word : searchWords) {
            List<KnowledgeDocumentChunk> chunks;
            if ("all".equalsIgnoreCase(kbId)) {
                chunks = chunkRepository.searchAllByKeyword(word);
            } else {
                chunks = chunkRepository.searchByKeyword(kbId, word);
            }
            for (KnowledgeDocumentChunk chunk : chunks) {
                // Deduplicate by chunk ID, keep first occurrence
                chunkMap.putIfAbsent(chunk.getId(), chunk);
            }
        }

        List<KnowledgeDocumentChunk> keywordChunks = new ArrayList<>(chunkMap.values());
        if (keywordChunks.size() > topK * 2) {
            keywordChunks = keywordChunks.subList(0, topK * 2);
        }
        return keywordChunks;
    }

    /**
     * 多模态检索 (图片 -> 文本分析 -> 混合检索)
     */
    public Map<String, Object> multimodalSearch(String kbId, String imageUrl, String vlmModel, String embeddingModel,
            int topK) {
        // 1. VLM 分析图片
        String description = visualAnalysisService.analyzeImage(imageUrl, vlmModel);

        // 2. 执行混合检索 (如果提供了 kbId)
        List<VectorStoreProvider.SearchResult> results = new ArrayList<>();
        if (kbId != null && !kbId.isEmpty() && !kbId.equalsIgnoreCase("none")) {
            results = hybridSearch(kbId, description, topK, embeddingModel);
        }

        // 3. 封装结果
        Map<String, Object> response = new HashMap<>();
        response.put("description", description);
        response.put("results", results);
        response.put("vlmModel", vlmModel);
        response.put("embeddingModel", embeddingModel);
        return response;
    }
}
