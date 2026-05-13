package com.adlin.orin.modules.workflow.dsl;

import com.adlin.orin.modules.workflow.converter.DifyDslConverter;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrinWorkflowDslNormalizerTest {

    private final OrinWorkflowDslNormalizer normalizer = new OrinWorkflowDslNormalizer();
    private final OrinWorkflowDslValidator validator = new OrinWorkflowDslValidator();
    private final DifyDslConverter difyDslConverter = new DifyDslConverter();

    @Test
    void normalizesRootNodesAndEdgesToOrinDslV1() {
        Map<String, Object> normalized = normalizer.normalize(Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "agent", "type", "agent"),
                        Map.of("id", "end", "type", "end", "data", Map.of(
                                "outputs", List.of(Map.of("name", "answer", "value", "{{ agent.output }}"))))),
                "edges", List.of(
                        Map.of("source", "start", "target", "agent"),
                        Map.of("source", "agent", "target", "end"))));

        assertThat(normalized.get("version")).isEqualTo(OrinWorkflowDslNormalizer.VERSION);
        assertThat(normalized.get("kind")).isEqualTo(OrinWorkflowDslNormalizer.KIND);
        assertThat(((Map<?, ?>) normalized.get("graph")).get("nodes")).asList().hasSize(3);
        assertThat(validator.validateForPublish(normalized)).isEmpty();
    }

    @Test
    void reportsUnsupportedDifyNodesAsBlockedCompatibility() {
        String yaml = """
                app:
                  name: imported
                  mode: workflow
                workflow:
                  graph:
                    nodes:
                      - id: start
                        type: custom
                        data:
                          type: start
                      - id: classifier
                        type: custom
                        data:
                          type: question-classifier
                      - id: end
                        type: custom
                        data:
                          type: end
                    edges:
                      - id: e1
                        source: start
                        target: classifier
                      - id: e2
                        source: classifier
                        target: end
                """;

        Map<String, Object> normalized = difyDslConverter.convert(yaml);
        Map<?, ?> metadata = (Map<?, ?>) normalized.get("metadata");
        Map<?, ?> compatibility = (Map<?, ?>) metadata.get("compatibility");

        assertThat(normalized.get("version")).isEqualTo(OrinWorkflowDslNormalizer.VERSION);
        assertThat(metadata.get("source")).isEqualTo("DIFY");
        assertThat(compatibility.get("level")).isEqualTo("PARTIAL");
        assertThat(compatibility.get("publishability")).isEqualTo("BLOCKED");
        assertThat(validator.validateForPublish(normalized)).contains("Workflow has unsupported compatibility nodes");
    }

    @Test
    void exportsOrinDslAsDifyCompatibleYaml() {
        Map<String, Object> normalized = normalizer.normalize(Map.of(
                "graph", Map.of(
                        "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "code", "type", "code"),
                                Map.of("id", "end", "type", "end", "data", Map.of(
                                        "outputs", List.of(Map.of("name", "answer", "value", "{{ code.result }}"))))),
                        "edges", List.of(
                                Map.of("source", "start", "target", "code"),
                                Map.of("source", "code", "target", "end")))));

        String yaml = difyDslConverter.export(normalized, "Demo", "", "1.0");
        Map<?, ?> exported = new Yaml().load(yaml);

        assertThat(((Map<?, ?>) exported.get("app")).get("name")).isEqualTo("Demo");
        Map<?, ?> workflow = (Map<?, ?>) exported.get("workflow");
        assertThat(((Map<?, ?>) workflow.get("graph")).get("nodes")).asList().hasSize(3);
    }

    @Test
    void answerNodeRemainsAnswerAfterNormalize() {
        Map<String, Object> normalized = normalizer.normalize(Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "llm", "type", "llm"),
                        Map.of("id", "answer", "type", "answer", "data", Map.of("answer", "{{ llm.text }}"))),
                "edges", List.of(
                        Map.of("source", "start", "target", "llm"),
                        Map.of("source", "llm", "target", "answer"))));

        List<?> nodes = (List<?>) ((Map<?, ?>) normalized.get("graph")).get("nodes");

        assertThat(nodes.stream().map(node -> String.valueOf(((Map<?, ?>) node).get("type"))).toList())
                .containsExactly("start", "llm", "answer");
        assertThat(validator.validateForPublish(normalized)).isEmpty();
    }

    @Test
    void loopNodeIsPublishableInOrinDsl() {
        Map<String, Object> normalized = normalizer.normalize(Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "loop", "type", "loop", "data", Map.of("maxIterations", 3)),
                        Map.of("id", "end", "type", "end", "data", Map.of(
                                "outputs", List.of(Map.of("name", "iterations", "value", "{{ loop.totalIterations }}"))))),
                "edges", List.of(
                        Map.of("source", "start", "target", "loop"),
                        Map.of("source", "loop", "target", "end"))));

        Map<?, ?> metadata = (Map<?, ?>) normalized.get("metadata");
        Map<?, ?> compatibility = (Map<?, ?>) metadata.get("compatibility");

        assertThat(compatibility.get("unsupportedTypes")).asList().doesNotContain("loop");
        assertThat(validator.validateForPublish(normalized)).isEmpty();
    }

    @Test
    void publishValidationFailsWhenEndOutputIsMissingOrReferencesMissingNode() {
        Map<String, Object> missingOutput = normalizer.normalize(Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "llm", "type", "llm"),
                        Map.of("id", "end", "type", "end")),
                "edges", List.of(
                        Map.of("source", "start", "target", "llm"),
                        Map.of("source", "llm", "target", "end"))));

        assertThat(validator.validateForPublish(missingOutput))
                .contains("End node must configure at least one output mapping: end");

        Map<String, Object> badReference = normalizer.normalize(Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "llm", "type", "llm"),
                        Map.of("id", "end", "type", "end", "data", Map.of(
                                "outputs", List.of(Map.of("name", "answer", "value", "{{ missing.text }}"))))),
                "edges", List.of(
                        Map.of("source", "start", "target", "llm"),
                        Map.of("source", "llm", "target", "end"))));

        assertThat(validator.validateForPublish(badReference))
                .contains("End node output mapping answer references missing node: missing");
    }

    @Test
    void publishValidationTreatsAnswerAsTerminalNode() {
        Map<String, Object> downstreamAnswer = normalizer.normalize(Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "llm", "type", "llm"),
                        Map.of("id", "answer", "type", "answer", "data", Map.of("answer", "{{ llm.text }}")),
                        Map.of("id", "code", "type", "code")),
                "edges", List.of(
                        Map.of("source", "start", "target", "llm"),
                        Map.of("source", "llm", "target", "answer"),
                        Map.of("source", "answer", "target", "code"))));

        assertThat(validator.validateForPublish(downstreamAnswer))
                .contains("Terminal node cannot connect to downstream node: answer");
    }

    @Test
    void publishValidationRequiresAnswerReplySource() {
        Map<String, Object> missingReplySource = normalizer.normalize(Map.of(
                "nodes", List.of(
                        Map.of("id", "start", "type", "start"),
                        Map.of("id", "llm", "type", "llm"),
                        Map.of("id", "answer", "type", "answer")),
                "edges", List.of(
                        Map.of("source", "start", "target", "llm"),
                        Map.of("source", "llm", "target", "answer"))));

        assertThat(validator.validateForPublish(missingReplySource))
                .contains("Answer node must configure reply source: answer");
    }
}
