package com.adlin.orin.modules.gateway.filter;

import com.adlin.orin.modules.apikey.entity.ApiKey;
import com.adlin.orin.modules.apikey.service.ApiKeyService;
import com.adlin.orin.modules.gateway.config.GatewayStatsService;
import com.adlin.orin.modules.gateway.entity.GatewayAuditLog;
import com.adlin.orin.modules.gateway.entity.GatewayRetryPolicy;
import com.adlin.orin.modules.gateway.entity.GatewayRoute;
import com.adlin.orin.modules.gateway.repository.GatewayAuditLogRepository;
import com.adlin.orin.modules.gateway.repository.GatewayRetryPolicyRepository;
import com.adlin.orin.modules.gateway.service.GatewayAclService;
import com.adlin.orin.modules.gateway.service.GatewayCircuitBreakerService;
import com.adlin.orin.modules.gateway.service.GatewayRateLimiterService;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 统一网关过滤器 —— 全流量入口
 *
 * 执行顺序（所有请求）：
 *   ① 全局 ACL 检查         —— 不论路由是否匹配
 *   ② 路由匹配
 *   ③ 若无匹配路由           —— 放行到 Spring MVC（由 WebConfig 拦截器兜底）
 *   ④ 若匹配本地路由 (LOCAL) —— ACL ✓、认证、路由级限流、审计 → chain.doFilter()
 *   ⑤ 若匹配代理路由 (PROXY) —— ACL ✓、认证、路由级限流、熔断、重试 → 转发上游
 *
 * 本地路由 (LOCAL)：targetUrl 为空且 serviceId 为空的路由配置项。
 * 用于对 ORIN 自身端点（/api/v1/**、/v1/**）施加 ACL、认证、路由级限流和审计，
 * 不做代理转发，最终仍由本地 Controller 处理。
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
    private final GatewayRateLimiterService rateLimiterService;
    private final GatewayCircuitBreakerService circuitBreakerService;
    private final GatewayRetryPolicyRepository retryPolicyRepository;

    private static final String ATTR_ROUTE = "gateway.route";
    private static final String ATTR_TARGET_URL = "gateway.targetUrl";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade");

    // =========================================================================
    // 主过滤逻辑
    // =========================================================================

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();
        String clientIp = extractClientIp(httpRequest);
        String traceId = resolveTraceId(httpRequest);

        log.debug("Gateway: {} {}", method, path);

        // ① 全局 ACL —— 所有请求都要过，不论是否匹配路由
        Map<String, Object> aclDecision = aclService.testIp(clientIp, path);
        if (!"ALLOW".equals(aclDecision.get("action"))) {
            log.warn("ACL denied (global): {} {}", method, path);
            statsService.incrementErrorCount();
            writeError(httpRequest, httpResponse, HttpStatus.FORBIDDEN, "ACL denied", traceId);
            return;
        }

        // ② 路由匹配
        Optional<GatewayRuntimeRoutingService.ResolvedRoute> resolvedRouteOpt =
                routingService.resolveRoute(path, method, httpRequest.getQueryString());

        if (resolvedRouteOpt.isEmpty()) {
            // 无匹配路由：全局 ACL 已通过，放行到下游（WebConfig 拦截器继续兜底）
            log.debug("No route matched, passthrough: {} {}", method, path);
            chain.doFilter(request, response);
            return;
        }

        GatewayRuntimeRoutingService.ResolvedRoute resolvedRoute = resolvedRouteOpt.get();
        GatewayRoute matchedRoute = resolvedRoute.getRoute();
        statsService.incrementRequestCount();

        boolean isLocal = isLocalRoute(matchedRoute);
        log.info("Route matched: {} [{}]", matchedRoute.getName(), isLocal ? "LOCAL" : "PROXY");

        // ③ API Key 解析（后续认证和限流需要）
        Optional<ApiKey> apiKeyOpt = resolveApiKey(httpRequest);
        boolean hasJwt = hasJwtAuthentication(httpRequest);
        String apiKeyId = apiKeyOpt.map(k -> String.valueOf(k.getId())).orElse(null);

        // ④ 认证检查（本地路由和代理路由均适用）
        if (Boolean.TRUE.equals(matchedRoute.getAuthRequired()) && apiKeyOpt.isEmpty() && !hasJwt) {
            statsService.incrementErrorCount();
            writeError(httpRequest, httpResponse, HttpStatus.UNAUTHORIZED,
                    "Authentication required for this route", traceId);
            saveAuditLog(httpRequest, matchedRoute, resolvedRoute.getTargetUrl(),
                    resolvedRoute.getTargetService(), HttpStatus.UNAUTHORIZED.value(),
                    "DENY", "Missing authentication", 0L);
            return;
        }
        if (Boolean.TRUE.equals(aclDecision.get("apiKeyRequired")) && apiKeyOpt.isEmpty()) {
            statsService.incrementErrorCount();
            writeError(httpRequest, httpResponse, HttpStatus.UNAUTHORIZED,
                    "API key required by ACL rule", traceId);
            saveAuditLog(httpRequest, matchedRoute, resolvedRoute.getTargetUrl(),
                    resolvedRoute.getTargetService(), HttpStatus.UNAUTHORIZED.value(),
                    "DENY", "ACL requires API key", 0L);
            return;
        }

        // ⑤ 路由级限流（本地路由和代理路由均适用）
        if (!rateLimiterService.tryAcquire(matchedRoute, clientIp, apiKeyId)) {
            statsService.incrementErrorCount();
            writeError(httpRequest, httpResponse, HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded", traceId);
            saveAuditLog(httpRequest, matchedRoute, resolvedRoute.getTargetUrl(),
                    resolvedRoute.getTargetService(), HttpStatus.TOO_MANY_REQUESTS.value(),
                    "DENY", "Rate limit exceeded", 0L);
            return;
        }

        // ⑥ 分发到本地或代理处理
        if (isLocal) {
            handleLocalRoute(httpRequest, httpResponse, chain, matchedRoute, traceId);
        } else {
            // 熔断检查（仅代理路由有意义）
            if (!circuitBreakerService.allowRequest(matchedRoute)) {
                statsService.incrementErrorCount();
                writeError(httpRequest, httpResponse, HttpStatus.SERVICE_UNAVAILABLE,
                        "Circuit breaker is open", traceId);
                saveAuditLog(httpRequest, matchedRoute, resolvedRoute.getTargetUrl(),
                        resolvedRoute.getTargetService(), HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "DENY", "Circuit breaker open", 0L);
                return;
            }
            handleProxyRoute(httpRequest, httpResponse, matchedRoute, resolvedRoute, traceId, method);
        }
    }

    // =========================================================================
    // 本地路由处理：策略执行后交给 Spring MVC
    // =========================================================================

    private void handleLocalRoute(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain chain, GatewayRoute route, String traceId)
            throws IOException, ServletException {
        response.setHeader(TRACE_ID_HEADER, traceId);
        long start = System.currentTimeMillis();
        Exception caught = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            caught = e;
        } finally {
            long latency = System.currentTimeMillis() - start;
            int status = (caught != null) ? 500 : response.getStatus();
            // getStatus() 在响应未写入时可能返回 0，默认按 200 处理
            if (status <= 0) status = 200;
            boolean ok = status < 400;
            if (!ok) statsService.incrementErrorCount();
            saveAuditLog(request, route, null, "LOCAL", status,
                    ok ? "SUCCESS" : "ERROR", caught != null ? caught.getMessage() : null, latency);
        }
        // 原样重新抛出，不吞异常
        if (caught instanceof IOException ioe) throw ioe;
        if (caught instanceof ServletException se) throw se;
        if (caught instanceof RuntimeException re) throw re;
    }

    // =========================================================================
    // 代理路由处理：转发到上游服务（含重试）
    // =========================================================================

    private void handleProxyRoute(HttpServletRequest request, HttpServletResponse response,
                                   GatewayRoute route,
                                   GatewayRuntimeRoutingService.ResolvedRoute resolvedRoute,
                                   String traceId, String method) throws IOException {
        String targetUrl = resolvedRoute.getTargetUrl();
        log.info("Proxy to: {} {}", method, targetUrl);

        request.setAttribute(ATTR_ROUTE, route);
        request.setAttribute(ATTR_TARGET_URL, targetUrl);

        long start = System.currentTimeMillis();

        org.springframework.http.HttpMethod httpMethod;
        try {
            httpMethod = org.springframework.http.HttpMethod.valueOf(method.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            writeError(request, response, HttpStatus.METHOD_NOT_ALLOWED,
                    "Unsupported method: " + method, traceId);
            saveAuditLog(request, route, targetUrl, resolvedRoute.getTargetService(),
                    HttpStatus.METHOD_NOT_ALLOWED.value(), "ERROR", "Unsupported method", 0L);
            return;
        }

        int maxAttempts = resolveMaxAttempts(route);
        Set<Integer> retryStatusCodes = resolveRetryStatusCodes(route);
        long retryInitialMs = resolveRetryInitialMs(route);
        double retryBackoff = resolveRetryBackoff(route);

        byte[] requestBody = StreamUtils.copyToByteArray(request.getInputStream());
        HttpEntity<byte[]> entity = new HttpEntity<>(requestBody, buildRequestHeaders(request, route, traceId));
        int timeout = route.getTimeoutMs() != null ? route.getTimeoutMs() : 30000;
        RestTemplate restTemplate = createRestTemplate(timeout);

        ResponseEntity<byte[]> result = null;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                result = restTemplate.exchange(targetUrl, httpMethod, entity, byte[].class);
                if (attempt < maxAttempts && retryStatusCodes.contains(result.getStatusCode().value())) {
                    log.warn("Retry {}/{}: {} {} status={}",
                            attempt, maxAttempts, method, targetUrl, result.getStatusCode().value());
                    sleepBackoff(retryInitialMs, retryBackoff, attempt);
                    lastException = null;
                    continue;
                }
                lastException = null;
                break;
            } catch (RestClientException e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    log.warn("Retry {}/{}: {} {} error={}",
                            attempt, maxAttempts, method, targetUrl, e.getMessage());
                    sleepBackoff(retryInitialMs, retryBackoff, attempt);
                }
            }
        }

        long latency = System.currentTimeMillis() - start;

        if (lastException != null || result == null) {
            String errMsg = lastException != null ? lastException.getMessage() : "No response";
            log.error("Proxy failed after {} attempt(s): {}", maxAttempts, errMsg);
            statsService.incrementErrorCount();
            circuitBreakerService.recordResult(route, false);
            writeError(request, response, HttpStatus.BAD_GATEWAY,
                    "Gateway upstream request failed", traceId);
            saveAuditLog(request, route, targetUrl, resolvedRoute.getTargetService(),
                    HttpStatus.BAD_GATEWAY.value(), "ERROR", errMsg, latency);
            return;
        }

        boolean upstreamSuccess = result.getStatusCode().is2xxSuccessful();
        circuitBreakerService.recordResult(route, upstreamSuccess);

        response.setStatus(result.getStatusCode().value());
        result.getHeaders().forEach((name, values) -> {
            if (!isHopByHopHeader(name)) values.forEach(v -> response.addHeader(name, v));
        });
        response.setHeader(TRACE_ID_HEADER, traceId);
        if (result.getBody() != null) response.getOutputStream().write(result.getBody());

        saveAuditLog(request, route, targetUrl, resolvedRoute.getTargetService(),
                result.getStatusCode().value(), upstreamSuccess ? "SUCCESS" : "ERROR", null, latency);
        if (!upstreamSuccess) statsService.incrementErrorCount();
    }

    // =========================================================================
    // 工具方法
    // =========================================================================

    /** 本地路由：无 targetUrl 且无 serviceId，不做代理，仅执行策略后放行到 Spring MVC */
    private boolean isLocalRoute(GatewayRoute route) {
        return (route.getTargetUrl() == null || route.getTargetUrl().isBlank())
                && route.getServiceId() == null;
    }

    private int resolveMaxAttempts(GatewayRoute route) {
        if (route.getRetryPolicyId() != null) {
            return retryPolicyRepository.findById(route.getRetryPolicyId())
                    .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                    .map(GatewayRetryPolicy::getMaxAttempts)
                    .orElse(1);
        }
        int retryCount = route.getRetryCount() != null ? route.getRetryCount() : 0;
        return 1 + retryCount;
    }

    private Set<Integer> resolveRetryStatusCodes(GatewayRoute route) {
        if (route.getRetryPolicyId() != null) {
            return retryPolicyRepository.findById(route.getRetryPolicyId())
                    .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                    .map(p -> parseStatusCodes(p.getRetryOnStatusCodes()))
                    .orElse(Set.of(500, 502, 503, 504));
        }
        return Set.of(500, 502, 503, 504);
    }

    private long resolveRetryInitialMs(GatewayRoute route) {
        if (route.getRetryPolicyId() != null) {
            return retryPolicyRepository.findById(route.getRetryPolicyId())
                    .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                    .map(p -> p.getInitialIntervalMs() != null ? p.getInitialIntervalMs().longValue() : 100L)
                    .orElse(100L);
        }
        return 100L;
    }

    private double resolveRetryBackoff(GatewayRoute route) {
        if (route.getRetryPolicyId() != null) {
            return retryPolicyRepository.findById(route.getRetryPolicyId())
                    .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                    .map(p -> p.getBackoffMultiplier() != null ? p.getBackoffMultiplier() : 2.0)
                    .orElse(2.0);
        }
        return 2.0;
    }

    private Set<Integer> parseStatusCodes(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        Set<Integer> codes = new HashSet<>();
        for (String s : csv.split(",")) {
            try { codes.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
        }
        return codes;
    }

    private void sleepBackoff(long initialMs, double multiplier, int attempt) {
        long delay = (long) (initialMs * Math.pow(multiplier, attempt - 1));
        try { Thread.sleep(Math.min(delay, 5000)); } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
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
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();
        Optional<ApiKey> validated = apiKeyService.validateApiKey(apiKey);
        validated.ifPresent(value -> request.setAttribute("apiKey", value));
        return validated;
    }

    private String extractApiKey(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (token.startsWith("sk-orin-")) return token;
        }
        String headerApiKey = request.getHeader("X-API-Key");
        if (headerApiKey != null && !headerApiKey.isBlank()) return headerApiKey;
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
            return authHeader.substring(7).startsWith("eyJ");
        }
        return false;
    }

    private HttpHeaders buildRequestHeaders(HttpServletRequest request, GatewayRoute route, String traceId) {
        HttpHeaders headers = new HttpHeaders();
        request.getHeaderNames().asIterator().forEachRemaining(name -> {
            if (!isHopByHopHeader(name)) {
                Enumeration<String> values = request.getHeaders(name);
                while (values.hasMoreElements()) headers.add(name, values.nextElement());
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
        if (traceId == null || traceId.isBlank()) traceId = MDC.get(TRACE_ID_MDC_KEY);
        if (traceId == null || traceId.isBlank()) traceId = UUID.randomUUID().toString();
        return traceId;
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            HttpStatus status, String message, String traceId) throws IOException {
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

    private void saveAuditLog(HttpServletRequest request, GatewayRoute route,
                               String targetUrl, String targetService,
                               Integer statusCode, String result,
                               String errorMessage, long latencyMs) {
        try {
            ApiKey apiKey = null;
            Object apiKeyObj = request.getAttribute("apiKey");
            if (apiKeyObj instanceof ApiKey value) apiKey = value;

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
