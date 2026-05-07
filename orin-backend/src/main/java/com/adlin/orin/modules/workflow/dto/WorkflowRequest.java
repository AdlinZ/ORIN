package com.adlin.orin.modules.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRequest {
    private Long id;
    private String workflowName;
    private String description;
    private String workflowType;
    private Map<String, Object> workflowDefinition;
    private Integer timeoutSeconds;
    private Map<String, Object> retryPolicy;
    private String status;
    private String createdBy;
    private java.util.List<WorkflowStepRequest> steps;
}
