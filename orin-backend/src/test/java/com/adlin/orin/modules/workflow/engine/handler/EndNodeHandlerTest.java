package com.adlin.orin.modules.workflow.engine.handler;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EndNodeHandlerTest {

    @Test
    void execute_ShouldReturnContextSnapshot() {
        EndNodeHandler handler = new EndNodeHandler();
        Map<String, Object> context = new HashMap<>();
        context.put("query", "hello");

        NodeExecutionResult result = handler.execute(Map.of(), context);

        assertThat(result.getOutputs()).isEqualTo(context);
        assertThat(result.getOutputs()).isNotSameAs(context);

        context.put("end_1", result.getOutputs());

        assertThat(result.getOutputs()).doesNotContainKey("end_1");
    }
}
