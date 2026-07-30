package com.adlin.orin.modules.gateway.service;

import com.adlin.orin.modules.gateway.dto.UnifiedGatewayServiceInstanceRequest;
import com.adlin.orin.modules.gateway.dto.UnifiedGatewayServiceInstanceResponse;
import com.adlin.orin.modules.gateway.dto.UnifiedGatewayServiceRequest;
import com.adlin.orin.modules.gateway.dto.UnifiedGatewayServiceResponse;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayService;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayServiceInstance;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayServiceInstanceRepository;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedGatewayServiceManagementService {

    private final UnifiedGatewayServiceRepository serviceRepository;
    private final UnifiedGatewayServiceInstanceRepository instanceRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final RestTemplate restTemplate;

    private static final String HEALTH_KEY_PREFIX = "gateway:health:";

    public List<UnifiedGatewayServiceResponse> getAllServices() {
        return getAllServices(false);
    }

    public List<UnifiedGatewayServiceResponse> getAllServices(boolean includeInstances) {
        List<UnifiedGatewayService> services = serviceRepository.findAllByOrderByServiceName();
        if (services.isEmpty()) {
            return List.of();
        }
        List<Long> serviceIds = services.stream().map(UnifiedGatewayService::getId).toList();
        Map<Long, List<UnifiedGatewayServiceInstance>> instancesByService = instanceRepository
                .findByServiceIdInOrderByHost(serviceIds)
                .stream()
                .collect(Collectors.groupingBy(UnifiedGatewayServiceInstance::getServiceId));

        return services.stream()
                .map(service -> toServiceResponse(
                        service,
                        instancesByService.getOrDefault(service.getId(), Collections.emptyList()),
                        includeInstances))
                .collect(Collectors.toList());
    }

    public UnifiedGatewayServiceResponse getService(Long id) {
        UnifiedGatewayService service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found: " + id));
        return toServiceResponse(service);
    }

    @Transactional
    public UnifiedGatewayServiceResponse createService(UnifiedGatewayServiceRequest request) {
        UnifiedGatewayService service = UnifiedGatewayService.builder()
                .serviceKey(request.getServiceKey())
                .serviceName(request.getServiceName())
                .protocol(request.getProtocol() != null ? request.getProtocol() : "HTTP")
                .basePath(request.getBasePath())
                .description(request.getDescription())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();
        service = serviceRepository.save(service);
        log.info("Created gateway service: {} ({})", service.getServiceName(), service.getId());
        return toServiceResponse(service);
    }

    @Transactional
    public UnifiedGatewayServiceResponse updateService(Long id, UnifiedGatewayServiceRequest request) {
        UnifiedGatewayService service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found: " + id));
        if (request.getServiceKey() != null) service.setServiceKey(request.getServiceKey());
        if (request.getServiceName() != null) service.setServiceName(request.getServiceName());
        if (request.getProtocol() != null) service.setProtocol(request.getProtocol());
        if (request.getBasePath() != null) service.setBasePath(request.getBasePath());
        if (request.getDescription() != null) service.setDescription(request.getDescription());
        if (request.getEnabled() != null) service.setEnabled(request.getEnabled());
        service = serviceRepository.save(service);
        log.info("Updated gateway service: {} ({})", service.getServiceName(), service.getId());
        return toServiceResponse(service);
    }

    @Transactional
    public void deleteService(Long id) {
        if (!serviceRepository.existsById(id)) {
            throw new RuntimeException("Service not found: " + id);
        }
        instanceRepository.deleteAll(instanceRepository.findByServiceIdOrderByHost(id));
        serviceRepository.deleteById(id);
        log.info("Deleted gateway service: {}", id);
    }

    public List<UnifiedGatewayServiceInstanceResponse> getInstances(Long serviceId) {
        return instanceRepository.findByServiceIdOrderByHost(serviceId).stream()
                .map(this::toInstanceResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public UnifiedGatewayServiceInstanceResponse createInstance(Long serviceId, UnifiedGatewayServiceInstanceRequest request) {
        if (!serviceRepository.existsById(serviceId)) {
            throw new RuntimeException("Service not found: " + serviceId);
        }
        UnifiedGatewayServiceInstance instance = UnifiedGatewayServiceInstance.builder()
                .serviceId(serviceId)
                .host(request.getHost())
                .port(request.getPort())
                .weight(request.getWeight() != null ? request.getWeight() : 100)
                .healthCheckPath(request.getHealthCheckPath())
                .status("UP")
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();
        instance = instanceRepository.save(instance);
        log.info("Created service instance: {}:{} ({})", instance.getHost(), instance.getPort(), instance.getId());
        return toInstanceResponse(instance);
    }

    @Transactional
    public UnifiedGatewayServiceInstanceResponse updateInstance(Long serviceId, Long instanceId, UnifiedGatewayServiceInstanceRequest request) {
        UnifiedGatewayServiceInstance instance = instanceRepository.findByIdAndServiceId(instanceId, serviceId)
                .orElseThrow(() -> new RuntimeException("Instance not found: " + instanceId));
        if (request.getHost() != null) instance.setHost(request.getHost());
        if (request.getPort() != null) instance.setPort(request.getPort());
        if (request.getWeight() != null) instance.setWeight(request.getWeight());
        if (request.getHealthCheckPath() != null) instance.setHealthCheckPath(request.getHealthCheckPath());
        if (request.getStatus() != null) instance.setStatus(request.getStatus());
        if (request.getEnabled() != null) instance.setEnabled(request.getEnabled());
        instance = instanceRepository.save(instance);
        log.info("Updated service instance: {}:{} ({})", instance.getHost(), instance.getPort(), instance.getId());
        return toInstanceResponse(instance);
    }

    @Transactional
    public void deleteInstance(Long serviceId, Long instanceId) {
        UnifiedGatewayServiceInstance instance = instanceRepository.findByIdAndServiceId(instanceId, serviceId)
                .orElseThrow(() -> new RuntimeException("Instance not found: " + instanceId));
        instanceRepository.delete(instance);
        redisTemplate.delete(HEALTH_KEY_PREFIX + instanceId);
        log.info("Deleted service instance: {}", instanceId);
    }

    public Map<String, Object> triggerHealthCheck(Long serviceId, Long instanceId) {
        UnifiedGatewayServiceInstance instance = instanceRepository.findByIdAndServiceId(instanceId, serviceId)
                .orElseThrow(() -> new RuntimeException("Instance not found: " + instanceId));

        Map<String, Object> result = new HashMap<>();
        result.put("instanceId", instanceId);

        if (instance.getHealthCheckPath() == null || instance.getHealthCheckPath().isEmpty()) {
            result.put("success", true);
            result.put("message", "No health check path configured");
            return result;
        }

        String url = buildHealthCheckUrl(instance);
        long start = System.currentTimeMillis();
        try {
            restTemplate.getForEntity(url, String.class);
            long latency = System.currentTimeMillis() - start;
            instance.setStatus("UP");
            instance.setConsecutiveFailures(0);
            instance.setLastHeartbeat(LocalDateTime.now());
            instanceRepository.save(instance);
            redisTemplate.opsForHash().put(HEALTH_KEY_PREFIX + instanceId, "status", "UP");
            redisTemplate.opsForHash().put(HEALTH_KEY_PREFIX + instanceId, "latency", String.valueOf(latency));
            result.put("success", true);
            result.put("latencyMs", latency);
            result.put("status", "UP");
        } catch (Exception e) {
            instance.setStatus("DOWN");
            instance.setConsecutiveFailures(instance.getConsecutiveFailures() + 1);
            instanceRepository.save(instance);
            redisTemplate.opsForHash().put(HEALTH_KEY_PREFIX + instanceId, "status", "DOWN");
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("status", "DOWN");
        }
        return result;
    }

    private String buildHealthCheckUrl(UnifiedGatewayServiceInstance instance) {
        String host = instance.getHost() == null ? "" : instance.getHost().trim();
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "http://" + host;
        }
        String path = instance.getHealthCheckPath() == null ? "" : instance.getHealthCheckPath().trim();
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        return host + ":" + instance.getPort() + path;
    }

    private UnifiedGatewayServiceResponse toServiceResponse(UnifiedGatewayService service) {
        return toServiceResponse(service, instanceRepository.findByServiceIdOrderByHost(service.getId()), false);
    }

    private UnifiedGatewayServiceResponse toServiceResponse(UnifiedGatewayService service,
                                                     List<UnifiedGatewayServiceInstance> instances,
                                                     boolean includeInstances) {
        int healthyCount = (int) instances.stream().filter(i -> "UP".equals(i.getStatus())).count();
        String overallStatus = instances.isEmpty() ? "NO_INSTANCES" :
                               healthyCount == instances.size() ? "HEALTHY" :
                               healthyCount > 0 ? "DEGRADED" : "UNHEALTHY";

        UnifiedGatewayServiceResponse.UnifiedGatewayServiceResponseBuilder builder = UnifiedGatewayServiceResponse.builder()
                .id(service.getId())
                .serviceKey(service.getServiceKey())
                .serviceName(service.getServiceName())
                .protocol(service.getProtocol())
                .basePath(service.getBasePath())
                .description(service.getDescription())
                .enabled(service.getEnabled())
                .instanceCount(instances.size())
                .healthyInstanceCount(healthyCount)
                .status(overallStatus)
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt());
        if (includeInstances) {
            builder.instances(instances.stream().map(this::toInstanceResponse).collect(Collectors.toList()));
        }
        return builder.build();
    }

    private UnifiedGatewayServiceInstanceResponse toInstanceResponse(UnifiedGatewayServiceInstance instance) {
        String latencyStr = (String) redisTemplate.opsForHash().get(HEALTH_KEY_PREFIX + instance.getId(), "latency");
        Long latencyMs = latencyStr != null ? Long.parseLong(latencyStr) : null;
        return UnifiedGatewayServiceInstanceResponse.builder()
                .id(instance.getId())
                .serviceId(instance.getServiceId())
                .host(instance.getHost())
                .port(instance.getPort())
                .weight(instance.getWeight())
                .healthCheckPath(instance.getHealthCheckPath())
                .status(instance.getStatus())
                .lastHeartbeat(instance.getLastHeartbeat())
                .consecutiveFailures(instance.getConsecutiveFailures())
                .enabled(instance.getEnabled())
                .latencyMs(latencyMs)
                .createdAt(instance.getCreatedAt())
                .updatedAt(instance.getUpdatedAt())
                .build();
    }
}
