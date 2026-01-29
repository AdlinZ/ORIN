package com.adlin.orin.modules.workflow.engine.handler;

import java.util.Map;

/**
 * Interface for handling specific node type execution.
 */
public interface NodeHandler {

    /**
     * Executes the node logic.
     *
     * @param nodeData The configuration data of the node.
     * @param context  The current execution context (read-only or thread-safe).
     * @return The result of the execution, including outputs.
     */
    NodeExecutionResult execute(Map<String, Object> nodeData, Map<String, Object> context);

    /**
     * Result of a node execution.
     */
    class NodeExecutionResult {
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
}
