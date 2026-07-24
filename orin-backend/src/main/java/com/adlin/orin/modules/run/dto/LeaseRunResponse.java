package com.adlin.orin.modules.run.dto;

import lombok.Builder;
import lombok.Data;

/**
 * F03 lease/claim 响应（ADR-001 /lease/claim）。
 * <p>MVP：leaseToken 同时充当 lease 标识与鉴权。
 * 独立 leaseId 在 R2（run_assignment 表持久化）时加入。
 */
@Data
@Builder
public class LeaseRunResponse {

    /** 是否领取到 Run。 */
    private boolean acquired;

    /** Run id。 */
    private String runId;

    /** Lease 验证令牌（不透明 bearer；MVP 同时作为 lease 标识）。 */
    private String leaseToken;

    /** Agent config_snapshot（JSON）。 */
    private String configSnapshot;

    /** 用户输入。 */
    private String input;

    /** lease 过期时间（epoch millis）。 */
    private Long leaseExpiresAt;

    /** W3C traceId（ADR-001 D-1.5）。 */
    private String traceId;

    public static LeaseRunResponse empty() {
        return LeaseRunResponse.builder().acquired(false).build();
    }
}
