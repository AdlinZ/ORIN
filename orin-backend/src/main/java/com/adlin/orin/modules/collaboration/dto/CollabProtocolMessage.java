package com.adlin.orin.modules.collaboration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Structured protocol message exchanged between collaborative agent branches.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollabProtocolMessage {

    private String type; // PLAN / EVIDENCE / PROPOSAL / CRITIQUE / PATCH / FINAL
    private String fromRole;
    private String branchId;
    private Integer attemptId;
    private String content;
    private List<String> evidenceRefs;
    private Map<String, Object> metadata;
}
