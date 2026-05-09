package com.adlin.orin.modules.integrationsync.adapter;

import com.adlin.orin.modules.workflow.converter.DifyDslConverter;
import com.adlin.orin.modules.workflow.dsl.OrinWorkflowDslNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

@Component
public class DifyWorkflowAdapter {

    private final DifyDslConverter difyDslConverter;
    private final OrinWorkflowDslNormalizer workflowDslNormalizer;
    private final ObjectMapper objectMapper;

    @Autowired
    public DifyWorkflowAdapter(
            DifyDslConverter difyDslConverter,
            OrinWorkflowDslNormalizer workflowDslNormalizer,
            ObjectMapper objectMapper) {
        this.difyDslConverter = difyDslConverter;
        this.workflowDslNormalizer = workflowDslNormalizer;
        this.objectMapper = objectMapper;
    }

    public DifyWorkflowAdapter(DifyDslConverter difyDslConverter, ObjectMapper objectMapper) {
        this(difyDslConverter, new OrinWorkflowDslNormalizer(), objectMapper);
    }

    public Map<String, Object> toWorkflowDefinition(Map<String, Object> rawDsl, Map<String, Object> appMetadata) {
        String yamlContent = new Yaml().dump(rawDsl == null ? Map.of() : rawDsl);
        Map<String, Object> definition = new LinkedHashMap<>(difyDslConverter.convert(yamlContent));
        Map<String, Object> metadata = definition.get("metadata") instanceof Map<?, ?> rawMetadata
                ? new LinkedHashMap<>((Map<String, Object>) rawMetadata)
                : new LinkedHashMap<>();
        metadata.put("source", "DIFY");
        metadata.put("externalAppId", value(appMetadata, "id"));
        metadata.put("mode", value(appMetadata, "mode"));
        metadata.put("name", value(appMetadata, "name"));
        definition.put("metadata", metadata);
        return definition;
    }

    public Map<String, Object> toDifyExportSnapshot(Map<String, Object> workflowDefinition, String workflowName) {
        Map<String, Object> definition = workflowDslNormalizer.normalize(workflowDefinition, "ORIN");

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("difyYaml", difyDslConverter.export(definition, workflowName, "", "1.0"));
        snapshot.put("kind", "dify-compatible-export");
        snapshot.put("compatibilityReport", compatibilityReport(definition));
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> compatibilityReport(Map<String, Object> workflowDefinition) {
        Map<String, Object> definition = workflowDslNormalizer.normalize(workflowDefinition, "ORIN");
        Object metadata = definition.get("metadata");
        if (metadata instanceof Map<?, ?> metadataMap
                && metadataMap.get("compatibility") instanceof Map<?, ?> compatibilityMap) {
            return new LinkedHashMap<>((Map<String, Object>) compatibilityMap);
        }
        return Map.of("level", "FULL", "unsupportedNodes", List.of());
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
