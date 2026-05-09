package com.adlin.orin.modules.workflow.converter;

import com.adlin.orin.modules.workflow.dsl.OrinWorkflowDslNormalizer;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DifyDslConverter {

    private final OrinWorkflowDslNormalizer normalizer = new OrinWorkflowDslNormalizer();

    @SuppressWarnings("unchecked")
    public Map<String, Object> convert(String yamlContent) {
        Yaml yaml = new Yaml();
        Object loaded = yaml.load(yamlContent);
        if (!(loaded instanceof Map<?, ?> rawDifyData)) {
            throw new IllegalArgumentException("Invalid Dify DSL: root object is required");
        }
        Map<String, Object> difyData = copyMap(rawDifyData);

        Map<String, Object> workflow = extractWorkflow(difyData);
        Map<String, Object> graph = extractGraph(workflow);

        Map<String, Object> app = difyData.get("app") instanceof Map<?, ?> rawApp
                ? copyMap(rawApp)
                : Map.of();

        Map<String, Object> intermediate = new LinkedHashMap<>();
        intermediate.put("metadata", Map.of(
                "source", "DIFY",
                "sourceApp", app));
        intermediate.put("graph", Map.of(
                "nodes", normalizeDifyNodes(graph.get("nodes")),
                "edges", normalizeDifyEdges(graph.get("edges"))));

        return normalizer.normalize(intermediate, "DIFY");
    }

    @SuppressWarnings("unchecked")
    public String export(Map<String, Object> orinDefinition, String name, String description, String version) {
        Map<String, Object> normalized = normalizer.normalize(orinDefinition, "ORIN");
        Map<String, Object> graph = (Map<String, Object>) normalized.get("graph");

        Map<String, Object> app = new LinkedHashMap<>();
        app.put("name", name != null ? name : "ORIN Workflow");
        app.put("description", description != null ? description : "");
        app.put("mode", "workflow");
        app.put("version", version != null ? version : "1.0");

        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("graph", Map.of(
                "nodes", toDifyNodes((List<Map<String, Object>>) graph.get("nodes")),
                "edges", toDifyEdges((List<Map<String, Object>>) graph.get("edges")),
                "viewport", Map.of("x", 0, "y", 0, "zoom", 1)));

        Map<String, Object> dsl = new LinkedHashMap<>();
        dsl.put("app", app);
        dsl.put("workflow", workflow);
        return new Yaml().dump(dsl);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractWorkflow(Map<String, Object> difyData) {
        Object workflow = difyData.get("workflow");
        if (workflow instanceof Map<?, ?> workflowMap) {
            return copyMap(workflowMap);
        }
        Object app = difyData.get("app");
        if (app instanceof Map<?, ?> appMap && appMap.get("workflow") instanceof Map<?, ?> workflowMap) {
            return copyMap(workflowMap);
        }
        if (difyData.get("graph") instanceof Map<?, ?> || difyData.get("nodes") instanceof List<?>) {
            return difyData;
        }
        throw new IllegalArgumentException("Invalid Dify DSL: workflow section not found");
    }

    private Map<String, Object> extractGraph(Map<String, Object> workflow) {
        Object graph = workflow.get("graph");
        if (graph instanceof Map<?, ?> graphMap) {
            return copyMap(graphMap);
        }
        if (workflow.get("nodes") instanceof List<?> || workflow.get("edges") instanceof List<?>) {
            return Map.of(
                    "nodes", workflow.getOrDefault("nodes", List.of()),
                    "edges", workflow.getOrDefault("edges", List.of()));
        }
        return Map.of("nodes", List.of(), "edges", List.of());
    }

    private List<Map<String, Object>> normalizeDifyNodes(Object rawNodes) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        if (!(rawNodes instanceof List<?> rawList)) {
            return nodes;
        }
        for (Object rawNode : rawList) {
            if (!(rawNode instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> difyNode = copyMap(rawMap);
            Map<String, Object> data = difyNode.get("data") instanceof Map<?, ?> rawData
                    ? copyMap(rawData)
                    : new LinkedHashMap<>();
            String rawType = stringValue(difyNode.get("type"));
            if (("custom".equals(rawType) || "custom-note".equals(rawType)) && data.get("type") != null) {
                rawType = stringValue(data.get("type"));
            }

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", difyNode.get("id"));
            node.put("type", rawType);
            node.put("position", difyNode.get("position"));
            node.put("data", data);
            nodes.add(node);
        }
        return nodes;
    }

    private List<Map<String, Object>> normalizeDifyEdges(Object rawEdges) {
        List<Map<String, Object>> edges = new ArrayList<>();
        if (!(rawEdges instanceof List<?> rawList)) {
            return edges;
        }
        for (Object rawEdge : rawList) {
            if (!(rawEdge instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> difyEdge = copyMap(rawMap);
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("id", difyEdge.get("id"));
            edge.put("source", difyEdge.get("source"));
            edge.put("target", difyEdge.get("target"));
            copyIfPresent(difyEdge, edge, "sourceHandle");
            copyIfPresent(difyEdge, edge, "targetHandle");

            Object rawData = difyEdge.get("data");
            if (rawData instanceof Map<?, ?> edgeData && edgeData.get("condition") != null) {
                edge.put("condition", edgeData.get("condition"));
            } else if (difyEdge.get("sourceHandle") != null) {
                edge.put("condition", difyEdge.get("sourceHandle"));
            }
            edges.add(edge);
        }
        return edges;
    }

    private List<Map<String, Object>> toDifyNodes(List<Map<String, Object>> nodes) {
        List<Map<String, Object>> difyNodes = new ArrayList<>();
        if (nodes == null) {
            return difyNodes;
        }
        for (Map<String, Object> node : nodes) {
            Map<String, Object> data = node.get("data") instanceof Map<?, ?> rawData
                    ? copyMap(rawData)
                    : new LinkedHashMap<>();
            String orinType = stringValue(node.get("type"));
            data.put("type", toDifyNodeType(orinType));
            data.putIfAbsent("title", node.getOrDefault("title", orinType));

            Map<String, Object> difyNode = new LinkedHashMap<>();
            difyNode.put("id", node.get("id"));
            difyNode.put("type", "custom");
            difyNode.put("position", node.getOrDefault("position", Map.of("x", 0, "y", 0)));
            difyNode.put("data", data);
            difyNodes.add(difyNode);
        }
        return difyNodes;
    }

    private List<Map<String, Object>> toDifyEdges(List<Map<String, Object>> edges) {
        List<Map<String, Object>> difyEdges = new ArrayList<>();
        if (edges == null) {
            return difyEdges;
        }
        for (Map<String, Object> edge : edges) {
            Map<String, Object> difyEdge = new LinkedHashMap<>();
            difyEdge.put("id", edge.get("id"));
            difyEdge.put("source", edge.get("source"));
            difyEdge.put("target", edge.get("target"));
            copyIfPresent(edge, difyEdge, "sourceHandle");
            copyIfPresent(edge, difyEdge, "targetHandle");
            if (edge.get("condition") != null) {
                difyEdge.put("data", Map.of("condition", edge.get("condition")));
            }
            difyEdges.add(difyEdge);
        }
        return difyEdges;
    }

    private String toDifyNodeType(String orinType) {
        return switch (orinType == null ? "" : orinType) {
            case "knowledge_retrieval" -> "knowledge-retrieval";
            case "if_else" -> "if-else";
            case "variable_assigner" -> "variable-assigner";
            case "http_request" -> "http-request";
            case "end" -> "end";
            default -> orinType;
        };
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> copyMap(Map<?, ?> rawMap) {
        Map<String, Object> copy = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }
}
