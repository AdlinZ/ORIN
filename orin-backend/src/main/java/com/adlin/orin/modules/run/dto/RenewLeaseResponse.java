package com.adlin.orin.modules.run.dto;

import lombok.Builder;
import lombok.Data;

/**
 * ADR-001 /lease/{leaseId}/renew 响应。
 *
 * <p>MVP 阶段总是返回 action=no_op + 新 leaseExpiresAt。
 */
@Data
@Builder
public class RenewLeaseResponse {

    /** 控制指令：no_op | cancel | drain（ADR-001 D-1.2）。 */
    private String action;

    /** cancel 原因（USER_CANCELLED | SECRET_REVOKED | LEASE_EXPIRED | ASSIGNMENT_TERMINATED）。 */
    private String reason;

    /** 续租后的过期时间（epoch millis）。 */
    private Long leaseExpiresAt;

    /** W3C traceId。 */
    private String traceId;

    public static RenewLeaseResponse noOp(long leaseExpiresAt, String traceId) {
        return RenewLeaseResponse.builder()
                .action("no_op")
                .leaseExpiresAt(leaseExpiresAt)
                .traceId(traceId)
                .build();
    }
}
