package com.adlin.orin.modules.run.dto;

import lombok.Builder;
import lombok.Data;

/**
 * F03 lease/claim 响应（ADR-001 /lease/claim）。
 *
 * <p>R2：run_assignment 是 lease/attempt/终端原因的唯一事实。
 * leaseToken 保留作为鉴权与向后兼容标识；assignmentId + leaseId 是新的权威标识。
 */
@Data
@Builder
public class LeaseRunResponse {

    /** 是否领取到 Run。 */
    private boolean acquired;

    /** Run id。 */
    private String runId;

    /** run_assignment.id（R2 新增——secret-bind 与 renew 的主键）。 */
    private String assignmentId;

    /** 显式 lease 标识（R2 新增——与 leaseToken 值相同，语义明确）。 */
    private String leaseId;

    /** Lease 验证令牌（不透明 bearer；与 leaseId 同值，保留向后兼容）。 */
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
