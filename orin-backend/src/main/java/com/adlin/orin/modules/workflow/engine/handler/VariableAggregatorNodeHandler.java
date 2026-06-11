package com.adlin.orin.modules.workflow.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("variableAggregatorNodeHandler")
public class VariableAggregatorNodeHandler implements NodeHandler {

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        List<Object> variables = (List<Object>) nodeData.get("variables");
        String outputVar = (String) nodeData.getOrDefault("output_type", "string");
        log.info("VariableAggregator executing: {} variables, outputType={}", variables != null ? variables.size() : 0, outputVar);

        Map<String, Object> outputs = new HashMap<>();
        if (variables == null || variables.isEmpty()) {
            outputs.put("output", null);
            return NodeExecutionResult.success(outputs);
        }

        // Each entry is either a string key or a [node, field] selector
        StringBuilder aggregated = new StringBuilder();
        for (Object var : variables) {
            Object resolved = resolveVariable(var, context);
            if (resolved != null) {
                if (!aggregated.isEmpty()) aggregated.append("\n");
                aggregated.append(resolved);
            }
        }

        outputs.put("output", aggregated.toString());
        return NodeExecutionResult.success(outputs);
    }

    @SuppressWarnings("unchecked")
    private Object resolveVariable(Object varDef, Map<String, Object> context) {
        if (varDef instanceof String key) {
            return context.get(key);
        }
        if (varDef instanceof List<?> selector && selector.size() == 2) {
            Object parent = context.get(String.valueOf(selector.get(0)));
            if (parent instanceof Map<?, ?> m) return m.get(String.valueOf(selector.get(1)));
        }
        return null;
    }
}
