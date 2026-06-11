package com.adlin.orin.modules.workflow.engine.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("parameterExtractorNodeHandler")
public class ParameterExtractorNodeHandler implements NodeHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) nodeData.get("parameters");
        String inputVar = (String) nodeData.getOrDefault("query", "query");
        String inputText = String.valueOf(context.getOrDefault(inputVar, ""));
        log.info("ParameterExtractor executing: {} params from input length={}", parameters != null ? parameters.size() : 0, inputText.length());

        Map<String, Object> outputs = new HashMap<>();
        if (parameters == null || parameters.isEmpty()) {
            return NodeExecutionResult.success(outputs);
        }

        // Attempt JSON parse first (LLM output may be JSON)
        try {
            Map<String, Object> parsed = MAPPER.readValue(inputText, new TypeReference<>() {});
            for (Map<String, Object> param : parameters) {
                String name = (String) param.get("name");
                if (name != null && parsed.containsKey(name)) {
                    outputs.put(name, parsed.get(name));
                }
            }
        } catch (Exception e) {
            // Not JSON — return empty extractions with param names as null
            for (Map<String, Object> param : parameters) {
                String name = (String) param.get("name");
                if (name != null) outputs.put(name, null);
            }
        }

        outputs.put("output", outputs);
        return NodeExecutionResult.success(outputs);
    }
}
