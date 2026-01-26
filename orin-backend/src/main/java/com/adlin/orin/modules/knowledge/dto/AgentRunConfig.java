package com.adlin.orin.modules.knowledge.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Configuration object containing all necessary context for an Agent execution.
 * Produced by AgentContextAssembler.
 */
@Data
@Builder
public class AgentRunConfig {

    // 1. Who is running
    private String agentId;
    private String modelName; // e.g. "gpt-4"

    // 2. The "Mind" (Meta Knowledge)
    private String systemPrompt; // Assembled from Role + Instructions

    // 3. The "Memory" (History)
    private List<Map<String, Object>> shortTermHistory; // Last N messages

    // 4. The "Knowledge" (RAG / Structured)
    private List<String> relevantContext; // Text chunks from UnstructuredService
    private String structuredDataContext; // SQL results or Table Schema from StructuredService

    // 5. The "Skills" (Tools)
    private List<Map<String, Object>> availableTools; // Definitions from ProceduralService
}
