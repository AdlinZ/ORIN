package com.adlin.orin.modules.workflow.engine.handler;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component("ifElseNodeHandler")
public class IfElseNodeHandler implements NodeHandler {

    @Override
    public NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context) {
        // Condition logic (Simplified, assuming context resolution happened before)
        // In real Dify, condition is often evaluated against an input variable

        // Check if there is a 'condition' field in nodeData that needs evaluation,
        // OR if the input context already contains the evaluation result (if
        // pre-calculated).
        // Since resolveVariables happens in GraphExecutor, we assume context has the
        // value needed.

        // For now, support a simple "condition" key in nodeData which is a variable
        // expression
        // e.g., "{{#sys.query#}} == 'search'"
        // BUT, since we moved variable resolution to Executor, let's assume
        // `GraphExecutor`
        // handled resolution or we do simple checks here.

        // IMPROVEMENT: GraphExecutor usually calls `evaluateCondition` logic.
        // Let's rely on `GraphExecutor` passing resolved conditionResult if it's
        // external,
        // OR we implement simple evaluation here.

        boolean conditionMet = false;

        // Try getting pre-evaluated result
        if (context.containsKey("condition_result")) {
            conditionMet = Boolean.TRUE.equals(context.get("condition_result"));
        } else {
            // Fallback: evaluate 'condition' string from nodeData
            // This is a naive implementation; complex SpEL should be in a utility
            String condition = (String) nodeData.get("condition"); // e.g. "true"
            conditionMet = "true".equalsIgnoreCase(condition);
        }

        Map<String, Object> output = new HashMap<>();
        output.put("result", conditionMet);

        // "if" handle for True, "else" handle for False
        String selectedHandle = conditionMet ? "source" : "false_source"; // Wait, check Dify spec.
        // Dify usually has 'source' (true) and 'target' is implicit? No.
        // VisualWorkflowEditor uses: sourceHandle="if" and sourceHandle="else".

        // Let's use the IDs seen in Frontend: id="if" and id="else"
        selectedHandle = conditionMet ? "if" : "else";

        return NodeExecutionResult.success(output, selectedHandle);
    }
}
