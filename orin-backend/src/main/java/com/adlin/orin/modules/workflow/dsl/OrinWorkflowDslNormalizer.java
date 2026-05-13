package com.adlin.orin.modules.workflow.dsl;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class OrinWorkflowDslNormalizer {

    public static final String VERSION = "orin.workflow.v1";
    public static final String KIND = "workflow";

    public static final Set<String> SUPPORTED_NODE_TYPES = Set.of(
            "start",
            "end",
            "answer",
            "llm",
            "agent",
            "knowledge_retrieval",
            "tool",
            "http_request",
            "if_else",
            "loop",
            "variable_assigner",
            "code");

    public Map<String, Object> normalize(Map<String, Object> definition) {
        return normalize(definition, "ORIN");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> normalize(Map<String, Object> definition, String source) {
        Map<String, Object> rawDefinition = definition != null ? definition : Map.of();
        Map<String, Object> rawGraph = extractGraph(rawDefinition);
        boolean alreadyOrin = VERSION.equals(rawDefinition.get("version"))
                && KIND.equals(rawDefinition.get("kind"))
                && rawDefinition.get("graph") instanceof Map<?, ?>;

        List<Map<String, Object>> nodes = normalizeNodes(rawGraph.get("nodes"));
        List<Map<String, Object>> edges = normalizeEdges(rawGraph.get("edges"));

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("version", VERSION);
        normalized.put("kind", KIND);

        Map<String, Object> metadata = copyMap(rawDefinition.get("metadata"));
        metadata.putIfAbsent("source", source == null || source.isBlank() ? "ORIN" : source);
        metadata.put("compatibility", buildCompatibilityReport(nodes, source, alreadyOrin));
        normalized.put("metadata", metadata);

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        normalized.put("graph", graph);
        normalized.put("inputs", copyList(rawDefinition.get("inputs")));
        normalized.put("outputs", copyList(rawDefinition.get("outputs")));
        normalized.put("variables", copyList(rawDefinition.get("variables")));

        return normalized;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractGraph(Map<String, Object> definition) {
        Object graph = definition.get("graph");
        if (graph instanceof Map<?, ?> graphMap) {
            return copyRawMap(graphMap);
        }

        Object workflow = definition.get("workflow");
        if (workflow instanceof Map<?, ?> workflowMap) {
            Object workflowGraph = workflowMap.get("graph");
            if (workflowGraph instanceof Map<?, ?> graphMap) {
                return copyRawMap(graphMap);
            }
        }

        if (definition.get("nodes") instanceof List<?> || definition.get("edges") instanceof List<?>) {
            Map<String, Object> graphMap = new LinkedHashMap<>();
            graphMap.put("nodes", definition.get("nodes"));
            graphMap.put("edges", definition.get("edges"));
            return graphMap;
        }

        return Map.of("nodes", List.of(), "edges", List.of());
    }

    private List<Map<String, Object>> normalizeNodes(Object rawNodes) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        if (!(rawNodes instanceof Collection<?> rawList)) {
            return nodes;
        }
        int index = 1;
        for (Object rawNode : rawList) {
            if (!(rawNode instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> node = copyRawMap(rawMap);
            Map<String, Object> data = copyMap(node.get("data"));

            String rawType = stringValue(node.get("type"));
            if (("custom".equals(rawType) || "custom-note".equals(rawType)) && data.get("type") != null) {
                rawType = stringValue(data.get("type"));
            }

            String type = normalizeNodeType(rawType);
            String id = stringValue(node.get("id"));
            if (id == null || id.isBlank()) {
                id = type + "-" + index;
            }

            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("id", id);
            normalized.put("type", type);
            normalized.put("title", firstNonBlank(
                    stringValue(node.get("title")),
                    stringValue(data.get("title")),
                    stringValue(data.get("label")),
                    type));
            normalized.put("position", node.getOrDefault("position", defaultPosition(index)));
            data.putIfAbsent("label", normalized.get("title"));
            if (rawType != null && !rawType.equals(type)) {
                data.put("originalType", rawType);
            }
            normalized.put("data", data);
            nodes.add(normalized);
            index++;
        }
        return nodes;
    }

    private List<Map<String, Object>> normalizeEdges(Object rawEdges) {
        List<Map<String, Object>> edges = new ArrayList<>();
        if (!(rawEdges instanceof Collection<?> rawList)) {
            return edges;
        }
        int index = 1;
        for (Object rawEdge : rawList) {
            if (!(rawEdge instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> edge = copyRawMap(rawMap);
            String source = stringValue(edge.get("source"));
            String target = stringValue(edge.get("target"));
            if (source == null || target == null) {
                continue;
            }

            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("id", firstNonBlank(stringValue(edge.get("id")), "edge-" + index));
            normalized.put("source", source);
            normalized.put("target", target);
            copyIfPresent(edge, normalized, "sourceHandle");
            copyIfPresent(edge, normalized, "targetHandle");
            copyIfPresent(edge, normalized, "condition");
            copyIfPresent(edge, normalized, "label");
            edges.add(normalized);
            index++;
        }
        return edges;
    }

    public String normalizeNodeType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "unknown";
        }
        String type = rawType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (type) {
            case "input" -> "start";
            case "skill" -> "tool";
            case "knowledge_retrieval" -> "knowledge_retrieval";
            case "if_else" -> "if_else";
            case "variable_assigner" -> "variable_assigner";
            case "http_request" -> "http_request";
            default -> type;
        };
    }

    private Map<String, Object> buildCompatibilityReport(
            List<Map<String, Object>> nodes,
            String source,
            boolean alreadyOrin) {
        Set<String> unsupportedTypes = new LinkedHashSet<>();
        List<Map<String, Object>> unsupportedNodes = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            String type = stringValue(node.get("type"));
            if (!SUPPORTED_NODE_TYPES.contains(type)) {
                unsupportedTypes.add(type);
                unsupportedNodes.add(Map.of(
                        "id", node.get("id"),
                        "type", type,
                        "title", node.getOrDefault("title", type)));
            }
        }

        List<String> warnings = new ArrayList<>();
        if (!alreadyOrin) {
            warnings.add("Definition normalized to ORIN Workflow DSL v1.");
        }
        if (source != null && "DIFY".equalsIgnoreCase(source)) {
            warnings.add("Imported from Dify DSL. Dify is treated as compatibility format only.");
        }

        String publishability = unsupportedNodes.isEmpty() ? "PUBLISHABLE" : "BLOCKED";
        String level = unsupportedNodes.isEmpty()
                ? "FULL"
                : unsupportedNodes.size() == nodes.size() ? "BLOCKED" : "PARTIAL";

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("format", "DIFY".equalsIgnoreCase(source) ? "DIFY" : "ORIN");
        report.put("level", level);
        report.put("publishability", publishability);
        report.put("unsupportedTypes", new ArrayList<>(unsupportedTypes));
        report.put("unsupportedNodes", unsupportedNodes);
        report.put("warnings", warnings);
        return report;
    }

    private Map<String, Object> defaultPosition(int index) {
        return Map.of("x", 120 + (index - 1) * 220, "y", 160);
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<Object> copyList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(collection);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copyMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new LinkedHashMap<>();
        }
        return copyRawMap(rawMap);
    }

    private Map<String, Object> copyRawMap(Map<?, ?> rawMap) {
        Map<String, Object> copy = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }
}
