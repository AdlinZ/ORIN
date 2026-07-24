package com.adlin.orin.modules.run.entity;

/**
 * BindingStatus — lease_secret_binding 行的生命周期（ADR-002 D-2.8.2）。
 *
 * <pre>
 *   ACTIVE → INVALIDATED  （管理员 Revoke secret）
 *   ACTIVE → RELEASED     （assignment 终态清理）
 * </pre>
 */
public enum BindingStatus {
    /** binding 有效；Runner 可使用对应的 materialized secret。 */
    ACTIVE,
    /** 被管理员 Revocation 显式作废；Runner 必须立即停用该 secret。 */
    INVALIDATED,
    /** assignment 已达终态，后台清理已释放 binding。 */
    RELEASED
}
