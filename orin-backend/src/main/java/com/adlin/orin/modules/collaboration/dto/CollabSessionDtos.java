package com.adlin.orin.modules.collaboration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class CollabSessionDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionCreateRequest {
        private String title;
        private String mainAgentPolicy;
        private Double qualityThreshold;
        private Integer maxCritiqueRounds;
        private Integer draftParallelism;
        private String mainAgentStaticDefault;
        private List<String> bidWhitelist;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionMessageRequest {
        private String content;
        private String category;
        private String priority;
        private String complexity;
        private String collaborationMode;
        private String executionProfile;
        private String workloadType;
        private String failurePolicy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionView {
        private String sessionId;
        private String title;
        private String status;
        private String mainAgentPolicy;
        private Double qualityThreshold;
        private Integer maxCritiqueRounds;
        private Integer draftParallelism;
        private String createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TurnStartResponse {
        private String sessionId;
        private String turnId;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageView {
        private Long id;
        private String sessionId;
        private String turnId;
        private String role;
        private String stage;
        private String content;
        private Map<String, Object> metadata;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionStateView {
        private String sessionId;
        private String status;
        private String latestTurnId;
        private String latestTurnStatus;
        private String packageId;
        private Map<String, Object> runtime;
        private Map<String, Object> selection;
        private List<Map<String, Object>> timeline;
        private List<Map<String, Object>> branches;
        private Map<String, Object> arbiter;
        private List<Map<String, Object>> evidenceRefs;
        private Map<String, Boolean> uiActions;
        private List<String> operatorHints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamEvent {
        private String eventType;
        private String sessionId;
        private String turnId;
        private String stage;
        private String content;
        private Map<String, Object> data;
        private Long timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionMetricsView {
        private Integer hours;
        private Long totalTurns;
        private Long successTurns;
        private Double successRate;
        private Double p95LatencyMs;
        private Long dlqBacklog;
        private Long biddingTriggeredTurns;
        private Double biddingTriggerRate;
        private Double biddingPostSuccessRate;
        private Double avgCritiqueRounds;
        private String overallLevel;
        private Map<String, String> metricLevels;
        private List<String> alerts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelCapabilityRequest {
        private String taskText;
        private String expectedRole;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelCapabilityView {
        private String agentId;
        private String name;
        private String model;
        private String provider;
        private String type;
        private List<String> supports;
        private Boolean healthy;
        private Boolean eligible;
        private Boolean intentMatched;
        private Boolean roleMatched;
        private Boolean imageCapable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicySwitchRequest {
        private String mainAgentPolicy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionResponse {
        private Boolean success;
        private String message;
        private Map<String, Object> data;
    }
}
