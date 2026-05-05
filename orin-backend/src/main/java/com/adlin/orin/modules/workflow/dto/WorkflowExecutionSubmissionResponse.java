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

    public static WorkflowExecutionSubmissionResponse from(TaskEntity task, WorkflowInstanceEntity instance) {
        return WorkflowExecutionSubmissionResponse.builder()
                .taskId(task.getTaskId())
                .workflowId(task.getWorkflowId())
                .workflowInstanceId(instance.getId())
                .traceId(instance.getTraceId())
                .status(task.getStatus())
                .statusUrl("/v1/tasks/" + task.getTaskId())
                .instanceUrl("/api/workflows/instances/" + instance.getId())
                .message("Task enqueued successfully")
                .build();
    }
}
