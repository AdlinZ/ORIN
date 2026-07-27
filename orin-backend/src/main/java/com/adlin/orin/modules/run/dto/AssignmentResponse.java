package com.adlin.orin.modules.run.dto;

import com.adlin.orin.modules.run.entity.RunAssignment;
import lombok.Builder;
import lombok.Data;

/**
 * F04 Run 分配历史响应（GET /api/v1/runs/{runId}/assignments）。
 *
 * <p>返回一个 Run 的所有 run_assignment 行，展示每次 lease 分配的历史。
 */
@Data
@Builder
public class AssignmentResponse {

    private String id;
    private String runnerId;
    private String leaseId;
    private String status;
    private Integer runAttempt;
    private String terminalReason;
    private Long leaseExpiresAt;
    private Long createdAt;

    public static AssignmentResponse from(RunAssignment assignment) {
        return AssignmentResponse.builder()
                .id(assignment.getId())
                .runnerId(assignment.getRunnerId())
                .leaseId(assignment.getLeaseId())
                .status(assignment.getStatus().name())
                .runAttempt(assignment.getRunAttempt())
                .terminalReason(assignment.getTerminalReason())
                .leaseExpiresAt(assignment.getLeaseExpiresAt())
                .createdAt(assignment.getCreatedAt())
                .build();
    }
}
