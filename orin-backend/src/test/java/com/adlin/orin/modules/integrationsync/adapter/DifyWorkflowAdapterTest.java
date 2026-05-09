package com.adlin.orin.modules.integrationsync.adapter;

import com.adlin.orin.modules.workflow.converter.DifyDslConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DifyWorkflowAdapterTest {

    private final DifyWorkflowAdapter adapter = new DifyWorkflowAdapter(new DifyDslConverter(), new ObjectMapper());

    @Test
    @SuppressWarnings("unchecked")
    void toWorkflowDefinition_ShouldPreserveUnsupportedNodesInCompatibilityReport() {
        Map<String, Object> rawDsl = Map.of(
                "workflow", Map.of(
                        "graph", Map.of(
                                "nodes", List.of(
                                        Map.of("id", "start", "type", "custom", "data", Map.of("type", "start")),
                                        Map.of("id", "x", "type", "custom", "data", Map.of("type", "vendor-special"))
                                ),
                                "edges", List.of()
                        )
                )
        );

        Map<String, Object> definition = adapter.toWorkflowDefinition(rawDsl, Map.of("id", "app-1", "name", "Demo", "mode", "workflow"));

        assertEquals("orin.workflow.v1", definition.get("version"));
        Map<String, Object> metadata = (Map<String, Object>) definition.get("metadata");
        Map<String, Object> report = (Map<String, Object>) metadata.get("compatibility");
        assertEquals("PARTIAL", report.get("level"));
        assertEquals("BLOCKED", report.get("publishability"));
        List<Map<String, Object>> unsupported = (List<Map<String, Object>>) report.get("unsupportedNodes");
        assertEquals(1, unsupported.size());
        assertEquals("vendor_special", unsupported.get(0).get("type"));
    }
}
