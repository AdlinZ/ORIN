package com.adlin.orin.modules.gateway.service;

import com.adlin.orin.modules.gateway.entity.UnifiedGatewayService;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayServiceInstance;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayServiceInstanceRepository;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedGatewayServiceManagementServiceTest {

    @Mock
    private UnifiedGatewayServiceRepository serviceRepository;
    @Mock
    private UnifiedGatewayServiceInstanceRepository instanceRepository;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private RestTemplate restTemplate;

    private UnifiedGatewayServiceManagementService service;

    @BeforeEach
    void setUp() {
        service = new UnifiedGatewayServiceManagementService(
                serviceRepository,
                instanceRepository,
                redisTemplate,
                restTemplate);
    }

    @Test
    void getAllServices_withInstances_shouldBatchLoadInstances() {
        UnifiedGatewayService users = UnifiedGatewayService.builder()
                .id(1L)
                .serviceKey("users")
                .serviceName("Users")
                .protocol("HTTP")
                .enabled(true)
                .build();
        UnifiedGatewayService billing = UnifiedGatewayService.builder()
                .id(2L)
                .serviceKey("billing")
                .serviceName("Billing")
                .protocol("HTTP")
                .enabled(true)
                .build();
        UnifiedGatewayServiceInstance usersUp = UnifiedGatewayServiceInstance.builder()
                .id(11L)
                .serviceId(1L)
                .host("127.0.0.1")
                .port(8081)
                .status("UP")
                .enabled(true)
                .build();
        UnifiedGatewayServiceInstance billingDown = UnifiedGatewayServiceInstance.builder()
                .id(21L)
                .serviceId(2L)
                .host("127.0.0.1")
                .port(8082)
                .status("DOWN")
                .enabled(true)
                .build();

        when(serviceRepository.findAllByOrderByServiceName()).thenReturn(List.of(billing, users));
        when(instanceRepository.findByServiceIdInOrderByHost(List.of(2L, 1L)))
                .thenReturn(List.of(billingDown, usersUp));
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        var responses = service.getAllServices(true);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getInstanceCount()).isEqualTo(1);
        assertThat(responses.get(0).getHealthyInstanceCount()).isZero();
        assertThat(responses.get(0).getInstances()).hasSize(1);
        assertThat(responses.get(1).getStatus()).isEqualTo("HEALTHY");
        verify(instanceRepository).findByServiceIdInOrderByHost(List.of(2L, 1L));
    }
}
