package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.component.VectorStoreProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Rerank 服务
 * 支持调用 rerank 模型对检索结果进行重排序
 */
@Slf4j
@Service
public class RerankService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${siliconflow.api.key:}")
    private String apiKey;

    @Value("${siliconflow.api.base-url:https://api.siliconflow.cn/v1}")
    private String baseUrl;

    //  rerank 模型映射
    private static final Map<String, String> RERANK_MODELS = Map.of(
            "bge-v2", "BAAI/bge-reranker-v2-m3",
            "cohere-v3", "Cohere/Cohere-rerank-3-multilingual",
            "qwen-8b", "Qwen/Qwen3-Reranker-8B",
            "qwen-4b", "Qwen/Qwen3-Reranker-4B",
            "qwen-0.6b", "Qwen/Qwen3-Reranker-0.6B"
    );

    public RerankService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 对检索结果进行 rerank
     *
     * @param query       查询语句
     * @param results     检索结果列表
     * @param rerankModel rerank 模型标识
     * @param topK        返回结果数量
     * @return rerank 后的结果列表
     */
    public List<VectorStoreProvider.SearchResult> rerank(String query,
                                                          List<VectorStoreProvider.SearchResult> results,
                                                          String rerankModel,
                                                          int topK) {
        if (results == null || results.isEmpty()) {
            log.info("Rerank: no results to rerank");
            return results;
        }

        if (rerankModel == null || "none".equalsIgnoreCase(rerankModel)) {
            log.info("Rerank: rerank model is none, skip reranking");
            return results;
        }

        String modelName = RERANK_MODELS.get(rerankModel.toLowerCase());
        if (modelName == null) {
            log.warn("Rerank: unknown rerank model {}, skip reranking", rerankModel);
            return results;
        }

        log.info("Rerank: using model {}, input {} results", modelName, results.size());

        try {
            return callRerankApi(query, results, modelName, topK);
        } catch (Exception e) {
            log.error("Rerank failed: {}", e.getMessage(), e);
            return results;
        }
    }

    /**
     * 调用 SiliconFlow Rerank API
     */
    private List<VectorStoreProvider.SearchResult> callRerankApi(String query,
                                                                   List<VectorStoreProvider.SearchResult> results,
                                                                   String modelName,
                                                                   int topK) {
        // 构建请求体
        List<String> documents = results.stream()
                .map(VectorStoreProvider.SearchResult::getContent)
                .collect(Collectors.toList());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("query", query);
        requestBody.put("documents", documents);
        requestBody.put("top_n", Math.min(topK, documents.size()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String url = baseUrl + "/rerank";

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseRerankResponse(response.getBody(), results, topK);
            } else {
                log.warn("Rerank API returned non-2xx: {}", response.getStatusCode());
                return results;
            }
        } catch (Exception e) {
            log.error("Failed to call rerank API: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 解析 rerank API 响应
     */
    private List<VectorStoreProvider.SearchResult> parseRerankResponse(Map response,
                                                                        List<VectorStoreProvider.SearchResult> originalResults,
                                                                        int topK) {
        List<VectorStoreProvider.SearchResult> rerankedResults = new ArrayList<>();

        // SiliconFlow rerank API 响应格式:
        // {
        //   "results": [
        //     {"index": 0, "relevance_score": 0.95},
        //     {"index": 1, "relevance_score": 0.85}
        //   ]
        // }

        List<Map<String, Object>> rerankResults = (List<Map<String, Object>>) response.get("results");
        if (rerankResults == null || rerankResults.isEmpty()) {
            log.warn("Rerank response has no results");
            return originalResults;
        }

        // 构建原始结果映射
        Map<Integer, VectorStoreProvider.SearchResult> originalMap = new HashMap<>();
        for (int i = 0; i < originalResults.size(); i++) {
            originalMap.put(i, originalResults.get(i));
        }

        // 按 rerank 分数排序
        for (Map<String, Object> rerankItem : rerankResults) {
            Integer index = (Integer) rerankItem.get("index");
            Double score = rerankItem.get("relevance_score") != null
                    ? ((Number) rerankItem.get("relevance_score")).doubleValue()
                    : 0.0;

            VectorStoreProvider.SearchResult original = originalMap.get(index);
            if (original != null) {
                // 创建新的结果，保留原始元数据但更新分数
                Map<String, Object> newMeta = new HashMap<>(original.getMetadata());
                newMeta.put("original_score", original.getScore());
                newMeta.put("rerank_model", "reranked");

                VectorStoreProvider.SearchResult newResult = VectorStoreProvider.SearchResult.builder()
                        .content(original.getContent())
                        .score(score)
                        .matchType("RERANKED")
                        .metadata(newMeta)
                        .build();
                rerankedResults.add(newResult);
            }
        }

        log.info("Rerank: returned {} results", rerankedResults.size());
        return rerankedResults.stream().limit(topK).collect(Collectors.toList());
    }

    /**
     * 获取可用的 rerank 模型列表
     */
    public List<String> getAvailableRerankModels() {
        return new ArrayList<>(RERANK_MODELS.keySet());
    }
}
