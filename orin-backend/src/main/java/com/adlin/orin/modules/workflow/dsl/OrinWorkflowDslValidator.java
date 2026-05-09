package com.adlin.orin.modules.workflow.dsl;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OrinWorkflowDslValidator {

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    @SuppressWarnings("unchecked")
    public List<String> validateForPublish(Map<String, Object> definition) {
        List<String> errors = new ArrayList<>();
        if (definition == null) {
            errors.add("Workflow definition is required");
            return errors;
        }
        if (!OrinWorkflowDslNormalizer.VERSION.equals(definition.get("version"))) {
            errors.add("Workflow definition must use ORIN Workflow DSL v1");
        }
        if (!OrinWorkflowDslNormalizer.KIND.equals(definition.get("kind"))) {
            errors.add("Workflow definition kind must be workflow");
        }

        Object rawGraph = definition.get("graph");
        if (!(rawGraph instanceof Map<?, ?> graph)) {
            errors.add("Workflow graph is required");
            return errors;
        }
        Object rawNodes = graph.get("nodes");
        if (!(rawNodes instanceof List<?> nodeList) || nodeList.isEmpty()) {
            errors.add("Workflow graph must contain nodes");
            return errors;
        }

        Set<String> nodeIds = new HashSet<>();
        Map<String, String> nodeTypesById = new HashMap<>();
        boolean hasStart = false;
        boolean hasEnd = false;
        boolean hasAnswer = false;
        boolean hasBusinessNode = false;
        for (Object rawNode : nodeList) {
            if (!(rawNode instanceof Map<?, ?> node)) {
                errors.add("Workflow node must be an object");
                continue;
            }
            String id = stringValue(node.get("id"));
            String type = stringValue(node.get("type"));
            if (id == null || id.isBlank()) {
                errors.add("Workflow node id is required");
            } else if (!nodeIds.add(id)) {
                errors.add("Duplicate workflow node id: " + id);
            } else {
                nodeTypesById.put(id, type);
            }
            if (type == null || type.isBlank()) {
                errors.add("Workflow node type is required: " + id);
                continue;
            }
            if (!OrinWorkflowDslNormalizer.SUPPORTED_NODE_TYPES.contains(type)) {
                errors.add("Unsupported workflow node type: " + type);
            }
            hasStart = hasStart || "start".equals(type);
            hasEnd = hasEnd || "end".equals(type);
            hasAnswer = hasAnswer || "answer".equals(type);
            hasBusinessNode = hasBusinessNode || (!"start".equals(type) && !"end".equals(type) && !"answer".equals(type));
        }

        if (!hasStart) {
            errors.add("Workflow must contain a start node");
        }
        if (!hasEnd && !hasAnswer) {
            errors.add("Workflow must contain an end or answer node");
        }
        if (!hasBusinessNode) {
            errors.add("Workflow must contain at least one business node");
        }

        validateTerminalOutputs(nodeList, nodeIds, errors);

        Object rawEdges = graph.get("edges");
        if (rawEdges instanceof List<?> edgeList) {
            for (Object rawEdge : edgeList) {
                if (!(rawEdge instanceof Map<?, ?> edge)) {
                    errors.add("Workflow edge must be an object");
                    continue;
                }
                String source = stringValue(edge.get("source"));
                String target = stringValue(edge.get("target"));
                if (source == null || source.isBlank() || target == null || target.isBlank()) {
                    errors.add("Workflow edge source and target are required");
                    continue;
                }
                if (!nodeIds.contains(source)) {
                    errors.add("Workflow edge source not found: " + source);
                } else if ("end".equals(nodeTypesById.get(source)) || "answer".equals(nodeTypesById.get(source))) {
                    errors.add("Terminal node cannot connect to downstream node: " + source);
                }
                if (!nodeIds.contains(target)) {
                    errors.add("Workflow edge target not found: " + target);
                } else if ("start".equals(nodeTypesById.get(target))) {
                    errors.add("Start node cannot have upstream node: " + target);
                }
            }
        }

        Map<String, Object> metadata = definition.get("metadata") instanceof Map<?, ?> rawMetadata
                ? (Map<String, Object>) rawMetadata
                : Map.of();
        Object compatibility = metadata.get("compatibility");
        if (compatibility instanceof Map<?, ?> compatibilityMap) {
            Object unsupportedNodes = compatibilityMap.get("unsupportedNodes");
            if (unsupportedNodes instanceof List<?> list && !list.isEmpty()) {
                errors.add("Workflow has unsupported compatibility nodes");
            }
        }

        return errors;
    }

    public void validateForPublishOrThrow(Map<String, Object> definition) {
        List<String> errors = validateForPublish(definition);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Workflow publish validation failed: " + String.join("; ", errors));
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private void validateTerminalOutputs(List<?> nodeList, Set<String> nodeIds, List<String> errors) {
        for (Object rawNode : nodeList) {
            if (!(rawNode instanceof Map<?, ?> node)) {
                continue;
            }
            String id = stringValue(node.get("id"));
            String type = stringValue(node.get("type"));
            Map<String, Object> data = node.get("data") instanceof Map<?, ?> rawData
                    ? (Map<String, Object>) rawData
                    : Map.of();
            if ("end".equals(type)) {
                Object rawOutputs = data.get("outputs");
                if (!(rawOutputs instanceof List<?> outputs) || outputs.isEmpty()) {
                    errors.add("End node must configure at least one output mapping: " + id);
                    continue;
                }
                validateOutputMappings(id, outputs, nodeIds, errors);
            } else if ("answer".equals(type)) {
                Object expression = firstPresent(data, "answer", "text", "value", "template", "source", "sourceExpression");
                if (expression == null || String.valueOf(expression).isBlank()) {
                    errors.add("Answer node must configure reply source: " + id);
                } else {
                    validateExpressionReferences("Answer node output", id, expression, nodeIds, errors);
                }
            }
        }
    }

    private void validateOutputMappings(String nodeId, List<?> outputs, Set<String> nodeIds, List<String> errors) {
        for (Object rawOutput : outputs) {
            if (!(rawOutput instanceof Map<?, ?> output)) {
                errors.add("End node output mapping must be an object: " + nodeId);
                continue;
            }
            String name = stringValue(firstPresent(output, "name", "key", "variable"));
            Object value = firstPresent(output, "value", "expression", "source", "sourceExpression");
            if (name == null || name.isBlank()) {
                errors.add("End node output mapping name is required: " + nodeId);
            }
            if (value == null || String.valueOf(value).isBlank()) {
                errors.add("End node output mapping value is required: " + nodeId + "." + (name == null ? "<unnamed>" : name));
                continue;
            }
            validateExpressionReferences("End node output mapping " + (name == null ? "<unnamed>" : name), nodeId, value, nodeIds, errors);
        }
    }

    private void validateExpressionReferences(String label, String nodeId, Object value, Set<String> nodeIds, List<String> errors) {
        if (!(value instanceof String expression)) {
            return;
        }
        Matcher matcher = EXPRESSION_PATTERN.matcher(expression);
        while (matcher.find()) {
            String path = matcher.group(1);
            String[] parts = path.split("\\.");
            if (parts.length < 2) {
                errors.add(label + " must reference node and field, for example {{ llm_1.output }}: " + nodeId);
                continue;
            }
            String referencedNodeId = parts[0];
            if (!"inputs".equals(referencedNodeId) && !nodeIds.contains(referencedNodeId)) {
                errors.add(label + " references missing node: " + referencedNodeId);
            }
        }
    }

    private Object firstPresent(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }
}
