package com.adlin.orin.modules.workflow.dto;

import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkflowExecutionSubmissionResponse {
    private String taskId;
    private Long workflowId;
    private Long workflowInstanceId;
    private String traceId;
    private TaskEntity.TaskStatus status;
    private String statusUrl;
    private String instanceUrl;
    private String message;
    private String errorMessage;

    public static WorkflowExecutionSubmissionResponse from(TaskEntity task, WorkflowInstanceEntity instance) {
        boolean failed = task.getStatus() == TaskEntity.TaskStatus.FAILED;
        return WorkflowExecutionSubmissionResponse.builder()
                .taskId(task.getTaskId())
                .workflowId(task.getWorkflowId())
                .workflowInstanceId(instance.getId())
                .traceId(instance.getTraceId())
                .status(task.getStatus())
                .statusUrl("/api/v1/workflow-tasks/" + task.getTaskId())
                .instanceUrl("/api/workflows/instances/" + instance.getId())
                .message(failed ? task.getErrorMessage() : "Task enqueued successfully")
                .errorMessage(task.getErrorMessage())
                .build();
    }
}
