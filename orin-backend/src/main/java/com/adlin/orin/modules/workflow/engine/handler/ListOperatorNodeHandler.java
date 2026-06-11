package com.adlin.orin.modules.workflow.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component("listOperatorNodeHandler")
public class ListOperatorNodeHandler implements NodeHandler {

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        String inputVar = (String) nodeData.getOrDefault("variable", "list");
        String operator = (String) nodeData.getOrDefault("operator", "first");
        Object raw = context.get(inputVar);
        log.info("ListOperator executing: operator={}, inputVar={}", operator, inputVar);

        List<Object> list = toList(raw);

        Object result = switch (operator) {
            case "first" -> list.isEmpty() ? null : list.get(0);
            case "last" -> list.isEmpty() ? null : list.get(list.size() - 1);
            case "filter" -> applyFilter(list, nodeData);
            case "slice" -> applySlice(list, nodeData);
            case "count" -> list.size();
            default -> list;
        };

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("output", result);
        return NodeExecutionResult.success(outputs);
    }

    private List<Object> toList(Object raw) {
        if (raw instanceof List<?> l) return (List<Object>) l;
        if (raw != null) return List.of(raw);
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Object applyFilter(List<Object> list, Map<String, Object> nodeData) {
        String field = (String) nodeData.get("filter_by");
        Object value = nodeData.get("filter_value");
        if (field == null) return list;
        return list.stream()
                .filter(item -> item instanceof Map<?, ?> m && String.valueOf(value).equals(String.valueOf(m.get(field))))
                .collect(Collectors.toList());
    }

    private Object applySlice(List<Object> list, Map<String, Object> nodeData) {
        int start = nodeData.get("slice_start") instanceof Number n ? n.intValue() : 0;
        int end = nodeData.get("slice_end") instanceof Number n ? n.intValue() : list.size();
        start = Math.max(0, Math.min(start, list.size()));
        end = Math.max(start, Math.min(end, list.size()));
        return list.subList(start, end);
    }
}
