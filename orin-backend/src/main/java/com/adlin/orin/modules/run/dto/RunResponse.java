package com.adlin.orin.modules.run.dto;

import com.adlin.orin.modules.run.entity.Run;
import lombok.Builder;
import lombok.Data;

/**
 * F03 Run 详情响应。
 */
@Data
@Builder
public class RunResponse {

    private String id;
    private String agentId;
    private String agentVersionId;
    private String runnerId;
    private String status;
    private String input;
    private String output;
    private String errorMessage;
    private Long leasedAt;
    private Long startedAt;
    private Long completedAt;
    private Long createdAt;
    private String createdBy;
    private Integer retryCount;
    private Integer maxRetries;
    private String retryOfRunId;
    private String traceId;
    private String terminalReason;
    private Integer runAttempt;
    private String endpointId;

    public static RunResponse from(Run run) {
        return RunResponse.builder()
                .id(run.getId())
                .agentId(run.getAgentId())
                .agentVersionId(run.getAgentVersionId())
                .runnerId(run.getRunnerId())
                .status(run.getStatus().name())
                .input(run.getInput())
                .output(run.getOutput())
                .errorMessage(run.getErrorMessage())
                .leasedAt(run.getLeasedAt())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .createdAt(run.getCreatedAt())
                .createdBy(run.getCreatedBy())
                .retryCount(run.getRetryCount())
                .maxRetries(run.getMaxRetries())
                .retryOfRunId(run.getRetryOfRunId())
                .traceId(run.getTraceId())
                .terminalReason(run.getTerminalReason())
                .runAttempt(run.getRunAttempt())
                .endpointId(run.getEndpointId())
                .build();
    }
}
