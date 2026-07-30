package com.adlin.orin.modules.workflow.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowGenerationServiceTest {

    @Test
    void extractsJsonFromMarkdownFenceWithoutRegex() {
        String content = """
                Here is the workflow:
                ```json
                {"nodes":[],"edges":[]}
                ```
                """;

        assertThat(WorkflowGenerationService.extractJson(content))
                .isEqualTo("{\"nodes\":[],\"edges\":[]}");
    }

    @Test
    void leavesNonJsonContentUnchanged() {
        String content = "  {\"nodes\":[]}  ";

        assertThat(WorkflowGenerationService.extractJson(content))
                .isEqualTo("{\"nodes\":[]}");
    }
}
