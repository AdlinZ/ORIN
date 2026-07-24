package com.adlin.orin.modules.run.entity;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;

import java.util.EnumSet;
import java.util.Set;

/**
 * Run 状态枚举（F03 Agent→Runner 执行记录）。
 *
 * <p>状态机：
 * <pre>
 *   QUEUED → LEASED → RUNNING → COMPLETED
 *                            `→ FAILED
 *   QUEUED → CANCELLED
 *   LEASED  → CANCELLED（lease 过期自动取消）
 * </pre>
 *
 * <p>每个状态定义合法的去向，避免多处维护"能去哪里"映射。
 */
public enum RunStatus {
    QUEUED,
    LEASED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    private static final Set<RunStatus> FROM_QUEUED    = EnumSet.of(LEASED, CANCELLED);
    private static final Set<RunStatus> FROM_LEASED    = EnumSet.of(RUNNING, CANCELLED);
    private static final Set<RunStatus> FROM_RUNNING   = EnumSet.of(COMPLETED, FAILED, CANCELLED);

    /**
     * 校验目标状态是否可由当前状态达到；不可达时抛业务异常。
     */
    public void requireCanTransitionTo(RunStatus target) {
        if (target == null || this == target) {
            return;
        }
        Set<RunStatus> allowed = allowedTargets();
        if (!allowed.contains(target)) {
            throw new BusinessException(
                    ErrorCode.OPERATION_FAILED,
                    String.format("Run 状态不允许从 %s 切换到 %s", this, target));
        }
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean isActive() {
        return this == QUEUED || this == LEASED || this == RUNNING;
    }

    private Set<RunStatus> allowedTargets() {
        switch (this) {
            case QUEUED:  return FROM_QUEUED;
            case LEASED:  return FROM_LEASED;
            case RUNNING: return FROM_RUNNING;
            default:      return EnumSet.noneOf(RunStatus.class);
        }
    }
}
