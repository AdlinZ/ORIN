package com.adlin.orin.modules.workflow.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("variableAssignerNodeHandler")
public class VariableAssignerNodeHandler implements NodeHandler {

    @Override
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        String targetVar = (String) nodeData.get("target_variable");
        Object value = nodeData.get("value"); // In real case, this might be an expression
        String writeMode = (String) nodeData.getOrDefault("write_mode", "overwrite");

        log.info("VariableAssigner executing: target={}, mode={}", targetVar, writeMode);

        if (targetVar != null) {
            // Update context (this is a simplified implementation)
            context.put(targetVar, value);
        }

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("assigned_variable", targetVar);
        outputs.put("value", value);

        return NodeExecutionResult.success(outputs);
    }
}
