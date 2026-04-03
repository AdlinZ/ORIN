package com.adlin.orin.modules.workflow.engine.handler;

import com.adlin.orin.modules.knowledge.service.KnowledgeManageService;
import com.adlin.orin.modules.knowledge.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("knowledgeNodeHandler")
@RequiredArgsConstructor
public class KnowledgeNodeHandler implements NodeHandler {

    private final RetrievalService retrievalService;
    private final KnowledgeManageService knowledgeManageService;

    @Override
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        String query = (String) context.get("query");
        Object datasetIdsObj = context.get("dataset_ids");
        String kbId = "";

        if (datasetIdsObj instanceof List) {
            List<?> list = (List<?>) datasetIdsObj;
            if (!list.isEmpty())
                kbId = list.get(0).toString();
        } else if (datasetIdsObj instanceof String) {
            kbId = (String) datasetIdsObj;
        }

        if (query == null || kbId.isEmpty()) {
            throw new IllegalArgumentException("Knowledge node requires 'query' and 'dataset_ids'");
        }

        log.info("KnowledgeNode executing: query='{}', kbId={}", query, kbId);

        // 获取知识库的默认检索配置
        Map<String, Object> kbConfig = knowledgeManageService.getRetrievalConfig(kbId);

        // 使用知识库的 topK 配置，如果没有则默认 4
        int topK = 4;
        Double alpha = null;
        Double threshold = null;
        Boolean enableRerank = null;
        String rerankModel = null;
        String embeddingModel = null;
        if (kbConfig != null) {
            if (kbConfig.containsKey("topK")) {
                topK = asInteger(kbConfig.get("topK"), topK);
            }
            if (kbConfig.containsKey("alpha")) {
                alpha = asDouble(kbConfig.get("alpha"));
            }
            if (kbConfig.containsKey("similarityThreshold")) {
                threshold = asDouble(kbConfig.get("similarityThreshold"));
            }
            if (kbConfig.containsKey("enableRerank")) {
                enableRerank = (Boolean) kbConfig.get("enableRerank");
            }
            if (kbConfig.containsKey("rerankModel")) {
                rerankModel = (String) kbConfig.get("rerankModel");
            }
            if (kbConfig.containsKey("embeddingModel")) {
                embeddingModel = String.valueOf(kbConfig.get("embeddingModel"));
            }
        }

        // 如果启用了 Rerank，则传入 rerankModel
        String finalRerankModel = (enableRerank != null && enableRerank && rerankModel != null) ? rerankModel : null;
        var results = retrievalService.hybridSearch(kbId, query, topK, embeddingModel, alpha, threshold, finalRerankModel);

        List<Map<String, Object>> docList = new ArrayList<>();
        StringBuilder contextBuilder = new StringBuilder();

        for (var res : results) {
            Map<String, Object> doc = new HashMap<>();
            doc.put("content", res.getContent());
            doc.put("score", res.getScore());
            doc.put("metadata", res.getMetadata());
            docList.add(doc);

            contextBuilder.append(res.getContent()).append("\n\n");
        }

        Map<String, Object> output = new HashMap<>();
        output.put("result", docList);
        output.put("output", contextBuilder.toString());

        return NodeExecutionResult.success(output);
    }

    private int asInteger(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && !((String) value).isBlank()) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
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
}
