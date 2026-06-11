package com.adlin.orin.modules.workflow.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("questionClassifierNodeHandler")
public class QuestionClassifierNodeHandler implements NodeHandler {

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        String inputVar = (String) nodeData.getOrDefault("query_variable", "query");
        List<Map<String, Object>> classes = (List<Map<String, Object>>) nodeData.get("classes");
        String query = String.valueOf(context.getOrDefault(inputVar, ""));
        log.info("QuestionClassifier executing: query length={}, classes={}", query.length(), classes != null ? classes.size() : 0);

        String matchedCategory = null;
        String matchedHandle = "source";

        if (classes != null) {
            for (Map<String, Object> cls : classes) {
                String name = (String) cls.get("name");
                List<String> keywords = (List<String>) cls.get("keywords");
                if (name != null && keywords != null) {
                    String lowerQuery = query.toLowerCase();
                    boolean matched = keywords.stream().anyMatch(k -> lowerQuery.contains(k.toLowerCase()));
                    if (matched) {
                        matchedCategory = name;
                        matchedHandle = (String) cls.getOrDefault("handle", name);
                        break;
                    }
                }
            }
            // default to first class if nothing matched
            if (matchedCategory == null && !classes.isEmpty()) {
                Map<String, Object> first = classes.get(0);
                matchedCategory = (String) first.get("name");
                matchedHandle = (String) first.getOrDefault("handle", matchedCategory);
            }
        }

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("category", matchedCategory);
        outputs.put("output", matchedCategory);
        return NodeExecutionResult.success(outputs, matchedHandle);
    }
}
