package com.adlin.orin.modules.run.dto;

import lombok.Builder;
import lombok.Data;

/**
 * F03 lease 响应 — Runner 领取到 Run 后返回。
 */
@Data
@Builder
public class LeaseRunResponse {

    /** 是否领取到 Run。 */
    private boolean acquired;

    /** Run id。 */
    private String runId;

    /** Lease 验证令牌。 */
    private String leaseToken;

    /** Agent config_snapshot（JSON）。 */
    private String configSnapshot;

    /** 用户输入。 */
    private String input;

    /** lease 过期时间（epoch millis）。 */
    private Long leaseExpiresAt;

    public static LeaseRunResponse empty() {
        return LeaseRunResponse.builder().acquired(false).build();
    }
}
