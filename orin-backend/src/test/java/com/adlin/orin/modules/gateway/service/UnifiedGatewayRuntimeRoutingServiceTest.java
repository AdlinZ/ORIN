package com.adlin.orin.modules.gateway.service;

import com.adlin.orin.modules.gateway.entity.UnifiedGatewayRoute;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayService;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayServiceInstance;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayRouteRepository;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayServiceInstanceRepository;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedGatewayRuntimeRoutingServiceTest {

    @Mock
    private UnifiedGatewayRouteRepository routeRepository;
    @Mock
    private UnifiedGatewayServiceRepository serviceRepository;
    @Mock
    private UnifiedGatewayServiceInstanceRepository instanceRepository;

    private UnifiedGatewayRuntimeRoutingService runtimeRoutingService;

    @BeforeEach
    void setUp() {
        runtimeRoutingService = new UnifiedGatewayRuntimeRoutingService(routeRepository, serviceRepository, instanceRepository);
    }

    @Test
    void resolveRoute_shouldMatchAndRewriteWithQuery() {
        UnifiedGatewayRoute route = UnifiedGatewayRoute.builder()
                .id(1L)
                .name("test-route")
                .pathPattern("/proxy/**")
                .method("GET")
                .targetUrl("http://upstream.local")
                .stripPrefix(true)
                .enabled(true)
                .build();
        when(routeRepository.findActiveRoutesOrderByPriority()).thenReturn(List.of(route));

        Optional<UnifiedGatewayRuntimeRoutingService.ResolvedRoute> resolved =
                runtimeRoutingService.resolveRoute("/proxy/v1/items", "GET", "a=1&b=2");

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getRoute().getName()).isEqualTo("test-route");
        assertThat(resolved.get().getTargetUrl()).isEqualTo("http://upstream.local/v1/items?a=1&b=2");
    }

    @Test
    void resolveRoute_shouldUseServiceInstanceRoundRobin() {
        UnifiedGatewayRoute route = UnifiedGatewayRoute.builder()
                .id(2L)
                .name("svc-route")
                .pathPattern("/svc/**")
                .method("ALL")
                .serviceId(10L)
                .loadBalance("ROUND_ROBIN")
                .enabled(true)
                .build();
        when(routeRepository.findActiveRoutesOrderByPriority()).thenReturn(List.of(route));

        UnifiedGatewayServiceInstance instanceA = UnifiedGatewayServiceInstance.builder()
                .id(101L)
                .serviceId(10L)
                .host("host-a.local")
                .port(8081)
                .status("UP")
                .enabled(true)
                .build();
        UnifiedGatewayServiceInstance instanceB = UnifiedGatewayServiceInstance.builder()
                .id(102L)
                .serviceId(10L)
                .host("http://host-b.local")
                .port(8082)
                .status("UP")
                .enabled(true)
                .build();
        when(instanceRepository.findByServiceIdAndEnabledOrderByHost(10L, true)).thenReturn(List.of(instanceA, instanceB));
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(UnifiedGatewayService.builder().id(10L).serviceName("svc").build()));

        String url1 = runtimeRoutingService.resolveRoute("/svc/ping", "GET", null).orElseThrow().getTargetUrl();
        String url2 = runtimeRoutingService.resolveRoute("/svc/ping", "GET", null).orElseThrow().getTargetUrl();

        assertThat(url1).isEqualTo("http://host-a.local:8081/svc/ping");
        assertThat(url2).isEqualTo("http://host-b.local:8082/svc/ping");
    }
}
