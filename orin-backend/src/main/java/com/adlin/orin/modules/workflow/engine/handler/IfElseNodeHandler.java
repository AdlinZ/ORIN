package com.adlin.orin.modules.workflow.engine.handler;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component("ifElseNodeHandler")
public class IfElseNodeHandler implements NodeHandler {

    @Override
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        List<?> conditions = nodeData.get("conditions") instanceof List<?> rawConditions
                ? rawConditions
                : List.of();
        String logicalOperator = String.valueOf(nodeData.getOrDefault("logical_operator", "and"));
        boolean conditionMet = evaluateConditions(conditions, logicalOperator, context);

        Map<String, Object> output = new HashMap<>();
        output.put("result", conditionMet);
        output.put("selected_branch", conditionMet ? "if" : "else");

        String selectedHandle = conditionMet ? "if" : "else";
        return NodeExecutionResult.success(output, selectedHandle);
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateConditions(List<?> conditions, String logicalOperator, Map<String, Object> context) {
        if (conditions.isEmpty()) {
            return !"or".equalsIgnoreCase(logicalOperator);
        }

        boolean useOr = "or".equalsIgnoreCase(logicalOperator);
        boolean result = !useOr;
        for (Object item : conditions) {
            if (!(item instanceof Map<?, ?> rawCondition)) {
                boolean conditionResult = false;
                result = useOr ? result || conditionResult : result && conditionResult;
                continue;
            }

            Map<String, Object> condition = (Map<String, Object>) rawCondition;
            boolean conditionResult = evaluateCondition(condition, context);
            result = useOr ? result || conditionResult : result && conditionResult;
        }
        return result;
    }

    private boolean evaluateCondition(Map<String, Object> condition, Map<String, Object> context) {
        String variable = stringValue(condition.get("variable"));
        String operator = stringValue(condition.get("operator"));
        Object expected = condition.get("value");
        Object actual = resolveVariable(variable, context);

        return switch (operator == null ? "" : operator) {
            case "contains" -> String.valueOf(actual).contains(String.valueOf(expected));
            case "not_contains" -> !String.valueOf(actual).contains(String.valueOf(expected));
            case "equals" -> Objects.equals(String.valueOf(actual), String.valueOf(expected));
            case "not_equals" -> !Objects.equals(String.valueOf(actual), String.valueOf(expected));
            case "is_empty" -> isEmpty(actual);
            case "is_not_empty" -> !isEmpty(actual);
            default -> false;
        };
    }

    @SuppressWarnings("unchecked")
    private Object resolveVariable(String path, Map<String, Object> context) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Object current = context;
        for (String part : path.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = ((Map<String, Object>) map).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
