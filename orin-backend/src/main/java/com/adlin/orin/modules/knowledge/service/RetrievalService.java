package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.component.VectorStoreProvider;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 检索服务
 * 负责 Hybrid Search (向量检索 + 关键词检索)
 * 支持 Parent-Child Hierarchical Retrieval
 * 支持 Rerank 重排序
 */
@Service
@Slf4j
public class RetrievalService {

    private final MilvusVectorService vectorService;
    private final KnowledgeDocumentChunkRepository chunkRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final StorageManagementService storageManagementService;
    private final com.adlin.orin.modules.multimodal.service.VisualAnalysisService visualAnalysisService;
    private final RerankService rerankService;

    // 关键词检索权重 (混合搜索时使用)
    // 注意：向量检索分数是实际的cosine相似度(0-1)，关键词分数固定为0.3
    private static final double WEIGHT_KEYWORD = 0.3;
    private static final double WEIGHT_VECTOR = 0.7;

    // 降级模式标志：当向量服务不可用时为 true
    private volatile boolean vectorServiceUnavailable = false;

    public RetrievalService(
            MilvusVectorService vectorService,
            KnowledgeDocumentChunkRepository chunkRepository,
            KnowledgeDocumentRepository documentRepository,
            StorageManagementService storageManagementService,
            com.adlin.orin.modules.multimodal.service.VisualAnalysisService visualAnalysisService,
            RerankService rerankService) {
        this.vectorService = vectorService;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.storageManagementService = storageManagementService;
        this.visualAnalysisService = visualAnalysisService;
        this.rerankService = rerankService;
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
        return hybridSearch(kbId, query, topK, null, null, null, null);
    }

    /**
     * Hybrid Search with document filtering
     */
    public List<VectorStoreProvider.SearchResult> hybridSearch(String kbId, String query, int topK,
            List<String> documentIds) {
        return hybridSearch(kbId, query, topK, null, null, null, null, documentIds);
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
        return hybridSearch(kbId, query, topK, embeddingModel, null, null, null);
    }

    public List<VectorStoreProvider.SearchResult> hybridSearch(String kbId, String query, int topK,
            String embeddingModel, Double alpha, Double threshold) {
        return hybridSearch(kbId, query, topK, embeddingModel, alpha, threshold, null);
    }

    public List<VectorStoreProvider.SearchResult> hybridSearch(String kbId, String query, int topK,
            String embeddingModel, Double alpha, Double threshold, String rerankModel) {
        return hybridSearch(kbId, query, topK, embeddingModel, alpha, threshold, rerankModel, null);
    }

    public List<VectorStoreProvider.SearchResult> hybridSearch(String kbId, String query, int topK,
            String embeddingModel, Double alpha, Double threshold, String rerankModel, List<String> documentIds) {
        // 使用前端传入的 alpha，默认为 0.7（语义权重）
        double actualAlpha = alpha != null ? alpha : 0.7;
        double actualThreshold = threshold != null ? threshold : 0.0;

        log.info("HybridSearch: kbId={}, query={}, alpha={}, threshold={}, rerankModel={}, documentIds={}",
                kbId, query, actualAlpha, actualThreshold, rerankModel, documentIds);

        // 1. 首先执行关键词检索（始终可用）
        // 返回 Map.Entry，包含 chunk 和匹配词数
        List<Map.Entry<KnowledgeDocumentChunk, Integer>> keywordResults = keywordSearch(kbId, query, topK * 3, documentIds);
        log.info("HybridSearch: keywordResults.size={}", keywordResults.size());

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
                    // Filter by documentIds if provided
                    if (documentIds != null && !documentIds.isEmpty()) {
                        Set<String> docIdSet = new HashSet<>(documentIds);
                        childResults = childResults.stream()
                                .filter(r -> {
                                    String docId = (String) r.getMetadata().get("doc_id");
                                    return docIdSet.contains(docId);
                                })
                                .collect(Collectors.toList());
                        log.info("HybridSearch: vector results after docId filter size={}", childResults.size());
                    } else {
                        log.info("HybridSearch: vector results size={}", childResults.size());
                    }
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
            return keywordOnlySearch(kbId, query, topK, documentIds);
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
        log.info("Keyword search results: size={}", keywordResults.size());

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
                meta.put("paragraphIndex", parent.getPosition());
                meta.put("pageNumber", parent.getPosition() != null ? parent.getPosition() + 1 : 1);

                // Use child content for matching, parent content for context
                // Apply alpha weight to vector score
                double weightedVectorScore = bestScore * actualAlpha;
                VectorStoreProvider.SearchResult result = VectorStoreProvider.SearchResult.builder()
                        .content(parent.getContent()) // Return parent content for LLM context
                        .score(weightedVectorScore)
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
                // Apply alpha weight to vector score
                double weightedVectorScore = child.getScore() * actualAlpha;

                VectorStoreProvider.SearchResult result = VectorStoreProvider.SearchResult.builder()
                        .content(child.getContent())
                        .score(weightedVectorScore)
                        .matchType("VECTOR")
                        .metadata(meta)
                        .build();
                finalResults.add(result);
            }
        }

        // 6. Add keyword-matched chunks (if not already included)
        // 使用 chunk_id 精确去重，而不是 content 字符串匹配
        // 计算关键词权重：1 - alpha
        double keywordWeight = 1.0 - actualAlpha;

        int keywordAdded = 0;
        Set<String> includedChunkIds = finalResults.stream()
                .map(r -> (String) r.getMetadata().get("chunk_id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Map.Entry<KnowledgeDocumentChunk, Integer> entry : keywordResults) {
            KnowledgeDocumentChunk chunk = entry.getKey();
            int matchCount = entry.getValue();
            if (chunk.getContent() == null) continue;

            // 使用 chunk_id 精确去重
            String chunkId = chunk.getId();
            if (includedChunkIds.contains(chunkId)) {
                continue;
            }
            includedChunkIds.add(chunkId);

            // 计算关键词得分：关键词权重 * 匹配度（匹配词数/总词数，上限1.0）
            // 这样匹配词越多得分越高
            double matchRatio = Math.min(matchCount / 3.0, 1.0);
            double keywordScore = keywordWeight * matchRatio;

            Map<String, Object> meta = new HashMap<>();
            meta.put("doc_id", chunk.getDocumentId());
            meta.put("chunk_id", chunk.getId());
            meta.put("chunk_type", chunk.getChunkType());
            meta.put("parent_id", chunk.getParentId());
            meta.put("match_count", matchCount);
            meta.put("paragraphIndex", chunk.getPosition());
            meta.put("pageNumber", chunk.getPosition() != null ? chunk.getPosition() + 1 : 1);

            VectorStoreProvider.SearchResult result = VectorStoreProvider.SearchResult.builder()
                    .content(chunk.getContent())
                    .score(keywordScore)
                    .matchType("KEYWORD")
                    .metadata(meta)
                    .build();
            finalResults.add(result);
            keywordAdded++;
        }

        // 统计向量和关键词结果数量
        long vectorCount = finalResults.stream().filter(r -> "VECTOR".equals(r.getMatchType())).count();
        long keywordCount = finalResults.stream().filter(r -> "KEYWORD".equals(r.getMatchType())).count();
        log.info("Keyword chunks added: {}. Before sort: VECTOR={}, KEYWORD={}, total={}",
                keywordAdded, vectorCount, keywordCount, finalResults.size());

        // 7. Sort by score
        finalResults.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        // 8. Apply threshold filter (过滤低于阈值的结果)
        if (actualThreshold > 0) {
            finalResults = finalResults.stream()
                    .filter(r -> r.getScore() >= actualThreshold)
                    .collect(Collectors.toList());
            log.info("After threshold filter ({}): size={}", actualThreshold, finalResults.size());
        }

        // 排序后再统计一次
        vectorCount = finalResults.stream().filter(r -> "VECTOR".equals(r.getMatchType())).count();
        keywordCount = finalResults.stream().filter(r -> "KEYWORD".equals(r.getMatchType())).count();

        // 9. Apply Rerank (if enabled)
        if (rerankModel != null && !"none".equalsIgnoreCase(rerankModel) && !finalResults.isEmpty()) {
            log.info("Applying rerank with model: {}", rerankModel);
            // 增加召回数量用于 rerank，rerank 后会取 topK
            List<VectorStoreProvider.SearchResult> rerankedResults = rerankService.rerank(
                    query, finalResults, rerankModel, topK * 2);
            if (!rerankedResults.isEmpty()) {
                finalResults = rerankedResults;
                log.info("After rerank: size={}", finalResults.size());
            }
        }

        log.info("Returning final results: size={}, topK={}, VECTOR={}, KEYWORD={}",
                finalResults.size(), topK, vectorCount, keywordCount);
        return finalResults.stream().limit(topK).collect(Collectors.toList());
    }

    /**
     * Keyword-only search fallback
     */
    private List<VectorStoreProvider.SearchResult> keywordOnlySearch(String kbId, String query, int topK, List<String> documentIds) {
        log.warn("keywordOnlySearch called: kbId={}, query={}, topK={}, documentIds={}", kbId, query, topK, documentIds);
        // 先走纯文本索引文件（parsed/*.txt）检索，再补 DB 关键词检索，最后按分数融合。
        List<VectorStoreProvider.SearchResult> textIndexResults =
                textIndexFallbackSearch(kbId, query, Math.max(topK * 3, 10), documentIds);
        List<Map.Entry<KnowledgeDocumentChunk, Integer>> keywordResults = keywordSearch(kbId, query, Math.max(topK * 2, 10), documentIds);

        Map<String, VectorStoreProvider.SearchResult> merged = new LinkedHashMap<>();
        for (VectorStoreProvider.SearchResult result : textIndexResults) {
            merged.put(buildResultDedupKey(result), result);
        }

        for (Map.Entry<KnowledgeDocumentChunk, Integer> entry : keywordResults) {
            KnowledgeDocumentChunk chunk = entry.getKey();
            int matchCount = entry.getValue();
            if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                continue;
            }

            double matchRatio = Math.min(matchCount / 3.0, 1.0);
            // 让 DB 关键词结果作为补充，不覆盖文本索引更高质量命中。
            double keywordScore = 0.55 * matchRatio;

            Map<String, Object> meta = new HashMap<>();
            meta.put("doc_id", chunk.getDocumentId());
            meta.put("chunk_id", chunk.getId());
            meta.put("match_count", matchCount);
            meta.put("chunk_type", chunk.getChunkType());
            meta.put("parent_id", chunk.getParentId());
            meta.put("paragraphIndex", chunk.getPosition());
            meta.put("pageNumber", chunk.getPosition() != null ? chunk.getPosition() + 1 : 1);

            VectorStoreProvider.SearchResult keywordResult = VectorStoreProvider.SearchResult.builder()
                    .content(chunk.getContent())
                    .score(keywordScore)
                    .matchType("KEYWORD")
                    .metadata(meta)
                    .build();

            String key = buildResultDedupKey(keywordResult);
            VectorStoreProvider.SearchResult existing = merged.get(key);
            if (existing == null || keywordResult.getScore() > existing.getScore()) {
                merged.put(key, keywordResult);
            }
        }

        List<VectorStoreProvider.SearchResult> finalResults = new ArrayList<>(merged.values());
        finalResults.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        List<VectorStoreProvider.SearchResult> limited = finalResults.stream().limit(topK).collect(Collectors.toList());
        log.warn("keywordOnlySearch merged fallback results: textIndex={}, keywordDb={}, final={}",
                textIndexResults.size(), keywordResults.size(), limited.size());
        return limited;
    }

    private String buildResultDedupKey(VectorStoreProvider.SearchResult result) {
        if (result == null) {
            return "";
        }
        Map<String, Object> metadata = result.getMetadata();
        String chunkId = metadata != null && metadata.get("chunk_id") != null ? String.valueOf(metadata.get("chunk_id")) : "";
        String docId = metadata != null && metadata.get("doc_id") != null ? String.valueOf(metadata.get("doc_id")) : "";
        if (!chunkId.isEmpty()) {
            return "chunk:" + chunkId;
        }
        String content = result.getContent() != null ? result.getContent() : "";
        return "doc:" + docId + ":content:" + Integer.toHexString(content.hashCode());
    }

    private List<VectorStoreProvider.SearchResult> textIndexFallbackSearch(String kbId, String query, int topK, List<String> documentIds) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        List<KnowledgeDocument> docs;
        if ("all".equalsIgnoreCase(kbId)) {
            docs = documentRepository.findAll();
        } else {
            docs = documentRepository.findByKnowledgeBaseIdOrderByUploadTimeDesc(kbId);
        }

        if (documentIds != null && !documentIds.isEmpty()) {
            Set<String> docIdSet = new HashSet<>(documentIds);
            docs = docs.stream().filter(d -> docIdSet.contains(d.getId())).collect(Collectors.toList());
        }

        List<VectorStoreProvider.SearchResult> results = new ArrayList<>();
        for (KnowledgeDocument doc : docs) {
            String text = readDocumentTextForFallback(doc);
            if (text == null || text.isBlank()) {
                continue;
            }

            List<String> segments = splitTextForFallback(text, 700, 140);
            int segmentIndex = 0;
            for (String segment : segments) {
                double score = scoreTextSegment(query, segment);
                if (score <= 0) {
                    segmentIndex++;
                    continue;
                }

                Map<String, Object> meta = new HashMap<>();
                meta.put("doc_id", doc.getId());
                meta.put("chunk_id", doc.getId() + "#text#" + segmentIndex);
                meta.put("chunk_type", "text_index");
                meta.put("title", doc.getFileName());
                meta.put("source", doc.getParsedTextPath() != null ? doc.getParsedTextPath() : doc.getFileName());
                meta.put("paragraphIndex", segmentIndex);
                meta.put("pageNumber", segmentIndex + 1);

                results.add(VectorStoreProvider.SearchResult.builder()
                        .content(segment)
                        .score(score)
                        .matchType("TEXT_INDEX")
                        .metadata(meta)
                        .build());
                segmentIndex++;
            }
        }

        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (results.size() > topK) {
            results = results.subList(0, topK);
        }
        return results;
    }

    private String readDocumentTextForFallback(KnowledgeDocument doc) {
        if (doc == null) {
            return "";
        }

        List<Path> candidates = new ArrayList<>();
        if (doc.getParsedTextPath() != null && !doc.getParsedTextPath().isBlank()) {
            candidates.add(Path.of(doc.getParsedTextPath()));
        }
        try {
            candidates.add(storageManagementService.getParsedFilePath(doc.getKnowledgeBaseId(), doc.getId(), doc.getFileType()));
        } catch (Exception e) {
            log.debug("Build parsed path failed for doc {}: {}", doc.getId(), e.getMessage());
        }

        for (Path path : candidates) {
            try {
                if (path != null && Files.exists(path)) {
                    String text = Files.readString(path);
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            } catch (IOException e) {
                log.debug("Read parsed text failed: docId={}, path={}, err={}", doc.getId(), path, e.getMessage());
            }
        }

        if (doc.getContentPreview() != null && !doc.getContentPreview().isBlank()) {
            return doc.getContentPreview();
        }
        return "";
    }

    private List<String> splitTextForFallback(String text, int maxLen, int overlap) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<String> chunks = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        String[] paragraphs = normalized.split("\\n\\s*\\n");

        for (String paragraph : paragraphs) {
            String p = paragraph.trim();
            if (p.isBlank()) {
                continue;
            }
            if (p.length() <= maxLen) {
                chunks.add(p);
                continue;
            }

            int start = 0;
            while (start < p.length()) {
                int end = Math.min(start + maxLen, p.length());
                String segment = p.substring(start, end).trim();
                if (!segment.isBlank()) {
                    chunks.add(segment);
                }
                if (end >= p.length()) {
                    break;
                }
                start = Math.max(start + maxLen - overlap, start + 1);
            }
        }

        if (chunks.isEmpty() && !normalized.isBlank()) {
            chunks.add(normalized.length() > maxLen ? normalized.substring(0, maxLen) : normalized);
        }
        return chunks;
    }

    private double scoreTextSegment(String query, String segment) {
        if (query == null || query.isBlank() || segment == null || segment.isBlank()) {
            return 0.0;
        }

        String q = query.toLowerCase(Locale.ROOT).trim();
        String s = segment.toLowerCase(Locale.ROOT);
        double score = 0.0;

        if (s.contains(q)) {
            score += 0.60;
        }

        Set<String> terms = Arrays.stream(q.split("[\\s,，.。!?;；:：/\\\\|]+"))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String term : terms) {
            if (s.contains(term)) {
                score += Math.min(0.10 + term.length() * 0.01, 0.22);
            }
        }

        Set<Character> qChars = q.chars()
                .mapToObj(c -> (char) c)
                .filter(c -> !Character.isWhitespace(c) && Character.isLetterOrDigit(c))
                .collect(Collectors.toSet());
        if (!qChars.isEmpty()) {
            long hit = qChars.stream().filter(c -> s.indexOf(c) >= 0).count();
            score += (hit * 1.0 / qChars.size()) * 0.20;
        }

        if (segment.length() >= 60 && segment.length() <= 600) {
            score += 0.05;
        }

        return Math.min(score, 0.98);
    }

    /**
     * Keyword search helper - split query into words and search each
     * Returns map of chunk to matched word count
     */
    private List<Map.Entry<KnowledgeDocumentChunk, Integer>> keywordSearch(String kbId, String query, int topK, List<String> documentIds) {
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

        log.info("Keyword search: kbId={}, query={}, words={}, documentIds={}", kbId, query, searchWords, documentIds);

        if (searchWords.isEmpty()) {
            return new ArrayList<>();
        }

        // Search for each word and collect results with match count
        Map<String, Map.Entry<KnowledgeDocumentChunk, Integer>> chunkMap = new LinkedHashMap<>();
        for (String word : searchWords) {
            List<KnowledgeDocumentChunk> chunks;
            if (documentIds != null && !documentIds.isEmpty()) {
                // Filter by specific document IDs
                chunks = chunkRepository.searchByDocIds(documentIds, word);
            } else if ("all".equalsIgnoreCase(kbId)) {
                chunks = chunkRepository.searchAllByKeyword(word);
            } else {
                chunks = chunkRepository.searchByKeyword(kbId, word);
            }
            log.info("Keyword '{}' returned {} chunks in kbId={}", word, chunks.size(), kbId);
            for (KnowledgeDocumentChunk chunk : chunks) {
                String chunkId = chunk.getId();
                if (chunkMap.containsKey(chunkId)) {
                    // Increment match count for existing chunk
                    Map.Entry<KnowledgeDocumentChunk, Integer> entry = chunkMap.get(chunkId);
                    chunkMap.put(chunkId, Map.entry(entry.getKey(), entry.getValue() + 1));
                } else {
                    // New chunk, match count = 1
                    chunkMap.put(chunkId, Map.entry(chunk, 1));
                }
            }
        }

        List<Map.Entry<KnowledgeDocumentChunk, Integer>> keywordChunks = new ArrayList<>(chunkMap.values());
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
