package com.adlin.orin.modules.workflow.engine;

import com.adlin.orin.modules.workflow.engine.handler.NodeHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class GraphExecutorValidationTest {

    private GraphExecutor graphExecutor;

    @BeforeEach
    void setUp() {
        NodeHandler noopHandler = (nodeData, context) -> null;
        graphExecutor = new GraphExecutor(Map.of(
                "startNodeHandler", noopHandler,
                "endNodeHandler", noopHandler,
                "llmNodeHandler", noopHandler), null, null, null);
    }

    @Test
    void validateGraphDefinition_ShouldRejectMissingEdgeTarget() {
        Map<String, Object> graph = Map.of(
                "nodes", List.of(Map.of("id", "start", "type", "start")),
                "edges", List.of(Map.of("source", "start", "target", "missing")));

        assertThatThrownBy(() -> graphExecutor.validateGraphDefinition(graph))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target not found");
    }

    @Test
    void validateGraphDefinition_ShouldRejectCycle() {
        Map<String, Object> graph = Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "b", "type", "llm"),
                        Map.of("id", "end", "type", "end")),
                "edges", List.of(
                        Map.of("source", "start", "target", "b"),
                        Map.of("source", "b", "target", "end"),
                        Map.of("source", "end", "target", "b")));

        assertThatThrownBy(() -> graphExecutor.validateGraphDefinition(graph))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void validateGraphDefinition_ShouldRejectDuplicateNode() {
        Map<String, Object> graph = Map.of(
                "nodes", List.of(
                        Map.of("id", "a", "type", "llm"),
                        Map.of("id", "a", "type", "llm")),
                "edges", List.of());

        assertThatThrownBy(() -> graphExecutor.validateGraphDefinition(graph))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void getSupportedNodeTypes_ShouldReturnExecutableNodeTypes() {
        assertThat(graphExecutor.getSupportedNodeTypes())
                .contains("start", "end", "llm")
                .doesNotContain("answer", "http_request");
    }
}
