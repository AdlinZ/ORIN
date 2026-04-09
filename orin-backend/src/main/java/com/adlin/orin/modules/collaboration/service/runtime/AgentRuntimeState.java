package com.adlin.orin.modules.collaboration.service.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Lightweight runtime view for each role branch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRuntimeState {

    private String branchId;
    private String role;
    private String status;
    private Integer attemptId;
    private Double score;
    private String summary;
    private String degradeReason;
    private List<String> evidenceRefs;
    private Map<String, Object> scoreBreakdown;
    private List<String> fallbackTrail;
}
