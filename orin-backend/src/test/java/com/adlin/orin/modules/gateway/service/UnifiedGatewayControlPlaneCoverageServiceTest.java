package com.adlin.orin.modules.gateway.service;

import com.adlin.orin.modules.gateway.entity.UnifiedGatewayRoute;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayRouteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedGatewayControlPlaneCoverageServiceTest {

    @Mock
    private RequestMappingHandlerMapping handlerMapping;
    @Mock
    private UnifiedGatewayRouteRepository routeRepository;

    private UnifiedGatewayControlPlaneCoverageService service;

    @BeforeEach
    void setUp() {
        service = new UnifiedGatewayControlPlaneCoverageService(handlerMapping, routeRepository);
    }

    @Test
    void getCoverage_shouldTreatUnregisteredControlPlaneEndpointsAsBaselineGoverned() {
        RequestMappingInfo pricing = RequestMappingInfo.paths("/api/v1/pricing").methods(org.springframework.web.bind.annotation.RequestMethod.GET).build();
        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(pricing, mock(HandlerMethod.class)));
        when(routeRepository.findActiveRoutesOrderByPriority()).thenReturn(List.of());

        var coverage = service.getCoverage();

        assertThat(coverage.getSummary().getBaselineGovernedEndpoints()).isEqualTo(1L);
        assertThat(coverage.getSummary().getAttentionRequiredEndpoints()).isZero();
        assertThat(coverage.getEndpoints().get(0).getStatus()).isEqualTo("BASELINE_GOVERNED");
    }

    @Test
    void getCoverage_shouldSeparateExplicitPolicyAndRescueEndpoints() {
        RequestMappingInfo managed = RequestMappingInfo.paths("/api/v1/admin/users").methods(org.springframework.web.bind.annotation.RequestMethod.GET).build();
        RequestMappingInfo rescue = RequestMappingInfo.paths("/api/v1/system/gateway/routes").methods(org.springframework.web.bind.annotation.RequestMethod.GET).build();
        UnifiedGatewayRoute route = UnifiedGatewayRoute.builder()
                .id(8L)
                .name("admin-users")
                .pathPattern("/api/v1/admin/users")
                .method("GET")
                .authRequired(true)
                .build();
        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(
                managed, mock(HandlerMethod.class),
                rescue, mock(HandlerMethod.class)));
        when(routeRepository.findActiveRoutesOrderByPriority()).thenReturn(List.of(route));

        var coverage = service.getCoverage();

        assertThat(coverage.getSummary().getPolicyEnforcedEndpoints()).isEqualTo(1L);
        assertThat(coverage.getSummary().getRescueReservedEndpoints()).isEqualTo(1L);
        assertThat(coverage.getEndpoints()).extracting("status")
                .containsExactlyInAnyOrder("POLICY_ENFORCED", "RESCUE_RESERVED");
    }

    @Test
    void getCoverage_shouldMarkPolicyRouteWithoutAnyPolicyAsAttentionRequired() {
        RequestMappingInfo endpoint = RequestMappingInfo.paths("/api/v1/admin/open").methods(org.springframework.web.bind.annotation.RequestMethod.GET).build();
        UnifiedGatewayRoute route = UnifiedGatewayRoute.builder()
                .id(9L)
                .name("admin-open")
                .pathPattern("/api/v1/admin/open")
                .method("GET")
                .authRequired(false)
                .build();
        when(handlerMapping.getHandlerMethods()).thenReturn(Map.of(endpoint, mock(HandlerMethod.class)));
        when(routeRepository.findActiveRoutesOrderByPriority()).thenReturn(List.of(route));

        var coverage = service.getCoverage();

        assertThat(coverage.getSummary().getAttentionRequiredEndpoints()).isEqualTo(1L);
        assertThat(coverage.getEndpoints().get(0).getStatus()).isEqualTo("ATTENTION_REQUIRED");
    }
}
