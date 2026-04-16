package com.adlin.orin.modules.gateway.service;

import com.adlin.orin.modules.gateway.entity.GatewayRateLimitPolicy;
import com.adlin.orin.modules.gateway.entity.GatewayRoute;
import com.adlin.orin.modules.gateway.repository.GatewayRateLimitPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关限流服务 —— 令牌桶算法
 *
 * 维度支持：
 *   GLOBAL   每条路由全局共用一个桶
 *   IP       每条路由 × 客户端 IP 各一个桶
 *   API_KEY  每条路由 × API Key 各一个桶
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayRateLimiterService {

    private final GatewayRateLimitPolicyRepository rateLimitPolicyRepository;

    /** key = routeId + ":" + bucketKey(ip/apiKey/"GLOBAL") */
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /**
     * 尝试消耗一个令牌。
     *
     * @return true 表示允许通过，false 表示被限流
     */
    public boolean tryAcquire(GatewayRoute route, String clientIp, String apiKeyId) {
        Long policyId = route.getRateLimitPolicyId();
        if (policyId == null) {
            return true; // 路由没有配置限流策略
        }

        GatewayRateLimitPolicy policy = rateLimitPolicyRepository.findById(policyId).orElse(null);
        if (policy == null || !Boolean.TRUE.equals(policy.getEnabled())) {
            return true;
        }

        String bucketKey = buildBucketKey(policy.getDimension(), route.getId(), clientIp, apiKeyId);
        TokenBucket bucket = buckets.computeIfAbsent(bucketKey,
                k -> new TokenBucket(policy.getCapacity(), policy.getBurst(), policy.getWindowSeconds()));

        // 策略参数可能被更新，同步一下
        bucket.reconfigure(policy.getCapacity(), policy.getBurst(), policy.getWindowSeconds());

        boolean allowed = bucket.tryConsume();
        if (!allowed) {
            log.warn("Rate limit exceeded: route={} dimension={} key={}", route.getName(), policy.getDimension(), bucketKey);
        }
        return allowed;
    }

    private String buildBucketKey(String dimension, Long routeId, String clientIp, String apiKeyId) {
        return switch (dimension == null ? "GLOBAL" : dimension.toUpperCase()) {
            case "IP" -> routeId + ":IP:" + (clientIp != null ? clientIp : "unknown");
            case "API_KEY" -> routeId + ":AK:" + (apiKeyId != null ? apiKeyId : "anonymous");
            default -> routeId + ":GLOBAL";
        };
    }

    // -------------------------------------------------------------------------
    // 令牌桶实现
    // -------------------------------------------------------------------------

    static final class TokenBucket {
        private volatile int capacity;      // 窗口内最大令牌数
        private volatile int burst;         // 桶容量（允许突发）
        private volatile long windowMs;     // 补充窗口（毫秒）

        private final AtomicLong tokens;    // 当前令牌数 * 1000（精度放大）
        private volatile long lastRefillAt; // 上次补充时间（毫秒）

        TokenBucket(int capacity, int burst, int windowSeconds) {
            this.capacity = capacity;
            this.burst = burst;
            this.windowMs = windowSeconds * 1000L;
            // 初始桶满（burst）
            this.tokens = new AtomicLong((long) burst * 1000);
            this.lastRefillAt = System.currentTimeMillis();
        }

        synchronized void reconfigure(int capacity, int burst, int windowSeconds) {
            this.capacity = capacity;
            this.burst = burst;
            this.windowMs = windowSeconds * 1000L;
        }

        synchronized boolean tryConsume() {
            refill();
            long current = tokens.get();
            if (current >= 1000L) {
                tokens.addAndGet(-1000L);
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillAt;
            if (elapsed <= 0) return;

            // 每 windowMs 毫秒补充 capacity 个令牌
            long toAdd = (long) capacity * 1000 * elapsed / windowMs;
            if (toAdd > 0) {
                long maxTokens = (long) burst * 1000;
                long updated = Math.min(tokens.get() + toAdd, maxTokens);
                tokens.set(updated);
                lastRefillAt = now;
            }
        }
    }
}
