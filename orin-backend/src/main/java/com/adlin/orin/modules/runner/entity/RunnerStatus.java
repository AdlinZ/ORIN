package com.adlin.orin.modules.runner.entity;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;

import java.util.EnumSet;
import java.util.Set;

/**
 * Runner 状态枚举。
 *
 * <p>F01 接入并监控服务器要求的状态机（[docs/Runner架构设计.md §4](../../Runner架构设计.md)）：
 * <ul>
 *   <li>{@link #NEW} 控制面已创建接入指令，Runner 尚未注册</li>
 *   <li>{@link #ENROLLING} Runner 正在交换身份与能力</li>
 *   <li>{@link #ONLINE} 心跳正常且容量可用</li>
 *   <li>{@link #DEGRADED} 心跳存在，但资源、版本或依赖异常</li>
 *   <li>{@link #DRAINING} 维护模式，只等待当前 Run 结束</li>
 *   <li>{@link #OFFLINE} 超过离线阈值未收到心跳</li>
 *   <li>{@link #REVOKED} 凭据被管理员撤销</li>
 * </ul>
 *
 * <p>每个状态定义合法的去向，避免 UI / API / 调度器各自维护一份"能去哪里"映射。
 */
public enum RunnerStatus {
    NEW,
    ENROLLING,
    ONLINE,
    DEGRADED,
    DRAINING,
    OFFLINE,
    REVOKED;

    private static final Set<RunnerStatus> FROM_NEW = EnumSet.of(ENROLLING, OFFLINE, REVOKED);
    private static final Set<RunnerStatus> FROM_ENROLLING = EnumSet.of(ONLINE, OFFLINE, REVOKED);
    private static final Set<RunnerStatus> FROM_ONLINE = EnumSet.of(DEGRADED, DRAINING, OFFLINE, REVOKED);
    private static final Set<RunnerStatus> FROM_DEGRADED = EnumSet.of(ONLINE, DRAINING, OFFLINE, REVOKED);
    private static final Set<RunnerStatus> FROM_DRAINING = EnumSet.of(ONLINE, OFFLINE, REVOKED);
    private static final Set<RunnerStatus> FROM_OFFLINE = EnumSet.of(ONLINE, DRAINING, REVOKED);
    private static final Set<RunnerStatus> FROM_REVOKED = EnumSet.noneOf(RunnerStatus.class);

    /**
     * 校验目标状态是否可由当前状态达到；不可达时抛业务异常。
     *
     * <p>注意 {@link #REVOKED} 是单向终态，{@link #OFFLINE} 可由 ONLINE / DEGRADED / DRAINING
     * 通过心跳超时达到，但 {@link #REVOKED} 之后不再允许任何恢复。
     */
    public void requireCanTransitionTo(RunnerStatus target) {
        if (target == null || this == target) {
            return;
        }
        Set<RunnerStatus> allowed = allowedTargets();
        if (!allowed.contains(target)) {
            throw new BusinessException(
                    ErrorCode.OPERATION_FAILED,
                    String.format("Runner 状态不允许从 %s 切换到 %s", this, target));
        }
    }

    /**
     * 已完成接入的 Runner 是否接受常规心跳。NEW / ENROLLING 的首次接入心跳由机器通道
     * 单独处理，OFFLINE 由下一次有效心跳恢复，REVOKED 永久拒绝。
     */
    public boolean acceptsHeartbeat() {
        return this == ONLINE || this == DEGRADED || this == DRAINING || this == OFFLINE;
    }

    public boolean isTerminal() {
        return this == REVOKED;
    }

    private Set<RunnerStatus> allowedTargets() {
        switch (this) {
            case NEW: return FROM_NEW;
            case ENROLLING: return FROM_ENROLLING;
            case ONLINE: return FROM_ONLINE;
            case DEGRADED: return FROM_DEGRADED;
            case DRAINING: return FROM_DRAINING;
            case OFFLINE: return FROM_OFFLINE;
            case REVOKED: return FROM_REVOKED;
            default: throw new IllegalStateException("Unknown RunnerStatus: " + this);
        }
    }
}
