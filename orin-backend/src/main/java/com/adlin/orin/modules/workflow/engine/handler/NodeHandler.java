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
}
