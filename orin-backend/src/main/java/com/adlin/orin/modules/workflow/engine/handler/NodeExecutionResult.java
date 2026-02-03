package com.adlin.orin.modules.workflow.engine.handler;

import java.util.Map;

/**
 * Result of a node execution.
 */
public class NodeExecutionResult {
    private final Map<String, Object> outputs;
    private final String selectedHandle; // e.g., "source", "if", "else"
    private final boolean success;

    public NodeExecutionResult(Map<String, Object> outputs, String selectedHandle, boolean success) {
        this.outputs = outputs;
        this.selectedHandle = selectedHandle;
        this.success = success;
    }

    public static NodeExecutionResult success(Map<String, Object> outputs) {
        return new NodeExecutionResult(outputs, "source", true);
    }

    public static NodeExecutionResult success(Map<String, Object> outputs, String selectedHandle) {
        return new NodeExecutionResult(outputs, selectedHandle, true);
    }

    public static NodeExecutionResult failure(Map<String, Object> outputs) {
        return new NodeExecutionResult(outputs, null, false);
    }

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public String getSelectedHandle() {
        return selectedHandle;
    }

    public boolean isSuccess() {
        return success;
    }
}
