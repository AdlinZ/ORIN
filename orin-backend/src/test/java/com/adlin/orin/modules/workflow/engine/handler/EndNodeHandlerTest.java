package com.adlin.orin.modules.workflow.engine.handler;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EndNodeHandlerTest {

    @Test
    void execute_ShouldResolveOutputMappings() {
        EndNodeHandler handler = new EndNodeHandler();
        Map<String, Object> context = new HashMap<>();
        context.put("llm_1", Map.of("output", "hello"));

        NodeExecutionResult result = handler.execute(Map.of(
                "outputs", java.util.List.of(Map.of("name", "answer", "value", "{{ llm_1.output }}"))), context);

        assertThat(result.getOutputs()).containsEntry("answer", "hello");
        assertThat(result.getOutputs()).doesNotContainKey("llm_1");
    }

    @Test
    void answerNode_ShouldReturnAnswerOutput() {
        AnswerNodeHandler handler = new AnswerNodeHandler();
        Map<String, Object> context = new HashMap<>();
        context.put("llm_1", Map.of("text", "chat answer"));

        NodeExecutionResult result = handler.execute(Map.of("answer", "{{ llm_1.text }}"), context);

        assertThat(result.getOutputs()).containsEntry("answer", "chat answer");
    }
}
