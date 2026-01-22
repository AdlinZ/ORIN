package com.adlin.orin.modules.workflow.dto;

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
    private Integer stepOrder;
    private String stepName;
    private Long skillId;
    private Map<String, Object> inputMapping;
    private Map<String, Object> outputMapping;
    private String conditionExpression;
    private List<Long> dependsOn;
}
