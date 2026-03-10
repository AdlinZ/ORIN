package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.ProviderConfig;
import com.adlin.orin.modules.system.repository.ProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 供应商配置 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderConfigService {

    private final ProviderConfigRepository providerConfigRepository;

    /**
     * 获取所有已启用的供应商，按显示顺序排序
     */
    public List<ProviderConfig> getEnabledProviders() {
        return providerConfigRepository.findAllEnabledOrderByDisplayOrder();
    }

    /**
     * 获取所有供应商，按显示顺序排序
     */
    public List<ProviderConfig> getAllProviders() {
        return providerConfigRepository.findAllOrderByDisplayOrder();
    }

    /**
     * 获取供应商名称映射 (key -> name)
     */
    public Map<String, String> getProviderNameMap() {
        return getEnabledProviders().stream()
                .collect(Collectors.toMap(
                        ProviderConfig::getProviderKey,
                        ProviderConfig::getProviderName
                ));
    }

    /**
     * 更新供应商配置
     */
    @Transactional
    public ProviderConfig updateProvider(ProviderConfig provider) {
        return providerConfigRepository.save(provider);
    }

    /**
     * 更新供应商显示顺序
     */
    @Transactional
    public void updateDisplayOrder(String providerKey, int order) {
        ProviderConfig provider = providerConfigRepository.findById(providerKey)
                .orElseThrow(() -> new RuntimeException("Provider not found: " + providerKey));
        provider.setDisplayOrder(order);
        providerConfigRepository.save(provider);
    }

    /**
     * 批量更新供应商显示顺序
     */
    @Transactional
    public void updateDisplayOrders(Map<String, Integer> orders) {
        orders.forEach((key, order) -> {
            providerConfigRepository.findById(key).ifPresent(provider -> {
                provider.setDisplayOrder(order);
                providerConfigRepository.save(provider);
            });
        });
    }

    /**
     * 启用/禁用供应商
     */
    @Transactional
    public void setEnabled(String providerKey, boolean enabled) {
        ProviderConfig provider = providerConfigRepository.findById(providerKey)
                .orElseThrow(() -> new RuntimeException("Provider not found: " + providerKey));
        provider.setEnabled(enabled);
        providerConfigRepository.save(provider);
    }

    /**
     * 检查供应商是否存在
     */
    public boolean exists(String providerKey) {
        return providerConfigRepository.existsById(providerKey);
    }
}
