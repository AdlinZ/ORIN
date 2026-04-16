package com.adlin.orin.modules.gateway.filter;

import com.adlin.orin.modules.apikey.service.ApiKeyService;
import com.adlin.orin.modules.gateway.config.GatewayStatsService;
import com.adlin.orin.modules.gateway.entity.GatewayRoute;
import com.adlin.orin.modules.gateway.repository.GatewayAuditLogRepository;
import com.adlin.orin.modules.gateway.repository.GatewayRetryPolicyRepository;
import com.adlin.orin.modules.gateway.service.GatewayAclService;
import com.adlin.orin.modules.gateway.service.GatewayCircuitBreakerService;
import com.adlin.orin.modules.gateway.service.GatewayRateLimiterService;
import com.adlin.orin.modules.gateway.service.GatewayRuntimeRoutingService;
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

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayProxyFilterTest {

    @Mock
    private GatewayAclService aclService;
    @Mock
    private GatewayRuntimeRoutingService routingService;
    @Mock
    private GatewayStatsService statsService;
    @Mock
    private GatewayAuditLogRepository auditLogRepository;
    @Mock
    private ApiKeyService apiKeyService;
    @Mock
    private GatewayRateLimiterService rateLimiterService;
    @Mock
    private GatewayCircuitBreakerService circuitBreakerService;
    @Mock
    private GatewayRetryPolicyRepository retryPolicyRepository;

    private GatewayProxyFilter gatewayProxyFilter;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() {
        gatewayProxyFilter = new GatewayProxyFilter(
                aclService,
                routingService,
                statsService,
                auditLogRepository,
                apiKeyService,
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
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @Test
    void doFilter_shouldReturn401WhenAuthRequiredAndNoAuth() throws Exception {
        GatewayRoute route = GatewayRoute.builder()
                .id(1L)
                .name("secure")
                .pathPattern("/api/proxy/**")
                .authRequired(true)
                .build();
        GatewayRuntimeRoutingService.ResolvedRoute resolved = GatewayRuntimeRoutingService.ResolvedRoute.builder()
                .route(route)
                .targetUrl("http://localhost:18080/test")
                .build();
        when(routingService.resolveRoute("/api/proxy/test", "GET", null)).thenReturn(Optional.of(resolved));
        when(aclService.testIp(anyString(), anyString())).thenReturn(Map.of("action", "ALLOW", "apiKeyRequired", false));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/proxy/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        gatewayProxyFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Authentication required");
        verify(statsService).incrementRequestCount();
        verify(statsService).incrementErrorCount();
        verify(auditLogRepository).save(ArgumentMatchers.any());
    }

    @Test
    void doFilter_shouldReturn403WhenGlobalAclDenied() throws Exception {
        // 全局 ACL 在路由匹配之前执行，即使无匹配路由也应拒绝
        when(aclService.testIp(anyString(), anyString())).thenReturn(Map.of("action", "DENY", "apiKeyRequired", false));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/proxy/test");
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
        GatewayRoute localRoute = GatewayRoute.builder()
                .id(10L)
                .name("local-api")
                .pathPattern("/api/v1/agents/**")
                .authRequired(false)
                // 无 targetUrl、无 serviceId → LOCAL 路由
                .build();
        GatewayRuntimeRoutingService.ResolvedRoute resolved = GatewayRuntimeRoutingService.ResolvedRoute.builder()
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
        GatewayRoute route = GatewayRoute.builder()
                .id(3L)
                .name("bad-gateway")
                .pathPattern("/proxy/**")
                .authRequired(false)
                .timeoutMs(1000)
                .build();
        GatewayRuntimeRoutingService.ResolvedRoute resolved = GatewayRuntimeRoutingService.ResolvedRoute.builder()
                .route(route)
                .targetUrl("http://127.0.0.1:9/unreachable")
                .build();
        when(routingService.resolveRoute("/proxy/unreachable", "GET", null)).thenReturn(Optional.of(resolved));
        when(aclService.testIp(anyString(), anyString())).thenReturn(Map.of("action", "ALLOW", "apiKeyRequired", false));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/proxy/unreachable");
        MockHttpServletResponse response = new MockHttpServletResponse();

        gatewayProxyFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getContentAsString()).contains("Gateway upstream request failed");
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

        GatewayRoute route = GatewayRoute.builder()
                .id(4L)
                .name("ok-route")
                .pathPattern("/proxy/**")
                .authRequired(false)
                .timeoutMs(2000)
                .build();
        String targetUrl = String.format("http://localhost:%d/upstream", mockWebServer.getPort());
        GatewayRuntimeRoutingService.ResolvedRoute resolved = GatewayRuntimeRoutingService.ResolvedRoute.builder()
                .route(route)
                .targetUrl(targetUrl)
                .targetService("test-service")
                .build();
        when(routingService.resolveRoute("/proxy/items", "POST", "x=1")).thenReturn(Optional.of(resolved));
        when(aclService.testIp(anyString(), anyString())).thenReturn(Map.of("action", "ALLOW", "apiKeyRequired", false));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/proxy/items");
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
}
