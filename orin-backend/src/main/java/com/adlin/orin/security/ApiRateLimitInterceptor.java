package com.adlin.orin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * API速率限制拦截器
 * 基于滑动窗口算法实现API调用速率限制
 * 使用API Key本身进行速率限制
 */
@Slf4j
@Component
public class ApiRateLimitInterceptor implements HandlerInterceptor {

    // 存储每个API Key的请求计数器
    // Key格式: "apiKeyId:timeWindow"
    private final Map<String, AtomicInteger> requestCounters = new ConcurrentHashMap<>();

    // 存储时间窗口的起始时间
    private final Map<String, Long> windowStartTimes = new ConcurrentHashMap<>();

    // 默认速率限制
    private static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 60;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 允许OPTIONS请求通过
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 从请求属性获取API Key ID(由ApiKeyAuthInterceptor设置)
        Object apiKeyObj = request.getAttribute("apiKey");
        if (apiKeyObj == null) {
            // 如果没有API Key信息,跳过速率限制(可能是公开端点)
            return true;
        }

        String apiKeyId = ((com.adlin.orin.modules.apikey.entity.ApiKey) apiKeyObj).getId();

        // 检查速率限制(每分钟)
        if (!checkRateLimit(apiKeyId, DEFAULT_RATE_LIMIT_PER_MINUTE, 60)) {
            log.warn("Rate limit exceeded for API key: {}", apiKeyId);
            sendRateLimitResponse(response, "Rate limit exceeded");
            return false;
        }

        return true;
    }

    /**
     * 检查速率限制
     *
     * @param apiKeyId      API密钥ID
     * @param limit         限制次数
     * @param windowSeconds 时间窗口(秒)
     * @return 是否允许通过
     */
    private boolean checkRateLimit(String apiKeyId, int limit, int windowSeconds) {
        String key = apiKeyId + ":" + windowSeconds;
        long currentTime = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;

        // 获取或创建时间窗口起始时间
        Long windowStart = windowStartTimes.computeIfAbsent(key, k -> currentTime);

        // 检查是否需要重置窗口
        if (currentTime - windowStart >= windowMs) {
            windowStartTimes.put(key, currentTime);
            requestCounters.put(key, new AtomicInteger(0));
            windowStart = currentTime;
        }

        // 获取或创建计数器
        AtomicInteger counter = requestCounters.computeIfAbsent(key, k -> new AtomicInteger(0));

        // 增加计数并检查是否超限
        int currentCount = counter.incrementAndGet();

        if (currentCount > limit) {
            return false;
        }

        return true;
    }

    /**
     * 发送速率限制响应
     */
    private void sendRateLimitResponse(HttpServletResponse response, String message) throws Exception {
        response.setStatus(429); // Too Many Requests
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", "60");

        String jsonResponse = String.format(
                "{\"error\":{\"message\":\"%s\",\"type\":\"rate_limit_error\",\"code\":\"rate_limit_exceeded\"}}",
                message);

        response.getWriter().write(jsonResponse);
    }

    /**
     * 清理过期的计数器(定期调用)
     */
    public void cleanupExpiredCounters() {
        long currentTime = System.currentTimeMillis();
        windowStartTimes.entrySet().removeIf(entry -> currentTime - entry.getValue() > 3600000 // 1小时
        );

        // 清理对应的计数器
        requestCounters.keySet().removeIf(key -> !windowStartTimes.containsKey(key));

        log.debug("Cleaned up expired rate limit counters");
    }
}
