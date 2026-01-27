package com.adlin.orin.modules.knowledge.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight Workflow Engine
 * Executes the JSON DSL defined in the frontend.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeWorkflowEngine {

    private final ObjectMapper objectMapper;

    // Use lazy injection to avoid circular dependency if SkillService uses
    // WorkflowEngine
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private com.adlin.orin.modules.skill.service.SkillService skillService;

    // TODO: Inject AgentService or RouterService to execute actual agent calls
    // private final RouterService routerService;

    public Map<String, Object> execute(String dslJson, Map<String, Object> initialInput) {
        log.info("Starting workflow execution. Input: {}", initialInput);

        Map<String, Object> context = new HashMap<>(initialInput);
        Map<String, Object> stepOutputs = new HashMap<>();

        try {
            JsonNode root = objectMapper.readTree(dslJson);
            JsonNode steps = root.get("steps");

            if (steps == null || !steps.isArray()) {
                throw new RuntimeException("Invalid DSL: 'steps' array missing");
            }

            for (JsonNode step : steps) {
                String stepId = step.get("id").asText();
                String stepName = step.get("name").asText();
                String type = step.get("type").asText();

                log.info("Executing Step [{}]: {} ({})", stepId, stepName, type);

                // 1. Resolve Inputs
                Map<String, Object> stepInput = resolveInputs(step.get("inputs"), context, stepOutputs);

                // 2. Execute Logic
                Object result = null;
                switch (type) {
                    case "AGENT":
                        String agentId = step.has("agentId") ? step.get("agentId").asText() : null;
                        result = executeAgentStep(agentId, stepInput);
                        break;
                    case "SKILL":
                        Long skillId = step.has("skillId") ? step.get("skillId").asLong() : null;
                        if (skillId != null && skillService != null) {
                            try {
                                result = skillService.executeSkill(skillId, stepInput);
                            } catch (Exception e) {
                                log.error("Skill step execution failed", e);
                                Map<String, Object> err = new HashMap<>();
                                err.put("error", e.getMessage());
                                result = err;
                            }
                        } else {
                            result = "Missing skillId or SkillService unavailable";
                        }
                        break;
                    case "LOGIC":
                        result = executeLogicStep(stepInput);
                        break;
                    default:
                        log.warn("Unknown step type: {}", type);
                }

                // 3. Store Output
                stepOutputs.put(stepId, result);
                // Also update global context for easier access?
                // For now, access via ${step_1}
            }

            log.info("Workflow execution completed.");
            return stepOutputs;

        } catch (JsonProcessingException e) {
            log.error("Failed to parse DSL", e);
            throw new RuntimeException("Invalid Workflow DSL", e);
        } catch (Exception e) {
            log.error("Workflow execution failed", e);
            throw new RuntimeException("Execution aborted: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> resolveInputs(JsonNode inputMapping, Map<String, Object> context,
            Map<String, Object> stepOutputs) {
        Map<String, Object> resolved = new HashMap<>();
        if (inputMapping == null)
            return resolved;

        inputMapping.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            String valueTemplate = entry.getValue().asText();

            // Simple Variable Substitution: ${step_1} or ${input_param}
            // Real impl would use SpEL or Mustache
            resolved.put(key, interpolate(valueTemplate, context, stepOutputs));
        });

        return resolved;
    }

    private Object interpolate(String template, Map<String, Object> context, Map<String, Object> stepOutputs) {
        if (template.startsWith("${") && template.endsWith("}")) {
            String varName = template.substring(2, template.length() - 1);

            // Check previous steps
            // Format: step_1.output (assuming output is object) or just step_1 (if string)
            if (varName.contains(".")) {
                String[] parts = varName.split("\\.", 2);
                String source = parts[0];
                // String field = parts[1];
                if (stepOutputs.containsKey(source)) {
                    return stepOutputs.get(source); // Simplified: return whole object
                }
            }

            if (stepOutputs.containsKey(varName)) {
                return stepOutputs.get(varName);
            }
            if (context.containsKey(varName)) {
                return context.get(varName);
            }
        }
        return template;
    }

    private Object executeAgentStep(String agentId, Map<String, Object> input) {
        log.info("Calling Agent: {} with {}", agentId, input);
        // Mock execution
        // In real system: routerService.chatCompletion(...)
        return "Agent response for " + input;
    }

    private Object executeLogicStep(Map<String, Object> input) {
        return "Logic executed";
    }
}
