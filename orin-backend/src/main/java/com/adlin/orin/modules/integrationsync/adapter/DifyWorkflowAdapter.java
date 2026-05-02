package com.adlin.orin.modules.integrationsync.adapter;

import com.adlin.orin.modules.workflow.converter.DifyDslConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

@Component
@RequiredArgsConstructor
public class DifyWorkflowAdapter {

    private static final Set<String> SUPPORTED_NODE_TYPES = Set.of(
            "start",
            "end",
            "answer",
            "llm",
            "knowledge_retrieval",
            "question_classifier",
            "if_else",
            "code",
            "template_transform",
            "http_request",
            "tool",
            "variable_aggregator",
            "iteration",
            "parameter_extractor",
            "document_extractor",
            "note"
    );

    private final DifyDslConverter difyDslConverter;
    private final ObjectMapper objectMapper;

    public Map<String, Object> toWorkflowDefinition(Map<String, Object> rawDsl, Map<String, Object> appMetadata) {
        String yamlContent = new Yaml().dump(rawDsl == null ? Map.of() : rawDsl);
        Map<String, Object> definition = new LinkedHashMap<>(difyDslConverter.convert(yamlContent));
        definition.put("metadata", Map.of(
                "source", "DIFY",
                "externalAppId", value(appMetadata, "id"),
                "mode", value(appMetadata, "mode"),
                "name", value(appMetadata, "name")
        ));
        definition.put("compatibilityReport", compatibilityReport(definition));
        return definition;
    }

    public Map<String, Object> toDifyExportSnapshot(Map<String, Object> workflowDefinition, String workflowName) {
        Map<String, Object> definition = workflowDefinition == null ? Map.of() : workflowDefinition;
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", definition.getOrDefault("nodes", List.of()));
        graph.put("edges", definition.getOrDefault("edges", List.of()));

        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("graph", graph);
        workflow.put("features", Map.of());

        Map<String, Object> app = new LinkedHashMap<>();
        app.put("name", workflowName == null || workflowName.isBlank() ? "ORIN Workflow" : workflowName);
        app.put("mode", "workflow");
        app.put("workflow", workflow);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("app", app);
        snapshot.put("kind", "dify-compatible-export");
        snapshot.put("compatibilityReport", compatibilityReport(definition));
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> compatibilityReport(Map<String, Object> workflowDefinition) {
        List<Map<String, Object>> unsupported = new ArrayList<>();
        Object nodesObject = workflowDefinition == null ? null : workflowDefinition.get("nodes");
        if (nodesObject instanceof List<?> nodes) {
            for (Object nodeObject : nodes) {
                if (nodeObject instanceof Map<?, ?> node) {
                    Object typeValue = node.get("type");
                    String type = String.valueOf(typeValue == null ? "unknown" : typeValue);
                    if (!SUPPORTED_NODE_TYPES.contains(type)) {
                        Map<String, Object> unsupportedNode = new LinkedHashMap<>();
                        unsupportedNode.put("id", String.valueOf(node.get("id")));
                        unsupportedNode.put("type", type);
                        unsupportedNode.put("data", node.get("data"));
                        unsupported.add(unsupportedNode);
                    }
                }
            }
        }
        return Map.of(
                "partial", !unsupported.isEmpty(),
                "unsupportedNodes", unsupported
        );
    }

    public String compatibilityMessage(Map<String, Object> workflowDefinition) {
        try {
            Map<String, Object> report = compatibilityReport(workflowDefinition);
            Object unsupported = report.get("unsupportedNodes");
            int count = unsupported instanceof List<?> list ? list.size() : 0;
            if (count == 0) {
                return "Dify workflow DSL converted to ORIN workflow definition";
            }
            return "Dify workflow converted with " + count + " unsupported node(s) preserved in compatibilityReport";
        } catch (Exception e) {
            return "Dify workflow converted with compatibility report unavailable: " + e.getMessage();
        }
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private Object value(Map<String, Object> map, String key) {
        return map == null ? null : map.get(key);
    }
}
