package com.adlin.orin.modules.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStepRequest {
    @NotNull(message = "步骤顺序不能为空")
    @PositiveOrZero(message = "步骤顺序必须为非负数")
    private Integer stepOrder;

    @NotBlank(message = "步骤名称不能为空")
    @Size(max = 100, message = "步骤名称长度不能超过100个字符")
    private String stepName;

    private String stepType; // SKILL, AGENT, LOGIC
    private Long skillId;
    private Long agentId;
    private Map<String, Object> inputMapping;
    private Map<String, Object> outputMapping;
    private String conditionExpression;
    private List<Long> dependsOn;
}
