package com.adlin.orin.common.exception;

/**
 * 工作流执行异常
 */
public class WorkflowExecutionException extends BusinessException {
    public WorkflowExecutionException(String message) {
        super(ErrorCode.WORKFLOW_EXECUTION_FAILED, message);
    }

    public WorkflowExecutionException(String message, Throwable cause) {
        super(ErrorCode.WORKFLOW_EXECUTION_FAILED, message, cause);
    }
}
