package com.adlin.orin.modules.run.entity;

/**
 * RunAssignment 状态机（ADR-001 D-1.4.2）。
 *
 * <p>独立于 {@link RunStatus}——Run 维度的状态是从 assignment 派生的只读投影。
 *
 * <pre>
 *   ASSIGNED → ACKED → COMPLETED
 *                     → FAILED
 *                     → CANCELLED
 *                     → EXPIRED
 * </pre>
 */
public enum AssignmentStatus {
    /** Runner 已通过 /lease/claim 领取，尚未确认开始执行。 */
    ASSIGNED,
    /** Runner 已确认收到 lease（R2 MVP 中由首次 /renew 或 /events 隐式确认）。 */
    ACKED,
    /** Runner 正常完成并提交了 /result。 */
    COMPLETED,
    /** Runner 执行失败（含 SECRET_REVOKED / RUNNER_LOCAL_SECRET_MISSING 等）。 */
    FAILED,
    /** 用户显式取消（terminal_reason=USER_CANCELLED）。 */
    CANCELLED,
    /** Lease 过期或 Runner 失联（terminal_reason=NETWORK_LOST 或 CREDENTIAL_REVOKED）。 */
    EXPIRED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == EXPIRED;
    }

    public boolean isActive() {
        return this == ASSIGNED || this == ACKED;
    }
}
