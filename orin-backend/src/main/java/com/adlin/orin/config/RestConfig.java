package com.adlin.orin.config;

import com.adlin.orin.security.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * RestTemplate 配置 — 全局注入 traceparent 传播拦截器。
 *
 * 所有通过 RestTemplate 发出的 HTTP 请求（到 AI Engine、外部 API 等）
 * 都会自动携带 W3C traceparent 和 X-Trace-Id header。
 *
 * 链路跟踪：前端 → ORIN Backend → AI Engine（Python）
 *   前端通过 traceparent 传入 ORIN，ORIN 将其透传到 AI Engine，
 *   AI Engine 返回时携带同一 traceparent，实现端到端链路追踪。
 */
@Configuration
public class RestConfig {

    @Value("${resttemplate.trace propagation.enabled:true}")
    private boolean tracePropagationEnabled;

    @Bean
    public RestTemplate restTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);

        RestTemplate template = new RestTemplate(factory);

        // Insert trace propagation interceptor as first interceptor
        template.setInterceptors(java.util.List.of(
            new TracePropagationInterceptor()
        ));

        return template;
    }

    /**
     * 从当前 HTTP 请求（ThreadLocal）中获取 traceId。
     * MDC 中的是当前 span 的 traceId（由 TraceIdFilter 写入），
     * 但 inbound traceparent 中的 traceId 才是真正的链路根 ID。
     * 我们以 inbound traceparent 为准（如果存在），否则 fallback 到 MDC。
     */
    private String resolveTraceId() {
        // Try ThreadLocal servlet request first (current span's traceId)
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String inboundTraceparent = request.getHeader(TraceIdFilter.TRACEPARENT_HEADER);
                if (inboundTraceparent != null && inboundTraceparent.length() >= 55) {
                    // traceparent = 00-<traceId>-<spanId>-<traceFlags>
                    // 00- is 3 chars, traceId is 32 chars, starts at index 3
                    return inboundTraceparent.substring(3, 35);
                }
                String legacyTraceId = request.getHeader(TraceIdFilter.TRACE_ID_HEADER);
                if (legacyTraceId != null && !legacyTraceId.isBlank()) {
                    return legacyTraceId;
                }
            }
        } catch (Exception ignored) {
        }

        // Fallback to MDC
        return MDC.get(TraceIdFilter.TRACE_ID_KEY);
    }

    private String resolveSpanId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String inboundTraceparent = request.getHeader(TraceIdFilter.TRACEPARENT_HEADER);
                if (inboundTraceparent != null && inboundTraceparent.length() >= 55) {
                    // spanId starts at index 36 (after 00- + 32-char traceId + -)
                    return inboundTraceparent.substring(36, 52);
                }
            }
        } catch (Exception ignored) {
        }
        return MDC.get(TraceIdFilter.SPAN_ID_KEY);
    }

    /**
     * Trace 传播拦截器 — 为所有出站 HTTP 请求注入 traceparent。
     *
     * 传播策略：
     * - 如果 inbound 有 traceparent，以 inbound traceId 为准生成新的 traceparent
     * - span-id 每请求重新生成（不是 inbound 的 span-id）
     * - trace-flags = 01（sampled）
     */
    private class TracePropagationInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                             ClientHttpRequestExecution execution) throws IOException {
            if (!tracePropagationEnabled) {
                return execution.execute(request, body);
            }

            String traceId = resolveTraceId();
            String spanId = generateSpanId(traceId);

            if (traceId != null && !traceId.isBlank()) {
                request.getHeaders().set(TraceIdFilter.TRACEPARENT_HEADER,
                    buildTraceparent(traceId, spanId));
                request.getHeaders().set(TraceIdFilter.TRACE_ID_HEADER, traceId);
                request.getHeaders().set(TraceIdFilter.SPAN_ID_HEADER, spanId);
            }

            return execution.execute(request, body);
        }

        private String buildTraceparent(String traceId, String spanId) {
            // W3C Trace Context: version(2)-traceId(32)-spanId(16)-flags(2)
            // traceId is 32 hex chars, spanId is 16 hex chars
            return "00-" + traceId + "-" + spanId + "-01";
        }

        private String generateSpanId(String traceId) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                String input = traceId + System.nanoTime();
                byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
                // Use first 8 bytes = 16 hex chars
                byte[] spanBytes = new byte[8];
                System.arraycopy(hash, 0, spanBytes, 0, 8);
                return HexFormat.of().formatHex(spanBytes);
            } catch (NoSuchAlgorithmException e) {
                // SHA-256 always available in JDK
                return String.format("%016x", Math.abs(System.nanoTime()));
            }
        }
    }
}