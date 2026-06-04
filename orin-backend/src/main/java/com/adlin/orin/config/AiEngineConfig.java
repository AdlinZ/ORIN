package com.adlin.orin.config;

import com.adlin.orin.security.TraceIdFilter;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Configuration
public class AiEngineConfig {

    @Value("${orin.ai-engine.url:http://localhost:8000}")
    private String aiEngineUrl;

    @Bean
    public WebClient aiEngineWebClient(WebClient.Builder builder) {
        return builder.baseUrl(aiEngineUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .filter(tracePropagationWebClientFilter())
                .build();
    }

    private ExchangeFilterFunction tracePropagationWebClientFilter() {
        return (request, next) -> {
            String traceId = resolveTraceId();
            String spanId = generateSpanId(traceId);
            if (traceId != null && !traceId.isBlank()) {
                request.headers().set(TraceIdFilter.TRACEPARENT_HEADER,
                    "00-" + traceId + "-" + spanId + "-01");
                request.headers().set(TraceIdFilter.TRACE_ID_HEADER, traceId);
                request.headers().set(TraceIdFilter.SPAN_ID_HEADER, spanId);
            }
            return next.exchange(request);
        };
    }

    private String resolveTraceId() {
        try {
            jakarta.servlet.http.HttpServletRequest req =
                ((org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes())
                    .getRequest();
            String tp = req.getHeader(TraceIdFilter.TRACEPARENT_HEADER);
            if (tp != null && tp.length() >= 35) {
                return tp.substring(3, 35);
            }
            String legacy = req.getHeader(TraceIdFilter.TRACE_ID_HEADER);
            if (legacy != null && !legacy.isBlank()) {
                return legacy;
            }
        } catch (Exception ignored) {
        }
        return MDC.get(TraceIdFilter.TRACE_ID_KEY);
    }

    private String generateSpanId(String traceId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((traceId + System.nanoTime()).getBytes(StandardCharsets.UTF_8));
            byte[] spanBytes = new byte[8];
            System.arraycopy(hash, 0, spanBytes, 0, 8);
            return HexFormat.of().formatHex(spanBytes);
        } catch (NoSuchAlgorithmException e) {
            return String.format("%016x", Math.abs(System.nanoTime()));
        }
    }
}
