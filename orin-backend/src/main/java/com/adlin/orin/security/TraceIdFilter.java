package com.adlin.orin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * W3C traceparent header 生成与传播过滤器。
 *
 * 生成符合 W3C Trace Context 标准的 traceparent header：
 *   traceparent = 00-<trace-id>-<span-id>-<trace-flags>
 *
 * 必填字段自动注入 MDC（供 JSON 日志使用）：traceId / spanId / method / path。
 * 日志 appender 通过 includeMdcKey 读取。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    /** W3C 标准 traceparent header */
    public static final String TRACEPARENT_HEADER = "traceparent";
    /** W3C 标准 tracestate header */
    public static final String TRACESTATE_HEADER = "tracestate";
    /** ORIN 内部 traceId header（兼容旧协议） */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    /** 请求链路过spanId header */
    public static final String SPAN_ID_HEADER = "X-Span-Id";
    /** MDC key */
    public static final String TRACE_ID_KEY = "traceId";
    public static final String SPAN_ID_KEY = "spanId";
    public static final String METHOD_KEY = "method";
    public static final String PATH_KEY = "path";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = parseTraceId(request);
        String spanId = parseOrGenerateSpanId(request, traceId);

        try {
            MDC.put(TRACE_ID_KEY, traceId);
            MDC.put(SPAN_ID_KEY, spanId);
            MDC.put(METHOD_KEY, request.getMethod());
            MDC.put(PATH_KEY, request.getRequestURI());

            // 写入响应 traceparent + 兼容旧 header
            response.setHeader(TRACEPARENT_HEADER, buildTraceparent(traceId, spanId));
            response.setHeader(TRACE_ID_HEADER, traceId);
            response.setHeader(SPAN_ID_HEADER, spanId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
            MDC.remove(SPAN_ID_KEY);
            MDC.remove(METHOD_KEY);
            MDC.remove(PATH_KEY);
        }
    }

    /**
     * 从请求中解析 traceparent 或兼容旧 X-Trace-Id。
     * 格式：00-<trace-id>-<span-id>-<trace-flags>（trace-id=32 hex char，span-id=16 hex char，flags=2 hex char）
     */
    String parseTraceId(HttpServletRequest request) {
        String parent = request.getHeader(TRACEPARENT_HEADER);
        if (isValidTraceparent(parent)) {
            // 取出中间的 trace-id 段
            String[] parts = parent.split("-");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        String legacy = request.getHeader(TRACE_ID_HEADER);
        if (legacy != null && !legacy.isBlank()) {
            return legacy;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 从 traceparent 解析 span-id，或为此 trace 生成新 span-id（每请求唯一）。
     */
    String parseOrGenerateSpanId(HttpServletRequest request, String traceId) {
        String parent = request.getHeader(TRACEPARENT_HEADER);
        if (isValidTraceparent(parent)) {
            String[] parts = parent.split("-");
            if (parts.length >= 3) {
                return parts[2];
            }
        }
        return generateSpanId(traceId);
    }

    /**
     * 生成新的 16-char hex span-id（每个请求唯一）。
     * W3C Trace Context 要求每请求唯一 span-id，不再缓存。
     */
    String generateSpanId(String traceId) {
        return randomSpanId();
    }

    /** 生成 16-char 随机 hex span-id */
    private String randomSpanId() {
        byte[] bytes = new byte[8];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(16);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** 构建 traceparent 响应值（用于下游传播） */
    public static String buildTraceparent(String traceId, String spanId) {
        return "00-" + traceId + "-" + spanId + "-01";
    }

    /** 从 traceparent 判断是否为有效 W3C 格式 */
    boolean isValidTraceparent(String header) {
        if (header == null || header.length() < 55) return false;
        return header.startsWith("00-") && header.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return p.startsWith("/actuator") || p.startsWith("/health") || p.startsWith("/v1/health") || p.startsWith("/favicon");
    }
}