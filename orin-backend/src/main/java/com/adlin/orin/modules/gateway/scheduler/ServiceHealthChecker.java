package com.adlin.orin.modules.gateway.scheduler;

import com.adlin.orin.modules.gateway.entity.GatewayServiceInstance;
import com.adlin.orin.modules.gateway.repository.GatewayServiceInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceHealthChecker {

    private final GatewayServiceInstanceRepository instanceRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final RestTemplate restTemplate;

    private static final String HEALTH_KEY_PREFIX = "gateway:health:";
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    @Scheduled(fixedDelay = 30000)
    public void checkHealth() {
        List<GatewayServiceInstance> instances = instanceRepository.findAll();

        for (GatewayServiceInstance instance : instances) {
            if (!instance.getEnabled()) {
                continue;
            }

            if (instance.getHealthCheckPath() == null || instance.getHealthCheckPath().isEmpty()) {
                continue;
            }

            // Add jitter to avoid thundering herd
            try {
                Thread.sleep(new Random().nextInt(5000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            performHealthCheck(instance);
        }
    }

    private void performHealthCheck(GatewayServiceInstance instance) {
        String url = buildHealthCheckUrl(instance);
        long start = System.currentTimeMillis();

        try {
            restTemplate.getForEntity(url, String.class);
            long latency = System.currentTimeMillis() - start;

            instance.setStatus("UP");
            instance.setLastHeartbeat(LocalDateTime.now());
            instance.setConsecutiveFailures(0);
            instanceRepository.save(instance);

            updateRedisHealth(instance.getId(), "UP", latency);
            log.debug("Health check OK for instance {}:{} latency={}ms",
                    instance.getHost(), instance.getPort(), latency);

        } catch (Exception e) {
            int failures = instance.getConsecutiveFailures() + 1;
            instance.setConsecutiveFailures(failures);

            if (failures >= MAX_CONSECUTIVE_FAILURES) {
                instance.setStatus("DOWN");
                log.warn("Instance {}:{} marked DOWN after {} consecutive failures",
                        instance.getHost(), instance.getPort(), failures);
            }
            instance.setLastHeartbeat(LocalDateTime.now());
            instanceRepository.save(instance);

            updateRedisHealth(instance.getId(), failures >= MAX_CONSECUTIVE_FAILURES ? "DOWN" : "DEGRADED", null);
            log.warn("Health check FAILED for instance {}:{}: {}",
                    instance.getHost(), instance.getPort(), e.getMessage());
        }
    }

    private String buildHealthCheckUrl(GatewayServiceInstance instance) {
        String host = instance.getHost() == null ? "" : instance.getHost().trim();
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "http://" + host;
        }
        String path = instance.getHealthCheckPath() == null ? "" : instance.getHealthCheckPath().trim();
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        return String.format("%s:%d%s", host, instance.getPort(), path);
    }

    private void updateRedisHealth(Long instanceId, String status, Long latency) {
        String key = HEALTH_KEY_PREFIX + instanceId;
        redisTemplate.opsForHash().put(key, "status", status);
        if (latency != null) {
            redisTemplate.opsForHash().put(key, "latency", String.valueOf(latency));
        }
        redisTemplate.expire(key, 300, java.util.concurrent.TimeUnit.SECONDS);
    }
}
