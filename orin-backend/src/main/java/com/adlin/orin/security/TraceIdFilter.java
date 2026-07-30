package com.adlin.orin.security;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Trace ID 过滤器
 * 从请求头获取或生成 Trace ID，设置到 MDC 用于日志关联，
 * 同时创建 OpenTelemetry Span 进行分布式追踪
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Autowired(required = false)
    private Tracer tracer;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 获取或生成 Trace ID
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = request.getHeader(REQUEST_ID_HEADER);
        }
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }

        // 设置到 MDC 用于日志关联
        MDC.put(TRACE_ID_MDC_KEY, traceId);

        // 设置响应头
        response.setHeader(TRACE_ID_HEADER, traceId);
        response.setHeader(REQUEST_ID_HEADER, traceId);
        response.setHeader("X-Span-Id", "");

        // 如果有 Tracer，创建 Span
        if (tracer != null) {
            executeWithTracing(request, response, filterChain, traceId);
        } else {
            // 无 Tracer 时使用原有逻辑
            try {
                filterChain.doFilter(request, response);
            } finally {
                MDC.remove(TRACE_ID_MDC_KEY);
            }
        }
    }

    private void executeWithTracing(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain, String traceId) {
        String spanName = request.getMethod() + " " + request.getRequestURI();

        Span span = tracer.spanBuilder(spanName)
                .setAttribute("http.method", request.getMethod())
                .setAttribute("http.url", request.getRequestURL().toString())
                .setAttribute("http.target", request.getRequestURI())
                .setAttribute("http.scheme", request.getScheme())
                .setAttribute("thread.name", Thread.currentThread().getName())
                .setAttribute("request.traceId", traceId)
                .startSpan();

        // 更新响应头
        response.setHeader("X-Span-Id", span.getSpanContext().getSpanId());

        try (Scope scope = span.makeCurrent()) {
            filterChain.doFilter(request, response);

            // 记录响应状态
            int status = response.getStatus();
            if (status >= 400) {
                span.setStatus(StatusCode.ERROR, "HTTP " + status);
                span.setAttribute("error", true);
                span.setAttribute("http.status_code", status);
            }
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            log.error("Request failed: {}", e.getMessage());
        } finally {
            span.end();
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 排除健康检查和 actuator 端点
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/health");
    }
}
