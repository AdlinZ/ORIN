package com.adlin.orin.modules.workflow.engine.handler;

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

        var results = retrievalService.hybridSearch(kbId, query, 4);

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
}
