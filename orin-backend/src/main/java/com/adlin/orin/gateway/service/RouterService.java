package com.adlin.orin.gateway.service;

import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.config.GatewayModelProperties;
import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 智能路由服务
 * 根据负载、成本、可用性等因素选择最佳Provider
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouterService {

    private final ProviderRegistry providerRegistry;
    private final GatewayModelProperties modelProperties;

    /**
     * 路由策略枚举
     */
    public enum RoutingStrategy {
        /**
         * 轮询
         */
        ROUND_ROBIN,

        /**
         * 最低成本
         */
        LOWEST_COST,

        /**
         * 随机
         */
        RANDOM,

        /**
         * 优先级
         */
        PRIORITY
    }

    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    /**
     * 选择最佳Provider
     * 
     * @param request  请求
     * @param strategy 路由策略
     * @return 选中的Provider
     */
    public Optional<ProviderAdapter> selectProvider(ChatCompletionRequest request, RoutingStrategy strategy) {
        List<ProviderAdapter> healthyProviders = providerRegistry.getHealthyProviders();

        if (healthyProviders.isEmpty()) {
            log.warn("No healthy providers available");
            return Optional.empty();
        }

        return switch (strategy) {
            case ROUND_ROBIN -> selectRoundRobin(healthyProviders);
            case LOWEST_COST -> selectLowestCost(healthyProviders, request);
            case RANDOM -> selectRandom(healthyProviders);
            case PRIORITY -> selectByPriority(healthyProviders);
        };
    }

    /**
     * 根据Provider ID直接选择
     * 
     * @param providerId Provider标识
     * @return Provider
     */
    public Optional<ProviderAdapter> selectProviderById(String providerId) {
        Optional<ProviderAdapter> providerOpt = providerRegistry.getProvider(providerId);
        if (providerOpt.isEmpty()) return Optional.empty();
        boolean healthy = providerRegistry.checkHealth(providerId).block();
        if (!healthy) return Optional.empty();
        return providerOpt;
    }

    /**
     * 根据模型名称智能选择Provider
     * 
     * @param modelName 模型名称
     * @param request   请求
     * @return Provider
     */
    public Optional<ProviderAdapter> selectProviderByModel(String modelName, ChatCompletionRequest request) {
        if (modelName == null || modelName.isBlank()) {
            return selectProvider(request, RoutingStrategy.LOWEST_COST);
        }

        String normalizedModel = modelName.toLowerCase();

        // 尝试从配置的属性中查找映射
        GatewayModelProperties.ModelMapping mapping = modelProperties.findMapping(normalizedModel);
        if (mapping != null) {
            if (mapping.getProviderId() != null && !mapping.getProviderId().isBlank()) {
                Optional<ProviderAdapter> provider = selectProviderById(mapping.getProviderId());
                if (provider.isPresent()) return provider;
            }
            Optional<ProviderAdapter> byType = selectProviderByType(mapping.getProviderType(), request);
            if (byType.isPresent()) return byType;
        }

        // 兜底：内置模型前缀判断（用于未在配置中声明的前缀）
        if (normalizedModel.startsWith("gpt-")) {
            return selectProviderByType("openai", request);
        } else if (normalizedModel.startsWith("dify-")) {
            return selectProviderByType("dify", request);
        } else if (normalizedModel.startsWith("pro/")
                || normalizedModel.startsWith("deepseek-ai/")
                || normalizedModel.startsWith("zai-org/")
                || normalizedModel.startsWith("moonshotai/")
                || normalizedModel.startsWith("minimaxai/")
                || normalizedModel.startsWith("stepfun-ai/")
                || normalizedModel.startsWith("inclusionai/")
                || normalizedModel.startsWith("bytedance-seed/")
                || normalizedModel.startsWith("tencent/")
                || normalizedModel.startsWith("paddlepaddle/")
                || normalizedModel.startsWith("kwaipilot/")) {
            return selectProviderById("siliconflow")
                    .or(() -> selectProviderByType("openai", request));
        } else if (normalizedModel.contains("local") || normalizedModel.contains("llama")
                || normalizedModel.contains("qwen")) {
            Optional<ProviderAdapter> ollama = selectProviderByType("ollama", request);
            if (ollama.isPresent()) return ollama;
            return selectProviderByType("local", request);
        }

        return selectProvider(request, RoutingStrategy.LOWEST_COST);
    }

    /**
     * 根据Provider类型选择
     * 
     * @param providerType Provider类型
     * @param request      请求
     * @return Provider
     */
    public Optional<ProviderAdapter> selectProviderByType(String providerType, ChatCompletionRequest request) {
        List<ProviderAdapter> providers = providerRegistry.getHealthyProvidersByType(providerType);

        if (providers.isEmpty()) {
            log.warn("No healthy providers found for type: {}", providerType);
            return Optional.empty();
        }

        // 在同类型中选择最低成本
        return selectLowestCost(providers, request);
    }

    /**
     * 轮询选择
     */
    private Optional<ProviderAdapter> selectRoundRobin(List<ProviderAdapter> providers) {
        if (providers.isEmpty()) {
            return Optional.empty();
        }

        int rawIndex = roundRobinIndex.getAndUpdate(current -> Math.floorMod(current + 1, providers.size()));
        int index = Math.floorMod(rawIndex, providers.size());
        ProviderAdapter selected = providers.get(index);
        log.debug("Round-robin selected provider: {}", selected.getProviderName());
        return Optional.of(selected);
    }

    /**
     * 最低成本选择
     */
    private Optional<ProviderAdapter> selectLowestCost(List<ProviderAdapter> providers, ChatCompletionRequest request) {
        Optional<ProviderAdapter> selected = providers.stream()
                .min(Comparator.comparingDouble(p -> p.estimateCost(request)));

        selected.ifPresent(provider -> log.debug("Lowest-cost selected provider: {} (estimated cost: ${})",
                provider.getProviderName(),
                provider.estimateCost(request)));

        return selected;
    }

    /**
     * 随机选择
     */
    private Optional<ProviderAdapter> selectRandom(List<ProviderAdapter> providers) {
        if (providers.isEmpty()) {
            return Optional.empty();
        }

        int randomIndex = (int) (Math.random() * providers.size());
        ProviderAdapter selected = providers.get(randomIndex);
        log.debug("Random selected provider: {}", selected.getProviderName());
        return Optional.of(selected);
    }

    /**
     * 优先级选择（目前简单返回第一个，可扩展为配置化优先级）
     */
    private Optional<ProviderAdapter> selectByPriority(List<ProviderAdapter> providers) {
        if (providers.isEmpty()) {
            return Optional.empty();
        }

        // TODO: [Plan] Integrate with Configuration Center (e.g. Nacos/Consul) for
        // dynamic priority rules
        // 目前简单实现：按 Provider ID 字母顺序排序，确保确定性
        // 实际生产中可从配置中心获取优先级列表
        providers.sort(
                java.util.Comparator.comparing(p -> (String) p.getProviderConfig().getOrDefault("providerId", "")));

        ProviderAdapter selected = providers.get(0);
        log.debug("Priority selected provider: {}", selected.getProviderName());
        return Optional.of(selected);
    }

    /**
     * 获取路由统计
     * 
     * @return 统计信息
     */
    public java.util.Map<String, Object> getRoutingStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("healthyProviders", providerRegistry.getHealthyProviders().size());
        stats.put("totalProviders", providerRegistry.getAllProviders().size());
        stats.put("currentRoundRobinIndex", roundRobinIndex);
        return stats;
    }
}
