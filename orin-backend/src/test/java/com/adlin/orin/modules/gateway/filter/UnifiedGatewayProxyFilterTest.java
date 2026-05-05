package com.adlin.orin.modules.gateway.filter;

import com.adlin.orin.modules.apikey.service.GatewaySecretService;
import com.adlin.orin.modules.gateway.config.UnifiedGatewayStatsService;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayRoute;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayAuditLogRepository;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayRetryPolicyRepository;
import com.adlin.orin.modules.gateway.service.UnifiedGatewayAclService;
import com.adlin.orin.modules.gateway.service.UnifiedGatewayCircuitBreakerService;
import com.adlin.orin.modules.gateway.service.UnifiedGatewayRateLimiterService;
import com.adlin.orin.modules.gateway.service.UnifiedGatewayRuntimeRoutingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnifiedGatewayProxyFilterTest {

    @Mock
    private UnifiedGatewayAclService aclService;
    @Mock
    private UnifiedGatewayRuntimeRoutingService routingService;
    @Mock
    private UnifiedGatewayStatsService statsService;
    @Mock
    private UnifiedGatewayAuditLogRepository auditLogRepository;
    @Mock
    private GatewaySecretService gatewaySecretService;
    @Mock
    private UnifiedGatewayRateLimiterService rateLimiterService;
    @Mock
    private UnifiedGatewayCircuitBreakerService circuitBreakerService;
    @Mock
    private UnifiedGatewayRetryPolicyRepository retryPolicyRepository;

    private UnifiedGatewayProxyFilter gatewayProxyFilter;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        gatewayProxyFilter = new UnifiedGatewayProxyFilter(
                aclService,
                routingService,
                statsService,
                auditLogRepository,
                gatewaySecretService,
                new ObjectMapper(),
                rateLimiterService,
                circuitBreakerService,
                retryPolicyRepository);
        // 默认放行限流和熔断，不影响现有测试逻辑
        lenient().when(rateLimiterService.tryAcquire(any(), any(), any())).thenReturn(true);
        lenient().when(circuitBreakerService.allowRequest(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws IOException {
        SecurityContextHolder.clearContext();
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @Test
    void doFilter_shouldReturn401WhenAuthRequiredAndNoAuth() throws Exception {
        UnifiedGatewayRoute route = UnifiedGatewayRoute.builder()
                .id(1L)
                .name("secure")
                .pathPattern("/api/v1/proxy/**")
                .authRequired(true)
                .build();
        UnifiedGatewayRuntimeRoutingService.ResolvedRoute resolved = UnifiedGatewayRuntimeRoutingService.ResolvedRoute.builder()
                .route(route)
                .targetUrl("http://localhost:18080/test")
                .build();
        when(routingService.resolveRoute("/api/v1/proxy/test", "GET", null)).thenReturn(Optional.of(resolved));
        when(aclService.testIp(anyString(), anyString())).thenReturn(Map.of("action", "ALLOW", "apiKeyRequired", false));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/proxy/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        gatewayProxyFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Authentication required");
        verify(statsService).incrementRequestCount();
        verify(statsService).incrementErrorCount();
        verify(auditLogRepository).save(ArgumentMatchers.any());
    }

    @Test
    void doFilter_shouldNotTreatJwtLookingBearerAsAuthenticated() throws Exception {
        UnifiedGatewayRoute route = UnifiedGatewayRoute.builder()
                .id(11L)
                .name("secure-v1")
                .pathPattern("/v1/chat/completions")
                .authRequired(true)
                .build();
        UnifiedGatewayRuntimeRoutingService.ResolvedRoute resolved = UnifiedGatewayRuntimeRoutingService.ResolvedRoute.builder()
                .route(route)
                .build();
        when(routingService.resolveRoute("/v1/chat/completions", "POST", null)).thenReturn(Optional.of(resolved));
        when(aclService.testIp(anyString(), anyString())).thenReturn(Map.of("action", "ALLOW", "apiKeyRequired", false));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/chat/completions");
        request.addHeader("Authorization", "Bearer eyJ.fake.unsigned");
        MockHttpServletResponse response = new MockHttpServletResponse();

        gatewayProxyFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        verify(gatewaySecretService, never()).validateClientAccessSecret(anyString());
    }

    @Test
    void doFilter_shouldReturn403WhenGlobalAclDenied() throws Exception {
        // 全局 ACL 在路由匹配之前执行，即使无匹配路由也应拒绝
        when(aclService.testIp(anyString(), anyString())).thenReturn(Map.of("action", "DENY", "apiKeyRequired", false));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/proxy/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        gatewayProxyFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("ACL denied");
        // 全局 ACL 阶段：路由未匹配，不计入请求统计，不写审计日志
        verify(statsService, never()).incrementRequestCount();
        verify(statsService).incrementErrorCount();
        verify(auditLogRepository, never()).save(ArgumentMatchers.any());
        // 全局 ACL 触发时路由服务不应被调用
        verify(routingService, never()).resolveRoute(anyString(), anyString(), any());
    }

    @Test
    void doFilter_shouldPassThroughAndAuditForLocalRoute() throws Exception {
        // 本地路由（无 targetUrl、无 serviceId）：经过策略后放行到 Spring MVC，并写审计日志
        UnifiedGatewayRoute localRoute = UnifiedGatewayRoute.builder()
                .id(10L)
                .name("local-api")
                .pathPattern("/api/v1/agents/**")
                .authRequired(false)
                // 无 targetUrl、无 serviceId → LOCAL 路由
                .build();
        UnifiedGatewayRuntimeRoutingService.ResolvedRoute resolved = UnifiedGatewayRuntimeRoutingService.ResolvedRoute.builder()
                .route(localRoute)
                .targetUrl(null)
                .targetService(null)
                .build();
        when(routingService.resolveRoute("/api/v1/agents/list", "GET", null))
                .thenReturn(Optional.of(resolved));
        when(aclService.testIp(anyString(), anyString()))
                .thenReturn(Map.of("action", "ALLOW", "apiKeyRequired", false));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/agents/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        // 模拟下游 Controller 写了 200
        MockFilterChain chain = new MockFilterChain();

        gatewayProxyFilter.doFilter(request, response, chain);

        // 应放行到下游（chain.doFilter 被调用）
        assertThat(chain.getRequest()).isNotNull();
        // 统计和审计
        verify(statsService).incrementRequestCount();
        verify(auditLogRepository).save(ArgumentMatchers.any());
        // 熔断服务不应被调用（本地路由不走代理）
        verify(circuitBreakerService, never()).allowRequest(any());
        verify(circuitBreakerService, never()).recordResult(any(), anyBoolean());
    }

    @Test
    void doFilter_shouldReturn502WhenUpstreamUnavailable() throws Exception {
        UnifiedGatewayRoute route = UnifiedGatewayRoute.builder()
                .id(3L)
                .name("bad-gateway")
                .pathPattern("/api/v1/proxy/**")
                .authRequired(false)
                .timeoutMs(1000)
                .build();
        UnifiedGatewayRuntimeRoutingService.ResolvedRoute resolved = UnifiedGatewayRuntimeRoutingService.ResolvedRoute.builder()
                .route(route)
                .targetUrl("http://127.0.0.1:9/unreachable")
                .build();
        when(routingService.resolveRoute("/api/v1/proxy/unreachable", "GET", null)).thenReturn(Optional.of(resolved));
        when(aclService.testIp(anyString(), anyString())).thenReturn(Map.of("action", "ALLOW", "apiKeyRequired", false));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/proxy/unreachable");
        MockHttpServletResponse response = new MockHttpServletResponse();

        gatewayProxyFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getContentAsString()).contains("UnifiedGateway upstream request failed");
        verify(statsService).incrementRequestCount();
        verify(statsService).incrementErrorCount();
        verify(auditLogRepository).save(ArgumentMatchers.any());
    }

    @Test
    void doFilter_shouldProxySuccessfullyAndPersistAudit() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":true}"));

        UnifiedGatewayRoute route = UnifiedGatewayRoute.builder()
                .id(4L)
                .name("ok-route")
                .pathPattern("/api/v1/proxy/**")
                .authRequired(false)
                .timeoutMs(2000)
                .build();
        String targetUrl = String.format("http://localhost:%d/upstream", mockWebServer.getPort());
        UnifiedGatewayRuntimeRoutingService.ResolvedRoute resolved = UnifiedGatewayRuntimeRoutingService.ResolvedRoute.builder()
                .route(route)
                .targetUrl(targetUrl)
                .targetService("test-service")
                .build();
        when(routingService.resolveRoute("/api/v1/proxy/items", "POST", "x=1")).thenReturn(Optional.of(resolved));
        when(aclService.testIp(anyString(), anyString())).thenReturn(Map.of("action", "ALLOW", "apiKeyRequired", false));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/proxy/items");
        request.setQueryString("x=1");
        request.setContentType("application/json");
        request.setContent("{\"name\":\"orin\"}".getBytes());
        request.addHeader("X-Trace-Id", "trace-001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        gatewayProxyFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"ok\":true");
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("trace-001");
        verify(statsService).incrementRequestCount();
        verify(auditLogRepository).save(ArgumentMatchers.any());
    }

    @Test
    void doFilter_shouldForwardSuccessfulEmptyUpstreamResponse() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));

        UnifiedGatewayRoute route = UnifiedGatewayRoute.builder()
                .id(5L)
                .name("empty-route")
                .pathPattern("/api/v1/proxy/**")
                .authRequired(false)
                .timeoutMs(2000)
                .build();
        String targetUrl = String.format("http://localhost:%d/upstream", mockWebServer.getPort());
        UnifiedGatewayRuntimeRoutingService.ResolvedRoute resolved = UnifiedGatewayRuntimeRoutingService.ResolvedRoute.builder()
                .route(route)
                .targetUrl(targetUrl)
                .targetService("empty-service")
                .build();
        when(routingService.resolveRoute("/api/v1/proxy/empty", "GET", null)).thenReturn(Optional.of(resolved));
        when(aclService.testIp(anyString(), anyString())).thenReturn(Map.of("action", "ALLOW", "apiKeyRequired", false));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/proxy/empty");
        MockHttpServletResponse response = new MockHttpServletResponse();

        gatewayProxyFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verify(statsService).incrementRequestCount();
        verify(statsService, never()).incrementErrorCount();
        verify(circuitBreakerService).recordResult(route, true);
        verify(auditLogRepository).save(ArgumentMatchers.any());
    }

    @Test
    void doFilter_shouldAuditBaselineGovernedControlPlaneWhenNoRouteMatches() throws Exception {
        when(aclService.testIp(anyString(), anyString())).thenReturn(Map.of("action", "ALLOW", "apiKeyRequired", false));
        when(routingService.resolveRoute("/api/v1/pricing", "GET", null)).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/pricing");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        gatewayProxyFilter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getHeader("X-Trace-Id")).isNotBlank();
        verify(auditLogRepository).save(argThat(log ->
                log.getRouteId() == null
                        && "BASELINE_GOVERNED".equals(log.getResult())
                        && "BASELINE_GOVERNED".equals(log.getTargetService())));
    }

    @Test
    void doFilter_shouldBypassGovernanceAndAuditRescueEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/gateway/routes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        gatewayProxyFilter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(aclService, never()).testIp(anyString(), anyString());
        verify(routingService, never()).resolveRoute(anyString(), anyString(), any());
        verify(auditLogRepository).save(argThat(log ->
                log.getRouteId() == null
                        && "RESCUE_RESERVED".equals(log.getResult())
                        && "RESCUE_RESERVED".equals(log.getTargetService())));
    }
}
