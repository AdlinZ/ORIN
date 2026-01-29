package com.adlin.orin.modules.workflow.engine.handler;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component("startNodeHandler")
public class StartNodeHandler implements NodeHandler {
    @Override
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        // Start node typically just passes through initial inputs or triggers execution
        Map<String, Object> output = new HashMap<>();
        output.put("status", "completed");
        return NodeExecutionResult.success(output);
    }
}
