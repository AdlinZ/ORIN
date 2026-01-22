package com.adlin.orin.modules.workflow.dto;

import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowResponse {
    private Long id;
    private String workflowName;
    private String description;
    private WorkflowEntity.WorkflowType workflowType;
    private Map<String, Object> workflowDefinition;
    private Integer timeoutSeconds;
    private WorkflowEntity.WorkflowStatus status;
    private String version;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WorkflowResponse fromEntity(WorkflowEntity entity) {
        return WorkflowResponse.builder()
                .id(entity.getId())
                .workflowName(entity.getWorkflowName())
                .description(entity.getDescription())
                .workflowType(entity.getWorkflowType())
                .workflowDefinition(entity.getWorkflowDefinition())
                .timeoutSeconds(entity.getTimeoutSeconds())
                .status(entity.getStatus())
                .version(entity.getVersion())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
