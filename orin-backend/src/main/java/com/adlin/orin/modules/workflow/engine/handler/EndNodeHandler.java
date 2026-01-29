package com.adlin.orin.modules.workflow.engine.handler;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component("endNodeHandler")
public class EndNodeHandler implements NodeHandler {
    @Override
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        // End node collects inputs as final output, logic largely handled by executor
        // aggregation
        // Here we just return what we received (resolved inputs)
        return NodeExecutionResult.success(context); // context here would contain resolved inputs
    }
}
