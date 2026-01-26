package com.adlin.orin.modules.knowledge.component;

import com.adlin.orin.modules.knowledge.dto.AgentRunConfig;
import com.adlin.orin.modules.knowledge.service.MetaMemoryService;
import com.adlin.orin.modules.knowledge.service.StructuredService;
import com.adlin.orin.modules.knowledge.service.UnstructuredService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Orchestrator that assembles the "Brain" of the Agent for a specific request.
 * Solves the problem of "Agent calling knowledge is too complex".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentContextAssembler {

    private final UnstructuredService unstructuredService;
    private final StructuredService structuredService;
    private final MetaMemoryService metaMemoryService;

    /**
     * Unified Entry Point: Assemble context for an Agent run.
     * 
     * @param agentId   The Agent ID
     * @param userQuery The latest user input (used for RAG & routing)
     * @param sessionId Current session ID (for memory retrieval)
     * @return Fully populated AgentRunConfig
     */
    public AgentRunConfig assemble(String agentId, String userQuery, String sessionId) {
        log.info("Assembling context for Agent: {}", agentId);

        // 1. Silent Assembly: Meta Knowledge & Memory
        // Automatically load System Prompt and Short-term Memory
        String systemPrompt = metaMemoryService.getSystemPrompt(agentId);
        List<Map<String, Object>> history = metaMemoryService.getShortTermMemory(agentId, sessionId, 10);

        // 2. Semantic Routing (Simple Heuristics for now)
        // TODO: Replace with specialized Router Model or classifier
        boolean isDataQuery = isStructuredDataQuery(userQuery);

        List<String> textContext = new ArrayList<>();
        String dataContext = null;

        if (isDataQuery) {
            // Call Structured Service
            // For now, we might just get the schema or try to generate SQL
            // In a real flow, we might generate SQL here or pass the tool to the Agent
            dataContext = "Available Tables: " + structuredService.getDatabaseSchema(agentId).toString();
        } else {
            // Default to RAG for general queries
            textContext = unstructuredService.retrieveContext(agentId, userQuery, 3);
        }

        // 3. Skill Assembly
        // For now return empty or simple predefined skills.
        // In future usage, ProceduralService will return definitions.
        List<Map<String, Object>> tools = new ArrayList<>();
        // tools.addAll(proceduralService.getAllSkills(agentId)); // method to be added
        // to interface if needed

        // 4. Final Encapsulation
        return AgentRunConfig.builder()
                .agentId(agentId)
                .systemPrompt(systemPrompt)
                .shortTermHistory(history)
                .relevantContext(textContext)
                .structuredDataContext(dataContext)
                .availableTools(tools)
                .build();
    }

    /**
     * Simple instruction/keyword based classifier.
     */
    private boolean isStructuredDataQuery(String query) {
        String q = query.toLowerCase();
        return q.contains("count") || q.contains("average") || q.contains("sum")
                || q.contains("statistics") || q.contains("table") || q.contains("chart");
    }
}
