package com.adlin.orin.modules.gateway.filter;

import com.adlin.orin.modules.apikey.entity.ApiKey;
import com.adlin.orin.modules.apikey.service.ApiKeyService;
import com.adlin.orin.modules.gateway.config.GatewayStatsService;
import com.adlin.orin.modules.gateway.entity.GatewayAuditLog;
import com.adlin.orin.modules.gateway.entity.GatewayRoute;
import com.adlin.orin.modules.gateway.repository.GatewayAuditLogRepository;
import com.adlin.orin.modules.gateway.service.GatewayAclService;
import com.adlin.orin.modules.gateway.service.GatewayRuntimeRoutingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 网关请求转发过滤器
 * 根据路由配置将请求转发到目标服务
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GatewayProxyFilter implements Filter {

    private final GatewayAclService aclService;
    private final GatewayRuntimeRoutingService routingService;
    private final GatewayStatsService statsService;
    private final GatewayAuditLogRepository auditLogRepository;
    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    private static final String ATTR_ROUTE = "gateway.route";
    private static final String ATTR_TARGET_URL = "gateway.targetUrl";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        log.debug("Gateway processing: {} {}", method, path);

        Optional<GatewayRuntimeRoutingService.ResolvedRoute> resolvedRouteOpt =
                routingService.resolveRoute(path, method, httpRequest.getQueryString());
        if (resolvedRouteOpt.isEmpty()) {
            // 没有匹配的路由，继续下游
            log.debug("No route matched for: {} {}", method, path);
            chain.doFilter(request, response);
            return;
        }

        GatewayRuntimeRoutingService.ResolvedRoute resolvedRoute = resolvedRouteOpt.get();
        GatewayRoute matchedRoute = resolvedRoute.getRoute();
        statsService.incrementRequestCount();

        log.info("Route matched: {} -> {}", matchedRoute.getName(), matchedRoute.getTargetUrl());

        // ACL 检查
        String clientIp = extractClientIp(httpRequest);
        Map<String, Object> aclDecision = aclService.testIp(clientIp, path);
        if (!"ALLOW".equals(aclDecision.get("action"))) {
            log.warn("ACL denied: {} {}", method, path);
            statsService.incrementErrorCount();
            writeError(httpRequest, httpResponse, HttpStatus.FORBIDDEN, "ACL denied", resolveTraceId(httpRequest));
            saveAuditLog(httpRequest, matchedRoute, resolvedRoute.getTargetUrl(), resolvedRoute.getTargetService(),
                    HttpStatus.FORBIDDEN.value(), "DENY", "ACL denied", 0L);
            return;
        }

        Optional<ApiKey> apiKeyOpt = resolveApiKey(httpRequest);
        boolean hasJwt = hasJwtAuthentication(httpRequest);

        if (Boolean.TRUE.equals(matchedRoute.getAuthRequired()) && apiKeyOpt.isEmpty() && !hasJwt) {
            statsService.incrementErrorCount();
            writeError(httpRequest, httpResponse, HttpStatus.UNAUTHORIZED,
                    "Authentication required for this route", resolveTraceId(httpRequest));
            saveAuditLog(httpRequest, matchedRoute, resolvedRoute.getTargetUrl(), resolvedRoute.getTargetService(),
                    HttpStatus.UNAUTHORIZED.value(), "DENY", "Missing authentication", 0L);
            return;
        }

        if (Boolean.TRUE.equals(aclDecision.get("apiKeyRequired")) && apiKeyOpt.isEmpty()) {
            statsService.incrementErrorCount();
            writeError(httpRequest, httpResponse, HttpStatus.UNAUTHORIZED,
                    "API key required by ACL rule", resolveTraceId(httpRequest));
            saveAuditLog(httpRequest, matchedRoute, resolvedRoute.getTargetUrl(), resolvedRoute.getTargetService(),
                    HttpStatus.UNAUTHORIZED.value(), "DENY", "ACL requires API key", 0L);
            return;
        }

        String targetUrl = resolvedRoute.getTargetUrl();
        log.info("Proxy to: {} {}", method, targetUrl);

        // 存储到request属性
        httpRequest.setAttribute(ATTR_ROUTE, matchedRoute);
        httpRequest.setAttribute(ATTR_TARGET_URL, targetUrl);

        // 调用目标服务
        long start = System.currentTimeMillis();
        String traceId = resolveTraceId(httpRequest);
        try {
            int timeout = matchedRoute.getTimeoutMs() != null ? matchedRoute.getTimeoutMs() : 30000;
            org.springframework.http.HttpMethod httpMethod;
            try {
                httpMethod = org.springframework.http.HttpMethod.valueOf(method.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                writeError(httpRequest, httpResponse, HttpStatus.METHOD_NOT_ALLOWED,
                        "Unsupported method: " + method, traceId);
                saveAuditLog(httpRequest, matchedRoute, targetUrl, resolvedRoute.getTargetService(),
                        HttpStatus.METHOD_NOT_ALLOWED.value(), "ERROR", "Unsupported method", 0L);
                return;
            }

            byte[] requestBody = StreamUtils.copyToByteArray(httpRequest.getInputStream());
            HttpEntity<byte[]> entity = new HttpEntity<>(requestBody, buildRequestHeaders(httpRequest, matchedRoute, traceId));

            RestTemplate restTemplate = createRestTemplate(timeout);
            ResponseEntity<byte[]> result = restTemplate.exchange(
                    targetUrl,
                    httpMethod,
                    entity,
                    byte[].class
            );

            // 处理响应
            httpResponse.setStatus(result.getStatusCode().value());
            result.getHeaders().forEach((name, values) -> {
                if (!isHopByHopHeader(name)) {
                    values.forEach(v -> httpResponse.addHeader(name, v));
                }
            });
            httpResponse.setHeader(TRACE_ID_HEADER, traceId);

            if (result.getBody() != null) {
                httpResponse.getOutputStream().write(result.getBody());
            }
            long latency = System.currentTimeMillis() - start;
            saveAuditLog(httpRequest, matchedRoute, targetUrl, resolvedRoute.getTargetService(),
                    result.getStatusCode().value(),
                    result.getStatusCode().is2xxSuccessful() ? "SUCCESS" : "ERROR",
                    null, latency);
            if (!result.getStatusCode().is2xxSuccessful()) {
                statsService.incrementErrorCount();
            }

        } catch (RestClientException e) {
            log.error("Proxy failed: {} - {}", targetUrl, e.getMessage());
            statsService.incrementErrorCount();
            writeError(httpRequest, httpResponse, HttpStatus.BAD_GATEWAY,
                    "Gateway upstream request failed", traceId);
            long latency = System.currentTimeMillis() - start;
            saveAuditLog(httpRequest, matchedRoute, targetUrl, resolvedRoute.getTargetService(),
                    HttpStatus.BAD_GATEWAY.value(), "ERROR", e.getMessage(), latency);
        }
    }

    private RestTemplate createRestTemplate(int timeoutMs) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }

    private Optional<ApiKey> resolveApiKey(HttpServletRequest request) {
        Object apiKeyObj = request.getAttribute("apiKey");
        if (apiKeyObj instanceof ApiKey apiKey) {
            return Optional.of(apiKey);
        }

        String apiKey = extractApiKey(request);
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        Optional<ApiKey> validated = apiKeyService.validateApiKey(apiKey);
        validated.ifPresent(value -> request.setAttribute("apiKey", value));
        return validated;
    }

    private String extractApiKey(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (token.startsWith("sk-orin-")) {
                return token;
            }
        }

        String headerApiKey = request.getHeader("X-API-Key");
        if (headerApiKey != null && !headerApiKey.isBlank()) {
            return headerApiKey;
        }
        return null;
    }

    private boolean hasJwtAuthentication(HttpServletRequest request) {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return token.startsWith("eyJ");
        }
        return false;
    }

    private HttpHeaders buildRequestHeaders(HttpServletRequest request, GatewayRoute route, String traceId) {
        HttpHeaders headers = new HttpHeaders();
        request.getHeaderNames().asIterator().forEachRemaining(name -> {
            if (!isHopByHopHeader(name)) {
                Enumeration<String> values = request.getHeaders(name);
                while (values.hasMoreElements()) {
                    headers.add(name, values.nextElement());
                }
            }
        });

        headers.set(TRACE_ID_HEADER, traceId);
        headers.add("X-Forwarded-For", request.getRemoteAddr());
        headers.add("X-Original-URI", request.getRequestURI());
        headers.add("X-Gateway-Route", route.getName());
        return headers;
    }

    private boolean isHopByHopHeader(String name) {
        return name != null && HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT));
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get(TRACE_ID_MDC_KEY);
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        return traceId;
    }

    private void writeError(HttpServletRequest request,
                            HttpServletResponse response,
                            HttpStatus status,
                            String message,
                            String traceId) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader(TRACE_ID_HEADER, traceId);

        Map<String, Object> body = new HashMap<>();
        body.put("code", String.valueOf(status.value()));
        body.put("message", message);
        body.put("path", request.getRequestURI());
        body.put("traceId", traceId);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private void saveAuditLog(HttpServletRequest request,
                              GatewayRoute route,
                              String targetUrl,
                              String targetService,
                              Integer statusCode,
                              String result,
                              String errorMessage,
                              long latencyMs) {
        try {
            ApiKey apiKey = null;
            Object apiKeyObj = request.getAttribute("apiKey");
            if (apiKeyObj instanceof ApiKey value) {
                apiKey = value;
            }

            GatewayAuditLog logEntity = GatewayAuditLog.builder()
                    .routeId(route.getId())
                    .traceId(resolveTraceId(request))
                    .method(request.getMethod())
                    .path(request.getRequestURI())
                    .targetService(targetService)
                    .targetUrl(targetUrl)
                    .statusCode(statusCode)
                    .latencyMs(latencyMs)
                    .clientIp(extractClientIp(request))
                    .userAgent(request.getHeader("User-Agent"))
                    .apiKeyId(apiKey != null ? apiKey.getId() : null)
                    .result(result)
                    .errorMessage(errorMessage)
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(logEntity);
        } catch (Exception ex) {
            log.warn("Failed to save gateway audit log: {}", ex.getMessage());
        }
    }
}
