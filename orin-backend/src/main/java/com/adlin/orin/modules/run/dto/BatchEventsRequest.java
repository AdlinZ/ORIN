package com.adlin.orin.modules.run.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * ADR-001 /events 请求 — Runner 批量回传中间态与日志。
 *
 * <p>幂等键 run:idemp:{runId}:{leaseId}:{runAttempt}:{eventSeq}（ADR-001 D-1.4.1）。
 */
@Data
public class BatchEventsRequest {

    /** Lease 验证令牌（R2：leaseId 优先；leaseToken 保留向后兼容）。 */
    private String leaseToken;

    /** Lease 标识（R2 新增——优先于 leaseToken 使用）。 */
    private String leaseId;

    /** 批量事件数组。 */
    @NotEmpty
    @Valid
    private List<EventEntry> events;

    @Data
    public static class EventEntry {

        /** Runner 自增序号。 */
        @NotNull
        private Integer seq;

        /** 日志级别（INFO / WARN / ERROR）。 */
        private String level;

        /** 日志消息。 */
        @NotBlank
        private String message;

        /** 时间戳（epoch millis）。 */
        private Long timestamp;
    }
}
