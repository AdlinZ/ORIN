package com.adlin.orin.security;

import com.adlin.orin.common.dto.Result;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.service.GatewaySecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.UUID;

/**
 * API密钥验证拦截器
 * 拦截所有/v1/**路径的请求,验证API密钥
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private final GatewaySecretService gatewaySecretService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 允许OPTIONS请求通过(CORS预检)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 公开只读接口不需要认证
        String path = request.getRequestURI();
        if (path.equals("/v1")
                || path.equals("/v1/health")
                || path.equals("/api/v1/health")
                || path.equals("/v1/providers")
                || path.equals("/v1/docs")
                || path.equals("/v1/capabilities")) {
            return true;
        }

        // 检查是否是JWT token (以eyJ开头) - 让JWT filter处理
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // 如果是JWT token (以eyJ开头)，跳过API Key验证，让JWT filter处理
            if (token.startsWith("eyJ")) {
                return true;
            }
        }

        // 从请求头获取API密钥
        String apiKey = extractApiKey(request);

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Missing API key for request: {} {}", request.getMethod(), path);
            sendUnauthorizedResponse(request, response, "Missing API key");
            return false;
        }

        // 验证API密钥
        Optional<GatewaySecret> validatedKey = gatewaySecretService.validateClientAccessSecret(apiKey);

        if (validatedKey.isEmpty()) {
            log.warn("Invalid API key for request: {} {}", request.getMethod(), path);
            sendUnauthorizedResponse(request, response, "Invalid API key");
            return false;
        }

        // 将API密钥信息存储到请求属性中,供后续使用
        request.setAttribute("apiKey", validatedKey.get());
        request.setAttribute("gatewaySecret", validatedKey.get());
        request.setAttribute("userId", validatedKey.get().getUserId());

        log.debug("API key validated for user: {}", validatedKey.get().getUserId());
        return true;
    }

    /**
     * 从请求头提取API密钥
     * 支持两种格式:
     * 1. Authorization: Bearer sk-orin-xxx
     * 2. X-API-Key: sk-orin-xxx
     */
    private String extractApiKey(HttpServletRequest request) {
        // 尝试从Authorization头获取
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 尝试从X-API-Key头获取
        String apiKeyHeader = request.getHeader("X-API-Key");
        if (apiKeyHeader != null && !apiKeyHeader.isEmpty()) {
            return apiKeyHeader;
        }

        return null;
    }

    /**
     * 发送未授权响应（统一错误码格式）
     */
    private void sendUnauthorizedResponse(HttpServletRequest request, HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Result<?> result = Result.<Void>builder()
                .code(ErrorCode.AUTH_API_KEY_INVALID.getCode())
                .message(message)
                .traceId(UUID.randomUUID().toString())
                .path(request.getRequestURI())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
