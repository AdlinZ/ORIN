package com.adlin.orin.modules.collaboration.service.selection;

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
public class AgentSelectionResult {
    private String selectedAgentId;
    private String selectionMode; // static / bid
    private String selectionReason;
    private Map<String, Double> scoreBreakdown;
    private List<Map<String, Object>> candidates;
}
