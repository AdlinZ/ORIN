package com.adlin.orin.modules.workflow.engine.handler;

import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

@Component("endNodeHandler")
public class EndNodeHandler implements NodeHandler {
    @Override
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        Map<String, Object> outputs = WorkflowOutputResolver.resolveOutputMappings(nodeData.get("outputs"), context);
        return NodeExecutionResult.success(new LinkedHashMap<>(outputs));
    }
}
