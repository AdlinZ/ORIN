package com.adlin.orin.security;

import com.adlin.orin.modules.monitor.entity.RateLimitConfig;
import com.adlin.orin.modules.monitor.service.MonitorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

/**
 * API速率限制拦截器
 * 基于Redis令牌桶算法实现分布式限流
 * 支持多维度限流: user_id + api_key + agent_id
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiRateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;
    private final MonitorService monitorService;

    // Redis 令牌桶 Lua 脚本
    // 返回值: [是否允许, 剩余令牌数, 重置时间(秒)]
    private static final String TOKEN_BUCKET_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local requested = tonumber(ARGV[3])
            local now = tonumber(ARGV[4])
            local window_seconds = tonumber(ARGV[5])

            local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(bucket[1])
            local last_refill = tonumber(bucket[2])

            -- 如果是首次创建
            if tokens == nil then
                tokens = capacity
                last_refill = now
            end

            -- 计算应该补充的令牌数
            local elapsed = now - last_refill
            local refill = elapsed * refill_rate
            tokens = math.min(capacity, tokens + refill)

            -- 检查是否足够
            local allowed = false
            local remaining = 0
            if tokens >= requested then
                allowed = true
                tokens = tokens - requested
                remaining = math.floor(tokens)
            else
                remaining = 0
            end

            -- 计算下次重置时间
            local reset_time = now
            if tokens < capacity then
                reset_time = last_refill + math.ceil((capacity - tokens) / refill_rate)
            end

            -- 保存状态
            redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
            redis.call('EXPIRE', key, window_seconds * 2)

            return {allowed and 1 or 0, remaining, reset_time}
            """;

    private final RedisScript<List> tokenBucketScript = RedisScript.of(TOKEN_BUCKET_SCRIPT, List.class);

    // 滑窗限流 Lua 脚本（备用）
    private static final String SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window_seconds = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            local start_time = now - window_seconds

            -- 移除窗口外的请求记录
            redis.call('ZREMRANGEBYSCORE', key, '-inf', start_time)

            -- 获取当前窗口内的请求数
            local count = redis.call('ZCARD', key)

            local allowed = false
            local remaining = 0
            if count < limit then
                allowed = true
                redis.call('ZADD', key, now, now .. '-' .. math.random())
                remaining = limit - count - 1
            else
                remaining = 0
            end

            -- 计算重置时间
            local reset_time = now + window_seconds

            redis.call('EXPIRE', key, window_seconds * 2)

            return {allowed and 1 or 0, remaining, reset_time}
            """;

    private final RedisScript<List> slidingWindowScript = RedisScript.of(SLIDING_WINDOW_SCRIPT, List.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 允许OPTIONS请求通过
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 获取限流配置
        RateLimitConfig config = monitorService.getRateLimitConfigCached();
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            // 限流未启用，放行
            return true;
        }

        // 从请求属性获取API Key信息(由ApiKeyAuthInterceptor设置)
        Object apiKeyObj = request.getAttribute("apiKey");
        if (apiKeyObj == null) {
            // 如果没有API Key信息,跳过速率限制(可能是公开端点)
            return true;
        }

        com.adlin.orin.modules.apikey.entity.ApiKey apiKey =
                (com.adlin.orin.modules.apikey.entity.ApiKey) apiKeyObj;

        // 构建限流key - 支持多维度
        String userId = apiKey.getUserId();
        String apiKeyId = apiKey.getId();
        String agentId = request.getParameter("agent_id");
        if (agentId == null) {
            agentId = request.getParameter("agentId");
        }

        StringBuilder rateLimitKey = new StringBuilder("rate_limit:");

        // 根据配置添加限流维度
        if (Boolean.TRUE.equals(config.getEnableUserLimit()) && userId != null) {
            rateLimitKey.append("user:").append(userId).append(":");
        }
        if (Boolean.TRUE.equals(config.getEnableApiKeyLimit())) {
            rateLimitKey.append("apikey:").append(apiKeyId).append(":");
        }
        if (Boolean.TRUE.equals(config.getEnableAgentLimit()) && agentId != null) {
            rateLimitKey.append("agent:").append(agentId);
        }

        // 去掉最后的冒号
        String key = rateLimitKey.toString();
        if (key.endsWith(":")) {
            key = key.substring(0, key.length() - 1);
        }

        // 选用算法
        int limit = config.getRequestsPerMinute() != null ? config.getRequestsPerMinute() : 60;
        int bucketSize = config.getBucketSize() != null ? config.getBucketSize() : 60;
        double refillRate = config.getRefillRate() != null ? config.getRefillRate() : 1.0;

        long now = System.currentTimeMillis() / 1000; // 秒
        int windowSeconds = 60; // 分钟窗口

        // 执行限流检查
        RateLimitResult result;
        if ("SLIDING_WINDOW".equalsIgnoreCase(config.getAlgorithm())) {
            result = executeSlidingWindow(key, limit, windowSeconds, now);
        } else {
            // 默认使用令牌桶
            result = executeTokenBucket(key, bucketSize, refillRate, 1, now, windowSeconds);
        }

        // 设置响应头
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetTime));

        if (!result.allowed) {
            log.warn("Rate limit exceeded for key: {}, remaining: {}, reset: {}",
                    key, result.remaining, result.resetTime);

            long retryAfter = result.resetTime - now;
            if (retryAfter < 1) retryAfter = 60;

            sendRateLimitResponse(response, "Rate limit exceeded", retryAfter);
            return false;
        }

        return true;
    }

    /**
     * 执行令牌桶算法
     */
    private RateLimitResult executeTokenBucket(String key, int capacity, double refillRate,
                                                int requested, long now, int windowSeconds) {
        try {
            List<Long> result = redisTemplate.execute(
                    tokenBucketScript,
                    Arrays.asList(key),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(requested),
                    String.valueOf(now),
                    String.valueOf(windowSeconds)
            );

            if (result != null && result.size() >= 3) {
                return new RateLimitResult(
                        result.get(0) == 1L,
                        result.get(1).intValue(),
                        result.get(2).longValue()
                );
            }
        } catch (Exception e) {
            log.error("Redis token bucket error, falling back to allow: {}", e.getMessage());
            // Redis出错时默认放行，避免影响正常业务
        }

        // 出错时返回允许通过
        return new RateLimitResult(true, capacity, now + windowSeconds);
    }

    /**
     * 执行滑窗算法
     */
    private RateLimitResult executeSlidingWindow(String key, int limit,
                                                  int windowSeconds, long now) {
        try {
            List<Long> result = redisTemplate.execute(
                    slidingWindowScript,
                    Arrays.asList(key),
                    String.valueOf(limit),
                    String.valueOf(windowSeconds),
                    String.valueOf(now)
            );

            if (result != null && result.size() >= 3) {
                return new RateLimitResult(
                        result.get(0) == 1L,
                        result.get(1).intValue(),
                        result.get(2).longValue()
                );
            }
        } catch (Exception e) {
            log.error("Redis sliding window error, falling back to allow: {}", e.getMessage());
        }

        return new RateLimitResult(true, limit, now + windowSeconds);
    }

    /**
     * 发送速率限制响应
     */
    private void sendRateLimitResponse(HttpServletResponse response, String message, long retryAfter)
            throws Exception {
        response.setStatus(429); // Too Many Requests
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(retryAfter));

        String jsonResponse = String.format(
                "{\"error\":{\"message\":\"%s\",\"type\":\"rate_limit_error\",\"code\":\"rate_limit_exceeded\",\"retry_after\":%d}}",
                message, retryAfter);

        response.getWriter().write(jsonResponse);
    }

    /**
     * 限流结果
     */
    private static class RateLimitResult {
        final boolean allowed;
        final int remaining;
        final long resetTime;

        RateLimitResult(boolean allowed, int remaining, long resetTime) {
            this.allowed = allowed;
            this.remaining = remaining;
            this.resetTime = resetTime;
        }
    }
}
