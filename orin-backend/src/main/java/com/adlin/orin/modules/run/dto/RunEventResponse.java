package com.adlin.orin.modules.run.dto;

import com.adlin.orin.modules.run.entity.RunEvent;
import lombok.Builder;
import lombok.Data;

/**
 * F04 Run 事件响应（GET /api/v1/runs/{runId}/events）。
 *
 * <p>返回 run_events 表中的结构化事件，供前端渲染事件时间线。
 */
@Data
@Builder
public class RunEventResponse {

    private Long id;
    private Integer eventSeq;
    private String level;
    private String message;
    private Long timestamp;
    private Integer runAttempt;
    private String leaseId;

    public static RunEventResponse from(RunEvent event) {
        return RunEventResponse.builder()
                .id(event.getId())
                .eventSeq(event.getEventSeq())
                .level(event.getLevel())
                .message(event.getMessage())
                .timestamp(event.getTimestamp())
                .runAttempt(event.getRunAttempt())
                .leaseId(event.getLeaseId())
                .build();
    }
}
