package com.adlin.orin.modules.workflow.dto;

import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
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
    private String workflowName;
    private String description;
    private WorkflowEntity.WorkflowType workflowType;
    private Map<String, Object> workflowDefinition;
    private Integer timeoutSeconds;
    private Map<String, Object> retryPolicy;
    private String createdBy;
}
