package com.adlin.orin.modules.gateway.service;

import com.adlin.orin.modules.gateway.entity.GatewayRoute;
import com.adlin.orin.modules.gateway.entity.GatewayService;
import com.adlin.orin.modules.gateway.entity.GatewayServiceInstance;
import com.adlin.orin.modules.gateway.repository.GatewayRouteRepository;
import com.adlin.orin.modules.gateway.repository.GatewayServiceInstanceRepository;
import com.adlin.orin.modules.gateway.repository.GatewayServiceRepository;
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
class GatewayRuntimeRoutingServiceTest {

    @Mock
    private GatewayRouteRepository routeRepository;
    @Mock
    private GatewayServiceRepository serviceRepository;
    @Mock
    private GatewayServiceInstanceRepository instanceRepository;

    private GatewayRuntimeRoutingService runtimeRoutingService;

    @BeforeEach
    void setUp() {
        runtimeRoutingService = new GatewayRuntimeRoutingService(routeRepository, serviceRepository, instanceRepository);
    }

    @Test
    void resolveRoute_shouldMatchAndRewriteWithQuery() {
        GatewayRoute route = GatewayRoute.builder()
                .id(1L)
                .name("test-route")
                .pathPattern("/proxy/**")
                .method("GET")
                .targetUrl("http://upstream.local")
                .stripPrefix(true)
                .enabled(true)
                .build();
        when(routeRepository.findActiveRoutesOrderByPriority()).thenReturn(List.of(route));

        Optional<GatewayRuntimeRoutingService.ResolvedRoute> resolved =
                runtimeRoutingService.resolveRoute("/proxy/v1/items", "GET", "a=1&b=2");

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getRoute().getName()).isEqualTo("test-route");
        assertThat(resolved.get().getTargetUrl()).isEqualTo("http://upstream.local/v1/items?a=1&b=2");
    }

    @Test
    void resolveRoute_shouldUseServiceInstanceRoundRobin() {
        GatewayRoute route = GatewayRoute.builder()
                .id(2L)
                .name("svc-route")
                .pathPattern("/svc/**")
                .method("ALL")
                .serviceId(10L)
                .loadBalance("ROUND_ROBIN")
                .enabled(true)
                .build();
        when(routeRepository.findActiveRoutesOrderByPriority()).thenReturn(List.of(route));

        GatewayServiceInstance instanceA = GatewayServiceInstance.builder()
                .id(101L)
                .serviceId(10L)
                .host("host-a.local")
                .port(8081)
                .status("UP")
                .enabled(true)
                .build();
        GatewayServiceInstance instanceB = GatewayServiceInstance.builder()
                .id(102L)
                .serviceId(10L)
                .host("http://host-b.local")
                .port(8082)
                .status("UP")
                .enabled(true)
                .build();
        when(instanceRepository.findByServiceIdAndEnabledOrderByHost(10L, true)).thenReturn(List.of(instanceA, instanceB));
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(GatewayService.builder().id(10L).serviceName("svc").build()));

        String url1 = runtimeRoutingService.resolveRoute("/svc/ping", "GET", null).orElseThrow().getTargetUrl();
        String url2 = runtimeRoutingService.resolveRoute("/svc/ping", "GET", null).orElseThrow().getTargetUrl();

        assertThat(url1).isEqualTo("http://host-a.local:8081/svc/ping");
        assertThat(url2).isEqualTo("http://host-b.local:8082/svc/ping");
    }
}
