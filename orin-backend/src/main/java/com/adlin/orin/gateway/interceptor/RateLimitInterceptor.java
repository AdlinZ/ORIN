package com.adlin.orin.gateway.interceptor;

import com.adlin.orin.modules.apikey.entity.ApiKey;
import com.adlin.orin.modules.apikey.service.ApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * API限流拦截器
 * 基于Redis的滑动窗口限流
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, String> redisTemplate;
    private final ApiKeyService apiKeyService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 只对/v1/路径进行限流
        String path = request.getRequestURI();
        if (!path.startsWith("/v1/")) {
            return true;
        }

        // 获取API密钥
        String apiKeyString = extractApiKey(request);
        if (apiKeyString == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter()
                    .write("{\"error\": {\"message\": \"Missing API key\", \"type\": \"authentication_error\"}}");
            return false;
        }

        // 验证API密钥
        Optional<ApiKey> apiKeyOpt = apiKeyService.validateApiKey(apiKeyString);
        if (apiKeyOpt.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter()
                    .write("{\"error\": {\"message\": \"Invalid API key\", \"type\": \"authentication_error\"}}");
            return false;
        }

        ApiKey apiKey = apiKeyOpt.get();

        // 检查每分钟限流
        if (!checkRateLimit(apiKey.getId(), "minute", apiKey.getRateLimitPerMinute(), 60)) {
            response.setStatus(429); // Too Many Requests
            response.getWriter().write(
                    "{\"error\": {\"message\": \"Rate limit exceeded (per minute)\", \"type\": \"rate_limit_error\"}}");
            return false;
        }

        // 检查每日限流
        if (!checkRateLimit(apiKey.getId(), "day", apiKey.getRateLimitPerDay(), 86400)) {
            response.setStatus(429);
            response.getWriter().write(
                    "{\"error\": {\"message\": \"Rate limit exceeded (per day)\", \"type\": \"rate_limit_error\"}}");
            return false;
        }

        // 将API密钥信息存入请求属性
        request.setAttribute("apiKey", apiKey);
        request.setAttribute("userId", apiKey.getUserId());

        return true;
    }

    /**
     * 检查限流
     * 
     * @param keyId  API密钥ID
     * @param window 时间窗口（minute/day）
     * @param limit  限制数量
     * @param ttl    过期时间（秒）
     * @return 是否允许通过
     */
    private boolean checkRateLimit(String keyId, String window, int limit, int ttl) {
        String redisKey = "rate_limit:" + keyId + ":" + window;

        try {
            // 获取当前计数
            String countStr = redisTemplate.opsForValue().get(redisKey);
            int count = countStr != null ? Integer.parseInt(countStr) : 0;

            if (count >= limit) {
                return false;
            }

            // 增加计数
            Long newCount = redisTemplate.opsForValue().increment(redisKey);

            // 如果是第一次，设置过期时间
            if (newCount == 1) {
                redisTemplate.expire(redisKey, ttl, TimeUnit.SECONDS);
            }

            return true;
        } catch (Exception e) {
            log.error("Rate limit check failed: {}", e.getMessage());
            // 如果Redis出错，允许通过（降级策略）
            return true;
        }
    }

    /**
     * 从请求中提取API密钥
     */
    private String extractApiKey(HttpServletRequest request) {
        // 从Authorization header提取
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 从X-API-Key header提取
        String apiKeyHeader = request.getHeader("X-API-Key");
        if (apiKeyHeader != null) {
            return apiKeyHeader;
        }

        return null;
    }
}
