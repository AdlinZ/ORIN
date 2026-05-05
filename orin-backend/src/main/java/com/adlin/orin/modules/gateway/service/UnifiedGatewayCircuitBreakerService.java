package com.adlin.orin.modules.gateway.service;

import com.adlin.orin.modules.gateway.entity.UnifiedGatewayCircuitBreakerPolicy;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayRoute;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayCircuitBreakerPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网关熔断服务 —— 每条路由独立的三态状态机
 *
 * 状态转换：
 *   CLOSED  → OPEN      : 连续失败次数 >= failureThreshold
 *   OPEN    → HALF_OPEN : 冷却时间 timeoutSeconds 到期
 *   HALF_OPEN → CLOSED  : 连续成功次数 >= successThreshold
 *   HALF_OPEN → OPEN    : 任意失败
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedGatewayCircuitBreakerService {

    private final UnifiedGatewayCircuitBreakerPolicyRepository circuitBreakerPolicyRepository;

    private final ConcurrentHashMap<Long, CircuitBreakerState> states = new ConcurrentHashMap<>();

    /**
     * 删除路由时清理对应状态，防止内存泄漏
     */
    public void removeRouteState(Long routeId) {
        states.remove(routeId);
        log.debug("Circuit breaker state removed for routeId={}", routeId);
    }

    /**
     * 检查当前状态数量（用于监控）
     */
    public int getStateCount() {
        return states.size();
    }

    /**
     * 检查熔断器是否允许本次请求通过。
     *
     * @return true 表示允许，false 表示熔断拒绝
     */
    public boolean allowRequest(UnifiedGatewayRoute route) {
        Long policyId = route.getCircuitBreakerPolicyId();
        if (policyId == null) {
            return true;
        }

        UnifiedGatewayCircuitBreakerPolicy policy = circuitBreakerPolicyRepository.findById(policyId).orElse(null);
        if (policy == null || !Boolean.TRUE.equals(policy.getEnabled())) {
            return true;
        }

        CircuitBreakerState state = states.computeIfAbsent(route.getId(),
                id -> new CircuitBreakerState(policy));

        state.syncPolicy(policy);
        return state.allowRequest();
    }

    /**
     * 记录本次请求的结果（请求完成后调用）。
     *
     * @param success true=成功, false=失败
     */
    public void recordResult(UnifiedGatewayRoute route, boolean success) {
        Long policyId = route.getCircuitBreakerPolicyId();
        if (policyId == null) return;

        CircuitBreakerState state = states.get(route.getId());
        if (state == null) return;

        if (success) {
            state.onSuccess();
        } else {
            state.onFailure();
        }
    }

    // -------------------------------------------------------------------------
    // 熔断器状态机
    // -------------------------------------------------------------------------

    static final class CircuitBreakerState {
        private enum State { CLOSED, OPEN, HALF_OPEN }

        private volatile State current = State.CLOSED;

        // 策略参数（volatile 以支持热更新）
        private volatile int failureThreshold;
        private volatile int successThreshold;
        private volatile long timeoutMs;
        private volatile int halfOpenMaxRequests;

        private final AtomicInteger failureCount   = new AtomicInteger(0);
        private final AtomicInteger successCount   = new AtomicInteger(0);
        private final AtomicInteger halfOpenPassed = new AtomicInteger(0);

        /** OPEN 状态的到期时间（毫秒时间戳） */
        private volatile long openUntil = 0;

        CircuitBreakerState(UnifiedGatewayCircuitBreakerPolicy policy) {
            syncPolicy(policy);
        }

        void syncPolicy(UnifiedGatewayCircuitBreakerPolicy policy) {
            this.failureThreshold   = policy.getFailureThreshold();
            this.successThreshold   = policy.getSuccessThreshold();
            this.timeoutMs          = policy.getTimeoutSeconds() * 1000L;
            this.halfOpenMaxRequests = policy.getHalfOpenMaxRequests();
        }

        synchronized boolean allowRequest() {
            switch (current) {
                case CLOSED:
                    return true;

                case OPEN:
                    if (System.currentTimeMillis() >= openUntil) {
                        log.info("Circuit breaker transitioning OPEN -> HALF_OPEN");
                        current = State.HALF_OPEN;
                        halfOpenPassed.set(0);
                        successCount.set(0);
                        return true;
                    }
                    return false;

                case HALF_OPEN:
                    // 只允许有限数量的探测请求
                    int passed = halfOpenPassed.incrementAndGet();
                    return passed <= halfOpenMaxRequests;

                default:
                    return true;
            }
        }

        synchronized void onSuccess() {
            switch (current) {
                case CLOSED:
                    failureCount.set(0); // 重置连续失败计数
                    break;

                case HALF_OPEN:
                    int successes = successCount.incrementAndGet();
                    if (successes >= successThreshold) {
                        log.info("Circuit breaker transitioning HALF_OPEN -> CLOSED");
                        current = State.CLOSED;
                        failureCount.set(0);
                        successCount.set(0);
                    }
                    break;

                default:
                    break;
            }
        }

        synchronized void onFailure() {
            switch (current) {
                case CLOSED:
                    int failures = failureCount.incrementAndGet();
                    if (failures >= failureThreshold) {
                        log.warn("Circuit breaker transitioning CLOSED -> OPEN (failures={})", failures);
                        openCircuit();
                    }
                    break;

                case HALF_OPEN:
                    log.warn("Circuit breaker transitioning HALF_OPEN -> OPEN (probe failed)");
                    openCircuit();
                    break;

                default:
                    break;
            }
        }

        private void openCircuit() {
            current = State.OPEN;
            openUntil = System.currentTimeMillis() + timeoutMs;
            failureCount.set(0);
            successCount.set(0);
            halfOpenPassed.set(0);
        }

        /** 仅供测试 */
        State getState() { return current; }
    }
}
