package com.adlin.orin.gateway.service;

import com.adlin.orin.gateway.adapter.ProviderAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provider注册和管理服务
 * 负责Provider的注册、移除、查询和健康检查
 */
@Slf4j
@Service
public class ProviderRegistry {

    /**
     * Provider存储 - Key: providerId, Value: ProviderAdapter
     */
    private final Map<String, ProviderAdapter> providers = new ConcurrentHashMap<>();

    /**
     * Provider健康状态 - Key: providerId, Value: isHealthy
     */
    private final Map<String, Boolean> healthStatus = new ConcurrentHashMap<>();

    /**
     * Provider最后健康检查时间
     */
    private final Map<String, Long> lastHealthCheck = new ConcurrentHashMap<>();

    /**
     * 健康检查间隔（毫秒）
     */
    private static final long HEALTH_CHECK_INTERVAL = 60000; // 1分钟

    /**
     * 注册Provider
     * 
     * @param providerId Provider唯一标识
     * @param adapter    Provider适配器实例
     */
    public void registerProvider(String providerId, ProviderAdapter adapter) {
        providers.put(providerId, adapter);
        healthStatus.put(providerId, true);
        lastHealthCheck.put(providerId, 0L);
        log.info("Provider registered: {} (type: {})", providerId, adapter.getProviderType());
    }

    /**
     * 移除Provider
     * 
     * @param providerId Provider标识
     */
    public void unregisterProvider(String providerId) {
        providers.remove(providerId);
        healthStatus.remove(providerId);
        lastHealthCheck.remove(providerId);
        log.info("Provider unregistered: {}", providerId);
    }

    /**
     * 获取Provider
     * 
     * @param providerId Provider标识
     * @return Provider适配器
     */
    public Optional<ProviderAdapter> getProvider(String providerId) {
        return Optional.ofNullable(providers.get(providerId));
    }

    /**
     * 获取所有Provider
     * 
     * @return Provider列表
     */
    public List<ProviderAdapter> getAllProviders() {
        return new ArrayList<>(providers.values());
    }

    /**
     * 获取所有健康的Provider
     * 
     * @return 健康的Provider列表
     */
    public List<ProviderAdapter> getHealthyProviders() {
        return providers.entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(healthStatus.get(entry.getKey())))
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * 获取指定类型的Provider
     * 
     * @param providerType Provider类型
     * @return Provider列表
     */
    public List<ProviderAdapter> getProvidersByType(String providerType) {
        return providers.values().stream()
                .filter(provider -> provider.getProviderType().equals(providerType))
                .toList();
    }

    /**
     * 检查Provider健康状态
     * 
     * @param providerId Provider标识
     * @return 健康状态
     */
    public Mono<Boolean> checkHealth(String providerId) {
        ProviderAdapter provider = providers.get(providerId);
        if (provider == null) {
            return Mono.just(false);
        }

        // 如果最近检查过，直接返回缓存结果
        Long lastCheck = lastHealthCheck.get(providerId);
        if (lastCheck != null && System.currentTimeMillis() - lastCheck < HEALTH_CHECK_INTERVAL) {
            return Mono.just(healthStatus.getOrDefault(providerId, false));
        }

        // 执行健康检查
        return provider.healthCheck()
                .doOnNext(healthy -> {
                    healthStatus.put(providerId, healthy);
                    lastHealthCheck.put(providerId, System.currentTimeMillis());
                    if (!healthy) {
                        log.warn("Provider {} is unhealthy", providerId);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Health check failed for provider {}: {}", providerId, e.getMessage());
                    healthStatus.put(providerId, false);
                    lastHealthCheck.put(providerId, System.currentTimeMillis());
                    return Mono.just(false);
                });
    }

    /**
     * 检查所有Provider健康状态
     */
    public Mono<Map<String, Boolean>> checkAllHealth() {
        Map<String, Boolean> results = new HashMap<>();
        List<Mono<Void>> checks = new ArrayList<>();

        for (String providerId : providers.keySet()) {
            Mono<Void> check = checkHealth(providerId)
                    .doOnNext(healthy -> results.put(providerId, healthy))
                    .then();
            checks.add(check);
        }

        return Mono.when(checks).thenReturn(results);
    }

    /**
     * 获取Provider统计信息
     * 
     * @return 统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalProviders", providers.size());
        stats.put("healthyProviders", (int) healthStatus.values().stream().filter(Boolean::booleanValue).count());
        stats.put("unhealthyProviders", (int) healthStatus.values().stream().filter(h -> !h).count());

        // 按类型统计
        Map<String, Long> byType = new HashMap<>();
        providers.values().forEach(provider -> {
            String type = provider.getProviderType();
            byType.put(type, byType.getOrDefault(type, 0L) + 1);
        });
        stats.put("providersByType", byType);

        return stats;
    }

    /**
     * 获取Provider详细信息
     * 
     * @return Provider详细信息列表
     */
    public List<Map<String, Object>> getProviderDetails() {
        List<Map<String, Object>> details = new ArrayList<>();

        providers.forEach((id, provider) -> {
            Map<String, Object> detail = new HashMap<>();
            detail.put("id", id);
            detail.put("name", provider.getProviderName());
            detail.put("type", provider.getProviderType());
            detail.put("healthy", healthStatus.getOrDefault(id, false));
            detail.put("lastHealthCheck", lastHealthCheck.getOrDefault(id, 0L));
            detail.put("config", provider.getProviderConfig());
            details.add(detail);
        });

        return details;
    }
}
