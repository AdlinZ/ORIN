package com.adlin.orin.modules.workflow.engine.handler;

import com.adlin.orin.modules.agent.service.AgentExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("agentNodeHandler")
@RequiredArgsConstructor
public class AgentNodeHandler implements NodeHandler {

    private final AgentExecutor agentExecutor;

    @Override
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) { // Context is inputs
                                                                                                    // here
        Long agentId = getLongValue(nodeData, "agentId");
        if (agentId == null) {
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("status", "skipped");
            outputs.put("reason", "Agent node uses a non-numeric ORIN agentId; legacy Java executor did not invoke it.");
            outputs.put("agentId", nodeData.get("agentId"));
            outputs.put("agentName", nodeData.get("agentName"));
            return NodeExecutionResult.success(outputs);
        }

        log.info("AgentNode executing agentId={}", agentId);
        // context passed here should be the "inputs" resolved by the executor
        Map<String, Object> result = agentExecutor.executeAgent(agentId, context);
        return NodeExecutionResult.success(result);
    }

    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null)
            return null;
        if (value instanceof Number)
            return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
