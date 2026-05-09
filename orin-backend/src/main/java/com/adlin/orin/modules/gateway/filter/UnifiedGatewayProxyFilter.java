package com.adlin.orin.modules.gateway.filter;

import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.service.GatewaySecretService;
import com.adlin.orin.modules.gateway.config.UnifiedGatewayStatsService;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayAuditLog;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayRetryPolicy;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayRoute;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayAuditLogRepository;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayRetryPolicyRepository;
import com.adlin.orin.modules.gateway.service.UnifiedGatewayAclService;
import com.adlin.orin.modules.gateway.service.UnifiedGatewayCircuitBreakerService;
import com.adlin.orin.modules.gateway.service.UnifiedGatewayRateLimiterService;
import com.adlin.orin.modules.gateway.service.UnifiedGatewayRuntimeRoutingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StreamUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
public class UnifiedGatewayProxyFilter implements Filter {

    private final UnifiedGatewayAclService aclService;
    private final UnifiedGatewayRuntimeRoutingService routingService;
    private final UnifiedGatewayStatsService statsService;
    private final UnifiedGatewayAuditLogRepository auditLogRepository;
    private final GatewaySecretService gatewaySecretService;
    private final ObjectMapper objectMapper;
    private final UnifiedGatewayRateLimiterService rateLimiterService;
    private final UnifiedGatewayCircuitBreakerService circuitBreakerService;
    private final UnifiedGatewayRetryPolicyRepository retryPolicyRepository;
    private WebClient webClient = buildWebClient(10 * 1024 * 1024);

    @Value("${orin.gateway.proxy.max-body-bytes:10485760}")
    private int maxProxyBodyBytes = 10 * 1024 * 1024;

    private static final String ATTR_ROUTE = "gateway.route";
    private static final String ATTR_TARGET_URL = "gateway.targetUrl";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final String TARGET_BASELINE_GOVERNED = "BASELINE_GOVERNED";
    private static final String TARGET_RESCUE_RESERVED = "RESCUE_RESERVED";
    private static final List<String> RESCUE_PATTERNS = List.of(
            "/api/v1/auth/**",
            "/api/v1/health",
            "/api/v1/workflow/**",
            "/api/v1/system/gateway/**");
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade");
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @PostConstruct
    void initWebClient() {
        this.webClient = buildWebClient(maxProxyBodyBytes);
    }

    private static WebClient buildWebClient(int maxBodyBytes) {
        return WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxBodyBytes))
                .build();
    }

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
        httpResponse.setHeader(TRACE_ID_HEADER, traceId);

        log.debug("UnifiedGateway: {} {}", method, path);

        if (!isUnifiedGatewaySurface(path)) {
            chain.doFilter(request, response);
            return;
        }

        if (isRescuePath(path)) {
            handleRescueReservedRoute(httpRequest, httpResponse, chain, traceId);
            return;
        }

        // ① 全局 ACL —— 所有请求都要过，不论是否匹配路由
        Map<String, Object> aclDecision = aclService.testIp(clientIp, path);
        if (!"ALLOW".equals(aclDecision.get("action"))) {
            log.warn("ACL denied (global): {} {}", method, path);
            statsService.incrementErrorCount();
            writeError(httpRequest, httpResponse, HttpStatus.FORBIDDEN, "ACL denied", traceId);
            return;
        }

        // ② 路由匹配
        Optional<UnifiedGatewayRuntimeRoutingService.ResolvedRoute> resolvedRouteOpt =
                routingService.resolveRoute(path, method, httpRequest.getQueryString());

        if (resolvedRouteOpt.isEmpty()) {
            // 无匹配路由：控制面先观测审计，不阻断；WebConfig/Spring Security 继续兜底。
            log.debug("No route matched, passthrough: {} {}", method, path);
            if (isControlPlanePath(path)) {
                handleBaselineGovernedRoute(httpRequest, httpResponse, chain, traceId);
            } else {
                chain.doFilter(request, response);
            }
            return;
        }

        UnifiedGatewayRuntimeRoutingService.ResolvedRoute resolvedRoute = resolvedRouteOpt.get();
        UnifiedGatewayRoute matchedRoute = resolvedRoute.getRoute();
        statsService.incrementRequestCount();

        boolean isLocal = isLocalRoute(matchedRoute, resolvedRoute);
        log.info("Route matched: {} [{}]", matchedRoute.getName(), isLocal ? "LOCAL" : "PROXY");

        // ③ API Key 解析（后续认证和限流需要）
        Optional<GatewaySecret> apiKeyOpt = resolveApiKey(httpRequest);
        boolean hasJwt = hasJwtAuthentication(httpRequest);
        String apiKeyId = apiKeyOpt.map(GatewaySecret::getSecretId).orElse(null);

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

    private void handleRescueReservedRoute(HttpServletRequest request, HttpServletResponse response,
                                           FilterChain chain, String traceId)
            throws IOException, ServletException {
        handlePassthroughAudit(request, response, chain, traceId, TARGET_RESCUE_RESERVED,
                "Rescue control-plane endpoint bypassed gateway route governance");
    }

    private void handleBaselineGovernedRoute(HttpServletRequest request, HttpServletResponse response,
                                         FilterChain chain, String traceId)
            throws IOException, ServletException {
        handlePassthroughAudit(request, response, chain, traceId, TARGET_BASELINE_GOVERNED,
                "Control-plane endpoint passed through baseline gateway governance");
    }

    private void handlePassthroughAudit(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain, String traceId, String targetService,
                                        String message)
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
            int status = caught != null ? 500 : response.getStatus();
            if (status <= 0) status = 200;
            if (status >= 400) {
                statsService.incrementErrorCount();
            }
            saveAuditLog(request, null, null, targetService, status,
                    targetService, message, latency);
        }
        if (caught instanceof IOException ioe) throw ioe;
        if (caught instanceof ServletException se) throw se;
        if (caught instanceof RuntimeException re) throw re;
    }

    private void handleLocalRoute(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain chain, UnifiedGatewayRoute route, String traceId)
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
                                   UnifiedGatewayRoute route,
                                   UnifiedGatewayRuntimeRoutingService.ResolvedRoute resolvedRoute,
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

        long contentLength = request.getContentLengthLong();
        if (contentLength > maxProxyBodyBytes) {
            writeError(request, response, HttpStatus.PAYLOAD_TOO_LARGE,
                    "Request body exceeds gateway proxy limit", traceId);
            saveAuditLog(request, route, targetUrl, resolvedRoute.getTargetService(),
                    HttpStatus.PAYLOAD_TOO_LARGE.value(), "ERROR", "Request body too large", 0L);
            statsService.incrementErrorCount();
            return;
        }

        byte[] requestBody = StreamUtils.copyToByteArray(request.getInputStream());
        if (requestBody.length > maxProxyBodyBytes) {
            writeError(request, response, HttpStatus.PAYLOAD_TOO_LARGE,
                    "Request body exceeds gateway proxy limit", traceId);
            saveAuditLog(request, route, targetUrl, resolvedRoute.getTargetService(),
                    HttpStatus.PAYLOAD_TOO_LARGE.value(), "ERROR", "Request body too large", 0L);
            statsService.incrementErrorCount();
            return;
        }
        HttpHeaders requestHeaders = buildRequestHeaders(request, route, traceId);
        int timeout = route.getTimeoutMs() != null ? route.getTimeoutMs() : 30000;

        byte[] responseBody = null;
        org.springframework.http.HttpStatus responseStatus = null;
        org.springframework.http.HttpHeaders responseHeaders = null;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                org.springframework.web.reactive.function.client.ClientResponse clientResponse =
                        webClient.method(httpMethod)
                                .uri(targetUrl)
                                .headers(headers -> headers.addAll(requestHeaders))
                                .bodyValue(requestBody)
                                .exchange()
                                .block(java.time.Duration.ofMillis(timeout));

                if (clientResponse == null) {
                    lastException = new IOException("No response from upstream");
                    if (attempt < maxAttempts) {
                        sleepBackoff(retryInitialMs, retryBackoff, attempt);
                        continue;
                    }
                    break;
                }

                responseStatus = org.springframework.http.HttpStatus.valueOf(clientResponse.statusCode().value());
                responseHeaders = clientResponse.headers().asHttpHeaders();
                responseBody = clientResponse.bodyToMono(byte[].class).block(java.time.Duration.ofMillis(timeout));
                if (responseBody != null && responseBody.length > maxProxyBodyBytes) {
                    throw new IOException("Upstream response body exceeds gateway proxy limit");
                }

                if (attempt < maxAttempts && retryStatusCodes.contains(responseStatus.value())) {
                    log.warn("Retry {}/{}: {} {} status={}",
                            attempt, maxAttempts, method, targetUrl, responseStatus.value());
                    sleepBackoff(retryInitialMs, retryBackoff, attempt);
                    continue;
                }
                lastException = null;
                break;
            } catch (WebClientResponseException e) {
                lastException = e;
                responseStatus = org.springframework.http.HttpStatus.valueOf(e.getStatusCode().value());
                if (attempt < maxAttempts && retryStatusCodes.contains(e.getStatusCode().value())) {
                    log.warn("Retry {}/{}: {} {} error={}",
                            attempt, maxAttempts, method, targetUrl, e.getMessage());
                    sleepBackoff(retryInitialMs, retryBackoff, attempt);
                } else {
                    break;
                }
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    log.warn("Retry {}/{}: {} {} error={}",
                            attempt, maxAttempts, method, targetUrl, e.getMessage());
                    sleepBackoff(retryInitialMs, retryBackoff, attempt);
                }
            }
        }

        long latency = System.currentTimeMillis() - start;

        if (lastException != null || responseStatus == null) {
            String errMsg = lastException != null ? lastException.getMessage() : "No response";
            log.error("Proxy failed after {} attempt(s): {}", maxAttempts, errMsg);
            statsService.incrementErrorCount();
            circuitBreakerService.recordResult(route, false);
            writeError(request, response, HttpStatus.BAD_GATEWAY,
                    "UnifiedGateway upstream request failed", traceId);
            saveAuditLog(request, route, targetUrl, resolvedRoute.getTargetService(),
                    HttpStatus.BAD_GATEWAY.value(), "ERROR", errMsg, latency);
            return;
        }

        boolean upstreamSuccess = responseStatus.is2xxSuccessful();
        circuitBreakerService.recordResult(route, upstreamSuccess);

        response.setStatus(responseStatus.value());
        if (responseHeaders != null) {
            responseHeaders.forEach((name, values) -> {
                if (!isHopByHopHeader(name)) values.forEach(v -> response.addHeader(name, v));
            });
        }
        response.setHeader(TRACE_ID_HEADER, traceId);
        if (responseBody != null) response.getOutputStream().write(responseBody);

        saveAuditLog(request, route, targetUrl, resolvedRoute.getTargetService(),
                responseStatus.value(), upstreamSuccess ? "SUCCESS" : "ERROR", null, latency);
        if (!upstreamSuccess) statsService.incrementErrorCount();
    }

    // =========================================================================
    // 工具方法
    // =========================================================================

    /** 本地路由：无 targetUrl 且无 serviceId，不做代理，仅执行策略后放行到 Spring MVC */
    private boolean isLocalRoute(UnifiedGatewayRoute route, UnifiedGatewayRuntimeRoutingService.ResolvedRoute resolvedRoute) {
        String resolvedTargetUrl = resolvedRoute != null ? resolvedRoute.getTargetUrl() : null;
        String resolvedTargetService = resolvedRoute != null ? resolvedRoute.getTargetService() : null;
        return (resolvedTargetUrl == null || resolvedTargetUrl.isBlank())
                && (resolvedTargetService == null || resolvedTargetService.isBlank())
                && (route.getTargetUrl() == null || route.getTargetUrl().isBlank())
                && route.getServiceId() == null;
    }

    private boolean isUnifiedGatewaySurface(String path) {
        return path != null && (path.startsWith("/v1") || path.startsWith("/api/v1/"));
    }

    private boolean isControlPlanePath(String path) {
        return path != null && path.startsWith("/api/v1/");
    }

    private boolean isRescuePath(String path) {
        return path != null && RESCUE_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private int resolveMaxAttempts(UnifiedGatewayRoute route) {
        if (route.getRetryPolicyId() != null) {
            return retryPolicyRepository.findById(route.getRetryPolicyId())
                    .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                    .map(UnifiedGatewayRetryPolicy::getMaxAttempts)
                    .orElse(1);
        }
        int retryCount = route.getRetryCount() != null ? route.getRetryCount() : 0;
        return 1 + retryCount;
    }

    private Set<Integer> resolveRetryStatusCodes(UnifiedGatewayRoute route) {
        if (route.getRetryPolicyId() != null) {
            return retryPolicyRepository.findById(route.getRetryPolicyId())
                    .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                    .map(p -> parseStatusCodes(p.getRetryOnStatusCodes()))
                    .orElse(Set.of(500, 502, 503, 504));
        }
        return Set.of(500, 502, 503, 504);
    }

    private long resolveRetryInitialMs(UnifiedGatewayRoute route) {
        if (route.getRetryPolicyId() != null) {
            return retryPolicyRepository.findById(route.getRetryPolicyId())
                    .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                    .map(p -> p.getInitialIntervalMs() != null ? p.getInitialIntervalMs().longValue() : 100L)
                    .orElse(100L);
        }
        return 100L;
    }

    private double resolveRetryBackoff(UnifiedGatewayRoute route) {
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

    private Optional<GatewaySecret> resolveApiKey(HttpServletRequest request) {
        Object apiKeyObj = request.getAttribute("apiKey");
        if (apiKeyObj instanceof GatewaySecret apiKey) {
            return Optional.of(apiKey);
        }
        String apiKey = extractApiKey(request);
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();
        Optional<GatewaySecret> validated = gatewaySecretService.validateClientAccessSecret(apiKey);
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
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
    }

    private HttpHeaders buildRequestHeaders(HttpServletRequest request, UnifiedGatewayRoute route, String traceId) {
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
        headers.add("X-UnifiedGateway-Route", route.getName());
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

    private void saveAuditLog(HttpServletRequest request, UnifiedGatewayRoute route,
                               String targetUrl, String targetService,
                               Integer statusCode, String result,
                               String errorMessage, long latencyMs) {
        try {
            GatewaySecret apiKey = null;
            Object apiKeyObj = request.getAttribute("apiKey");
            if (apiKeyObj instanceof GatewaySecret value) apiKey = value;

            UnifiedGatewayAuditLog logEntity = UnifiedGatewayAuditLog.builder()
                    .routeId(route != null ? route.getId() : null)
                    .traceId(resolveTraceId(request))
                    .method(request.getMethod())
                    .path(request.getRequestURI())
                    .targetService(targetService)
                    .targetUrl(targetUrl)
                    .statusCode(statusCode)
                    .latencyMs(latencyMs)
                    .clientIp(extractClientIp(request))
                    .userAgent(request.getHeader("User-Agent"))
                    .apiKeyId(apiKey != null ? apiKey.getSecretId() : null)
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
