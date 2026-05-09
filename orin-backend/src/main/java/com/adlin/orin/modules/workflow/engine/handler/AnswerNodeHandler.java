package com.adlin.orin.modules.workflow.engine.handler;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component("answerNodeHandler")
public class AnswerNodeHandler implements NodeHandler {
    @Override
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("answer", WorkflowOutputResolver.resolveAnswer(nodeData, context));
        return NodeExecutionResult.success(outputs);
    }
}
