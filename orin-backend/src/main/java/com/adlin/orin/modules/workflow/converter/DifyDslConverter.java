package com.adlin.orin.modules.workflow.converter;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/**
 * Converter for Dify DSL to ORIN Workflow Definition
 */
@Component
public class DifyDslConverter {

    @SuppressWarnings("unchecked")
    public Map<String, Object> convert(String yamlContent) {
        Yaml yaml = new Yaml();
        Map<String, Object> difyData = yaml.load(yamlContent);

        Map<String, Object> workflow = (Map<String, Object>) difyData.get("workflow");
        if (workflow == null) {
            // Try to see if the root is within 'app'
            if (difyData.containsKey("app") && difyData.get("app") instanceof Map) {
                Map<String, Object> app = (Map<String, Object>) difyData.get("app");
                if (app.containsKey("workflow")) {
                    workflow = (Map<String, Object>) app.get("workflow");
                }
            }
        }

        // Handle case where root IS the workflow
        if (workflow == null && difyData.containsKey("graph")) {
            workflow = difyData;
        }

        if (workflow == null) {
            // If we still can't find it, but maybe "nodes" are at top level
            if (difyData.containsKey("nodes")) {
                workflow = difyData;
            } else {
                throw new IllegalArgumentException("Invalid Dify DSL: 'workflow' section not found");
            }
        }

        // Extract nodes and edges. Dify often puts them under 'graph'.
        List<Map<String, Object>> difyNodes = null;
        List<Map<String, Object>> difyEdges = null;

        if (workflow.containsKey("graph")) {
            Map<String, Object> graph = (Map<String, Object>) workflow.get("graph");
            difyNodes = (List<Map<String, Object>>) graph.get("nodes");
            difyEdges = (List<Map<String, Object>>) graph.get("edges");
        } else {
            difyNodes = (List<Map<String, Object>>) workflow.get("nodes");
            difyEdges = (List<Map<String, Object>>) workflow.get("edges");
        }

        if (difyNodes == null) {
            difyNodes = new ArrayList<>();
        }
        if (difyEdges == null) {
            difyEdges = new ArrayList<>();
        }

        List<Map<String, Object>> orinNodes = new ArrayList<>();
        List<Map<String, Object>> orinEdges = new ArrayList<>();

        // Map Nodes
        for (Map<String, Object> difyNode : difyNodes) {
            Map<String, Object> orinNode = new HashMap<>();
            String id = (String) difyNode.get("id");

            // Extract type: Dify 'custom' type nodes store real type in data.type
            String type = (String) difyNode.get("type");
            Map<String, Object> data = (Map<String, Object>) difyNode.get("data");
            if (data == null)
                data = new HashMap<>();

            // Handle "custom" type nodes which are common in Dify DSL
            if ("custom".equals(type) || "custom-note".equals(type)) {
                if (data.containsKey("type")) {
                    String dataType = (String) data.get("type");
                    if (dataType != null && !dataType.isEmpty()) {
                        type = dataType;
                    }
                }
            }

            // Handle cosmetic nodes (Notes)
            if ("custom-note".equals(difyNode.get("type"))) {
                orinNode.put("id", id);
                orinNode.put("type", "note");
                orinNode.put("position", difyNode.get("position"));
                orinNode.put("data", data);
                orinNodes.add(orinNode);
                continue;
            }

            orinNode.put("id", id);
            orinNode.put("type", mapNodeType(type));
            orinNode.put("position", difyNode.get("position")); // {x, y}

            // Add Dify specific config to data so we can use it
            orinNode.put("data", data);

            orinNodes.add(orinNode);
        }

        // Map Edges
        for (Map<String, Object> difyEdge : difyEdges) {
            Map<String, Object> orinEdge = new HashMap<>();
            orinEdge.put("id", difyEdge.get("id"));
            orinEdge.put("source", difyEdge.get("source"));
            orinEdge.put("target", difyEdge.get("target"));

            // Condition handling
            // Dify edges often have sourceHandle which might imply condition path
            // (true/false)
            if (difyEdge.containsKey("sourceHandle")) {
                String sourceHandle = String.valueOf(difyEdge.get("sourceHandle"));
                if ("true".equalsIgnoreCase(sourceHandle)) {
                    orinEdge.put("condition", "true");
                } else if ("false".equalsIgnoreCase(sourceHandle)) {
                    orinEdge.put("condition", "false");
                }
            }

            // Also check data.condition just in case
            if (difyEdge.containsKey("data")) {
                Map<String, Object> edgeData = (Map<String, Object>) difyEdge.get("data");
                if (edgeData != null && edgeData.containsKey("condition")) {
                    orinEdge.put("condition", edgeData.get("condition"));
                }
            }

            orinEdges.add(orinEdge);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", orinNodes);
        result.put("edges", orinEdges);

        return result;
    }

    private String mapNodeType(String difyType) {
        if (difyType == null)
            return "unknown";
        switch (difyType) {
            case "start":
                return "start";
            case "end":
                return "end";
            case "answer":
                return "answer";
            case "llm":
                return "llm";
            case "knowledge-retrieval":
                return "knowledge_retrieval";
            case "question-classifier":
                return "question_classifier";
            case "if-else":
                return "if_else";
            case "code":
                return "code";
            case "template-transform":
                return "template_transform";
            case "http-request":
                return "http_request";
            case "tool":
                return "tool";
            case "variable-aggregator":
                return "variable_aggregator";
            case "iteration":
                return "iteration";
            case "parameter-extractor":
                return "parameter_extractor";
            case "document-extractor":
                return "document_extractor";
            default:
                // Try to preserve original type for unknown ones, normalized to snake_case if
                // possible
                return difyType.replace("-", "_");
        }
    }
}
