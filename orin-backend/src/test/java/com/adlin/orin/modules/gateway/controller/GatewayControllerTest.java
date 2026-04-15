package com.adlin.orin.modules.gateway.controller;

import com.adlin.orin.modules.gateway.dto.GatewayTestRouteRequest;
import com.adlin.orin.modules.gateway.entity.GatewayRoute;
import com.adlin.orin.modules.gateway.service.*;
import com.adlin.orin.modules.gateway.config.GatewayStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayControllerTest {

    @Mock
    private GatewayRouteService routeService;
    @Mock
    private GatewayServiceManagementService serviceManagementService;
    @Mock
    private GatewayAclService aclService;
    @Mock
    private GatewayPolicyService policyService;
    @Mock
    private GatewayStatsService statsService;
    @Mock
    private GatewayRuntimeRoutingService runtimeRoutingService;

    private GatewayController gatewayController;

    @BeforeEach
    void setUp() {
        gatewayController = new GatewayController(
                routeService,
                serviceManagementService,
                aclService,
                policyService,
                statsService,
                runtimeRoutingService);
    }

    @Test
    void testRoute_shouldReturnMatchedRouteWhenResolved() {
        GatewayRoute route = GatewayRoute.builder().id(1L).name("proxy-route").build();
        GatewayRuntimeRoutingService.ResolvedRoute resolved = GatewayRuntimeRoutingService.ResolvedRoute.builder()
                .route(route)
                .targetUrl("http://localhost:9000/api")
                .build();
        when(runtimeRoutingService.resolveRoute("/proxy/api", "GET", null)).thenReturn(Optional.of(resolved));

        var response = gatewayController.testRoute(GatewayTestRouteRequest.builder()
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

        var response = gatewayController.testRoute(GatewayTestRouteRequest.builder()
                .path("/missing")
                .method("POST")
                .build());

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getStatusCode()).isEqualTo(404);
    }
}
