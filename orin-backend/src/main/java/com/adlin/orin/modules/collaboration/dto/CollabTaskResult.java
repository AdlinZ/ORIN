package com.adlin.orin.modules.collaboration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 协作任务结果 - AI Engine -> Backend
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollabTaskResult implements Serializable {

    private static final long serialVersionUID = 1L;

    // Core identification
    private String packageId;
    private String sessionId;
    private String turnId;
    private String subTaskId;
    private String traceId;
    private Integer attempt;
    private String stage;
    private Map<String, Object> selectionMeta;

    // Execution status
    private String status;            // COMPLETED, FAILED, TIMEOUT
    private String result;            // Execution result
    private String errorMessage;      // If failed

    // Timing
    private Long startedAt;
    private Long completedAt;
    private Long latencyMs;

    // Execution info
    private String executedBy;        // Agent/Workflow ID that executed

    // Additional metadata
    private Map<String, Object> metadata;
}
