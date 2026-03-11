package com.adlin.orin.modules.workflow.dto;

import lombok.Data;

/**
 * 工作流执行结果
 */
@Data
public class WorkflowExecutionResult {
    
    private String executionId;
    private String status; // PENDING, RUNNING, COMPLETED, FAILED
    private String workflowId;
    private String workflowName;
    
    private Object inputs;
    private Object outputs;
    private String errorMessage;
    
    private Long startTime;
    private Long endTime;
    private Long durationMs;
    
    private String traceId;
}
