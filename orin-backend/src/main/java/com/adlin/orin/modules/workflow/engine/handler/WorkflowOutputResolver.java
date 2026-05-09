package com.adlin.orin.modules.workflow.engine.handler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WorkflowOutputResolver {

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");
    private static final List<String> DEFAULT_TEXT_KEYS = List.of(
            "answer", "final_answer", "finalAnswer", "text", "content", "message", "output", "result", "value", "body");

    private WorkflowOutputResolver() {
    }

    static Map<String, Object> resolveOutputMappings(Object rawMappings, Map<String, Object> context) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        for (OutputMapping mapping : parseMappings(rawMappings)) {
            if (mapping.name().isBlank()) {
                continue;
            }
            outputs.put(mapping.name(), resolveExpression(mapping.value(), context));
        }
        return outputs;
    }

    static Object resolveAnswer(Map<String, Object> nodeData, Map<String, Object> context) {
        Object configured = firstPresent(nodeData, "answer", "text", "value", "template", "source", "sourceExpression");
        if (configured != null && !String.valueOf(configured).isBlank()) {
            return resolveExpression(configured, context);
        }
        return firstReadableValue(context);
    }

    private static List<OutputMapping> parseMappings(Object rawMappings) {
        List<OutputMapping> mappings = new ArrayList<>();
        if (rawMappings instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object rawName = firstPresent(map, "name", "key", "variable");
                    Object rawValue = firstPresent(map, "value", "expression", "source", "sourceExpression");
                    if (rawName != null && rawValue != null) {
                        mappings.add(new OutputMapping(String.valueOf(rawName), rawValue));
                    }
                }
            }
        } else if (rawMappings instanceof Map<?, ?> map) {
            map.forEach((key, value) -> mappings.add(new OutputMapping(String.valueOf(key), value)));
        }
        return mappings;
    }

    private static Object resolveExpression(Object rawValue, Map<String, Object> context) {
        if (!(rawValue instanceof String expression)) {
            return rawValue;
        }
        Matcher exactMatcher = EXPRESSION_PATTERN.matcher(expression.trim());
        if (exactMatcher.matches()) {
            Object resolved = resolvePath(exactMatcher.group(1), context);
            return resolved == null ? "" : resolved;
        }

        Matcher matcher = EXPRESSION_PATTERN.matcher(expression);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object resolved = resolvePath(matcher.group(1), context);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(resolved == null ? "" : String.valueOf(resolved)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    @SuppressWarnings("unchecked")
    private static Object resolvePath(String path, Map<String, Object> context) {
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

    @SuppressWarnings("unchecked")
    private static Object firstReadableValue(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> object = (Map<String, Object>) map;
        for (String key : DEFAULT_TEXT_KEYS) {
            Object nested = firstReadableValue(object.get(key));
            if (nested != null) {
                return nested;
            }
        }
        for (Object nestedValue : object.values()) {
            Object nested = firstReadableValue(nestedValue);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static Object firstPresent(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private record OutputMapping(String name, Object value) {
    }
}
