package com.adlin.orin.modules.workflow.dto;

import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
    // ID for updates (optional, null for new workflows)
    private Long id;

    @NotBlank(message = "工作流名称不能为空")
    @Size(max = 100, message = "工作流名称长度不能超过100个字符")
    private String workflowName;

    @Size(max = 2000, message = "描述长度不能超过2000个字符")
    private String description;

    @NotNull(message = "工作流类型不能为空")
    private WorkflowEntity.WorkflowType workflowType;

    @NotNull(message = "工作流定义不能为空")
    private Map<String, Object> workflowDefinition;

    @Positive(message = "超时时间必须为正数")
    private Integer timeoutSeconds;
    private Map<String, Object> retryPolicy;
    private String createdBy;

    // Optional: Create steps along with workflow
    private java.util.List<WorkflowStepRequest> steps;
}
