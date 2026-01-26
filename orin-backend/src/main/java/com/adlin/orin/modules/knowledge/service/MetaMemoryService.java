package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.entity.meta.PromptTemplate;
import java.util.List;
import java.util.Map;

/**
 * Service interface for Meta Knowledge (Prompts) and Memory (Context).
 * Manages the "Brains" and "Personality" of the Agent.
 */
public interface MetaMemoryService {

    /**
     * Retrieve the active System Prompt for an Agent.
     * Allows combining multiple templates (Role + Instructions + Constraints).
     *
     * @param agentId The Agent ID
     * @return The fully assembled system prompt string
     */
    String getSystemPrompt(String agentId);

    /**
     * Save or Update a prompt template.
     */
    PromptTemplate savePromptTemplate(PromptTemplate template);

    /**
     * Retrieve short-term conversation history for context window.
     * Likely backed by Redis.
     *
     * @param agentId   The Agent ID
     * @param sessionId Current session ID
     * @param limit     Number of messages to retrieve
     * @return List of message objects
     */
    List<Map<String, Object>> getShortTermMemory(String agentId, String sessionId, int limit);

    /**
     * Retrieve relevant long-term memories (facts/preferences) based on query.
     * Backed by Vector DB or specific SQL tables.
     *
     * @param agentId The Agent ID
     * @param query   Current user query context
     * @return List of memory snippets
     */
    List<String> getLongTermMemory(String agentId, String query);

    /**
     * Store a new long-term memory fact.
     */
    void saveLongTermMemory(String agentId, String key, String value);
}
