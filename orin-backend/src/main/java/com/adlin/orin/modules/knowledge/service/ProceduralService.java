package com.adlin.orin.modules.knowledge.service;

import java.util.Map;

/**
 * Service interface for Procedural Knowledge (Workflows / Skills).
 * Integrates with WorkflowService to register DSLs as callable tools.
 */
public interface ProceduralService {

    /**
     * Register a workflow definition as a callable skill for an Agent.
     *
     * @param agentId     The Agent ID
     * @param workflowDsl The JSON/YAML definition of the workflow
     * @param triggerName The name used to call this tool (e.g., "search_internet")
     */
    void registerSkill(String agentId, String workflowDsl, String triggerName);

    /**
     * Trigger a registered workflow skill.
     *
     * @param agentId     The Agent ID context
     * @param triggerName The skill name to trigger
     * @param inputParams Input parameters for the workflow
     * @return Execution result
     */
    Map<String, Object> triggerSkill(String agentId, String triggerName, Map<String, Object> inputParams);

    /**
     * Get all skills registered for an Agent.
     */
    java.util.List<com.adlin.orin.modules.knowledge.entity.KnowledgeSkill> getAgentSkills(String agentId);

    /**
     * Get the tool definition (JSON Schema) for a specific skill.
     * Used to provide tool info to the LLM.
     */
    Map<String, Object> getSkillDefinition(String agentId, String triggerName);
}
