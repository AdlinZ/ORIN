package com.adlin.orin.modules.collaboration.service.selection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSelectionContext {
    private String packageId;
    private String subTaskId;
    private String expectedRole;
    private String description;
    private Double qualityThreshold;
}
