package com.adlin.orin.modules.gateway.config;

import com.adlin.orin.gateway.service.ProviderRegistry;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayAuditLog;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayRoute;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayAuditLogRepository;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayRouteRepository;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayServiceInstanceRepository;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedGatewayStatsServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private UnifiedGatewayRouteRepository routeRepository;
    @Mock
    private UnifiedGatewayServiceRepository serviceRepository;
    @Mock
    private UnifiedGatewayServiceInstanceRepository instanceRepository;
    @Mock
    private UnifiedGatewayAuditLogRepository auditLogRepository;
    @Mock
    private ProviderRegistry providerRegistry;

    private UnifiedGatewayStatsService statsService;

    @BeforeEach
    void setUp() {
        statsService = new UnifiedGatewayStatsService(
                redisTemplate,
                routeRepository,
                serviceRepository,
                instanceRepository,
                auditLogRepository,
                providerRegistry);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        lenient().when(serviceRepository.findAll()).thenReturn(List.of());
        lenient().when(instanceRepository.findAll()).thenReturn(List.of());
        lenient().when(routeRepository.findActiveRoutesOrderByPriority()).thenReturn(List.of());
        lenient().when(serviceRepository.findByEnabledOrderByServiceName(true)).thenReturn(List.of());
        lenient().when(providerRegistry.getStatistics()).thenReturn(Map.of(
                "totalProviders", 0,
                "healthyProviders", 0,
                "unhealthyProviders", 0));
        lenient().when(providerRegistry.getProviderDetails()).thenReturn(List.of());
        lenient().when(auditLogRepository.findAverageLatencySince(org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);
    }

    @Test
    void getOverview_shouldIgnoreObservedOnlyAuditLogsWithoutRouteId() {
        UnifiedGatewayAuditLog observedOnly = UnifiedGatewayAuditLog.builder()
                .routeId(null)
                .result("BASELINE_GOVERNED")
                .createdAt(LocalDateTime.now())
                .latencyMs(12L)
                .build();
        UnifiedGatewayAuditLog routed = UnifiedGatewayAuditLog.builder()
                .routeId(7L)
                .result("SUCCESS")
                .createdAt(LocalDateTime.now())
                .latencyMs(20L)
                .build();
        when(auditLogRepository.findAll()).thenReturn(List.of(observedOnly, routed));
        when(routeRepository.findById(7L)).thenReturn(Optional.of(UnifiedGatewayRoute.builder()
                .id(7L)
                .name("managed-route")
                .pathPattern("/api/v1/managed/**")
                .build()));

        var overview = statsService.getOverview();

        assertThat(overview.getTopRoutes()).hasSize(1);
        assertThat(overview.getTopRoutes().get(0).getRouteId()).isEqualTo(7L);
        assertThat(overview.getTopRoutes().get(0).getRouteName()).isEqualTo("managed-route");
    }

    @Test
    void getOverview_shouldCapErrorRateWhenRedisCountersAreOutOfSync() {
        when(valueOperations.get("gateway:stats:total_requests")).thenReturn("1");
        when(valueOperations.get("gateway:stats:total_errors")).thenReturn("17");
        when(auditLogRepository.findAll()).thenReturn(List.of());

        var overview = statsService.getOverview();

        assertThat(overview.getErrorRate()).isEqualTo(100.0);
    }
}
