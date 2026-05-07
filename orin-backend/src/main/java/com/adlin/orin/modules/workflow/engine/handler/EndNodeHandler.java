package com.adlin.orin.modules.workflow.engine.handler;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component("endNodeHandler")
public class EndNodeHandler implements NodeHandler {
    @Override
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        // Return a snapshot so the executor can safely store it under the end node id
        // without creating context -> endNode -> context recursion.
        return NodeExecutionResult.success(new HashMap<>(context));
    }
}
