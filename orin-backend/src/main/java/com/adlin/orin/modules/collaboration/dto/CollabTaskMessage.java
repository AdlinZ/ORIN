package com.adlin.orin.modules.collaboration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 协作任务消息 - Backend -> AI Engine
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollabTaskMessage implements Serializable {

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

    // Collaboration context
    private String collaborationMode;  // PARALLEL, SEQUENTIAL, CONSENSUS
    private String expectedRole;      // PLANNER, SPECIALIST, REVIEWER, etc.

    // Subtask details
    private String description;
    private String inputData;         // JSON string of subtask input
    private List<String> dependsOn;   // Dependencies for scheduling

    // Execution control
    private Integer maxRetries;
    private Long timeoutMillis;
    private String executionStrategy; // AGENT, WORKFLOW, HUMAN

    // Retry configuration (exponential backoff)
    private Long retryInitialInterval;    // Initial retry interval in ms (default: 1000)
    private Double retryMultiplier;        // Backoff multiplier (default: 2.0)
    private Long retryMaxInterval;         // Max retry interval in ms (default: 30000)
    private boolean delayedRetry;          // Whether to use delayed retry

    // Reply configuration
    private String replyTo;           // Queue name for result callback
    private String correlationId;    // For matching requests to responses

    // Context snapshot (for state management)
    private Map<String, Object> contextSnapshot;

    // Metrics
    private Long enqueuedAt;
}
