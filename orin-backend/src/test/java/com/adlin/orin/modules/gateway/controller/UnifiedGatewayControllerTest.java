package com.adlin.orin.modules.gateway.controller;

import com.adlin.orin.modules.gateway.dto.UnifiedGatewayTestRouteRequest;
import com.adlin.orin.modules.gateway.dto.UnifiedGatewayWorkbenchResponse;
import com.adlin.orin.modules.gateway.dto.UnifiedGatewayEffectiveConfigResponse;
import com.adlin.orin.modules.gateway.dto.UnifiedGatewayAuditLogResponse;
import com.adlin.orin.modules.gateway.dto.UnifiedGatewayControlPlaneCoverageResponse;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayRoute;
import com.adlin.orin.modules.gateway.service.*;
import com.adlin.orin.modules.gateway.config.UnifiedGatewayStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedGatewayControllerTest {

    @Mock
    private UnifiedGatewayRouteService routeService;
    @Mock
    private UnifiedGatewayServiceManagementService serviceManagementService;
    @Mock
    private UnifiedGatewayAclService aclService;
    @Mock
    private UnifiedGatewayPolicyService policyService;
    @Mock
    private UnifiedGatewayStatsService statsService;
    @Mock
    private UnifiedGatewayRuntimeRoutingService runtimeRoutingService;
    @Mock
    private UnifiedGatewayWorkbenchService workbenchService;
    @Mock
    private UnifiedGatewayControlPlaneCoverageService controlPlaneCoverageService;

    private UnifiedGatewayController gatewayController;

    @BeforeEach
    void setUp() {
        gatewayController = new UnifiedGatewayController(
                routeService,
                serviceManagementService,
                aclService,
                policyService,
                statsService,
                runtimeRoutingService,
                workbenchService,
                controlPlaneCoverageService);
    }

    @Test
    void testRoute_shouldReturnMatchedRouteWhenResolved() {
        UnifiedGatewayRoute route = UnifiedGatewayRoute.builder().id(1L).name("proxy-route").build();
        UnifiedGatewayRuntimeRoutingService.ResolvedRoute resolved = UnifiedGatewayRuntimeRoutingService.ResolvedRoute.builder()
                .route(route)
                .targetUrl("http://localhost:9000/api")
                .build();
        when(runtimeRoutingService.resolveRoute("/proxy/api", "GET", null)).thenReturn(Optional.of(resolved));

        var response = gatewayController.testRoute(UnifiedGatewayTestRouteRequest.builder()
                .path("/proxy/api")
                .method("GET")
                .build());

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getMatchedRoute()).isEqualTo("proxy-route");
        assertThat(response.getTargetUrl()).isEqualTo("http://localhost:9000/api");
    }

    @Test
    void testRoute_shouldReturnNotFoundWhenNoMatch() {
        when(runtimeRoutingService.resolveRoute("/missing", "POST", null)).thenReturn(Optional.empty());

        var response = gatewayController.testRoute(UnifiedGatewayTestRouteRequest.builder()
                .path("/missing")
                .method("POST")
                .build());

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void getWorkbench_shouldReturnAggregatedWorkbenchReadModel() {
        UnifiedGatewayWorkbenchResponse workbench = UnifiedGatewayWorkbenchResponse.builder()
                .routes(List.of())
                .recentFailures(List.of())
                .build();
        when(workbenchService.getWorkbench()).thenReturn(workbench);

        var response = gatewayController.getWorkbench();

        assertThat(response).isSameAs(workbench);
    }

    @Test
    void getRouteEffectiveConfig_shouldReturnRouteChain() {
        UnifiedGatewayEffectiveConfigResponse config = UnifiedGatewayEffectiveConfigResponse.builder()
                .targetType("LOCAL")
                .warnings(List.of())
                .build();
        when(workbenchService.getEffectiveConfig(1L)).thenReturn(config);

        var response = gatewayController.getRouteEffectiveConfig(1L);

        assertThat(response.getTargetType()).isEqualTo("LOCAL");
    }

    @Test
    void getRecentFailures_shouldReturnDiagnosticsRows() {
        UnifiedGatewayAuditLogResponse failure = UnifiedGatewayAuditLogResponse.builder()
                .routeId(1L)
                .result("ERROR")
                .build();
        when(workbenchService.getRecentFailures()).thenReturn(List.of(failure));

        var response = gatewayController.getRecentFailures();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getResult()).isEqualTo("ERROR");
    }

    @Test
    void getControlPlaneCoverage_shouldReturnCoverageReadModel() {
        UnifiedGatewayControlPlaneCoverageResponse coverage = UnifiedGatewayControlPlaneCoverageResponse.builder()
                .summary(UnifiedGatewayControlPlaneCoverageResponse.Summary.builder()
                        .totalEndpoints(3L)
                        .baselineGovernedEndpoints(1L)
                        .policyEnforcedEndpoints(1L)
                        .attentionRequiredEndpoints(0L)
                        .rescueReservedEndpoints(1L)
                        .explicitPolicyCoverageRate(33.33)
                        .build())
                .endpoints(List.of())
                .build();
        when(controlPlaneCoverageService.getCoverage()).thenReturn(coverage);

        var response = gatewayController.getControlPlaneCoverage();

        assertThat(response.getSummary().getBaselineGovernedEndpoints()).isEqualTo(1L);
        assertThat(response.getSummary().getPolicyEnforcedEndpoints()).isEqualTo(1L);
        assertThat(response.getSummary().getAttentionRequiredEndpoints()).isZero();
        assertThat(response.getSummary().getRescueReservedEndpoints()).isEqualTo(1L);
    }
}
