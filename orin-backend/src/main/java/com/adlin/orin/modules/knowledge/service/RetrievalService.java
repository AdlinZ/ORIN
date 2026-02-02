package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.component.VectorStoreProvider;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 检索服务
 * 负责 Hybrid Search (向量检索 + 关键词检索)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetrievalService {

    private final MilvusVectorService vectorService;
    private final KnowledgeDocumentChunkRepository chunkRepository;

    private static final double WEIGHT_VECTOR = 0.7;
    private static final double WEIGHT_KEYWORD = 0.3;

    /**
     * 混合检索
     *
     * @param kbId           知识库 ID
     * @param query          查询语句
     * @param topK           返回结果数量
     * @param embeddingModel 嵌入模型名称 (可选)
     * @return 检索结果列表
     */
    public List<VectorStoreProvider.SearchResult> hybridSearch(String kbId, String query, int topK) {
        return hybridSearch(kbId, query, topK, null);
    }

    public List<VectorStoreProvider.SearchResult> hybridSearch(String kbId, String query, int topK,
            String embeddingModel) {
        // 1. 向量检索
        List<VectorStoreProvider.SearchResult> vectorResults = vectorService.search(kbId, query, topK * 2,
                embeddingModel);

        // 2. 关键词检索
        List<KnowledgeDocumentChunk> keywordChunks = chunkRepository.searchByKeyword(kbId, query);
        // Limit keyword results manually if too many
        if (keywordChunks.size() > topK * 2) {
            keywordChunks = keywordChunks.subList(0, topK * 2);
        }

        // 3. 结果融合 (Weighted Scoring / RRF)
        // 这里使用简单的加权评分融合
        Map<String, Double> scoreMap = new HashMap<>(); // Chunk Content -> Score
        Map<String, VectorStoreProvider.SearchResult> contentMap = new HashMap<>();

        // 处理向量结果
        for (VectorStoreProvider.SearchResult res : vectorResults) {
            scoreMap.put(res.getContent(), res.getScore() * WEIGHT_VECTOR);
            contentMap.put(res.getContent(), res);
        }

        // 处理关键词结果
        for (KnowledgeDocumentChunk chunk : keywordChunks) {
            if (chunk.getContent() == null)
                continue;

            double baseScore = 1.0; // Keyword match implies full relevance for that term
            double finalScore = baseScore * WEIGHT_KEYWORD;

            if (scoreMap.containsKey(chunk.getContent())) {
                scoreMap.put(chunk.getContent(), scoreMap.get(chunk.getContent()) + finalScore);
            } else {
                scoreMap.put(chunk.getContent(), finalScore);

                Map<String, Object> meta = new HashMap<>();
                meta.put("doc_id", chunk.getDocumentId());
                meta.put("chunk_id", chunk.getId());

                contentMap.put(chunk.getContent(), VectorStoreProvider.SearchResult.builder()
                        .content(chunk.getContent())
                        .score(finalScore) // Initial low score for keyword only, but will rank
                        .metadata(meta)
                        .build());
            }
        }

        // 排序并截取 TopK
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    VectorStoreProvider.SearchResult res = contentMap.get(entry.getKey());
                    res.setScore(entry.getValue());
                    return res;
                })
                .collect(Collectors.toList());
    }

    private final com.adlin.orin.modules.multimodal.service.VisualAnalysisService visualAnalysisService;

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
