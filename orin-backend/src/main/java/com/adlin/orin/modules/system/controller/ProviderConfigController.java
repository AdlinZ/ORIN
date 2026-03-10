package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.system.entity.ProviderConfig;
import com.adlin.orin.modules.system.service.ProviderConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 供应商配置 Controller
 * 提供统一的供应商列表API，所有前端页面都从这里获取供应商顺序
 */
@RestController
@RequestMapping("/api/v1/system/providers")
@RequiredArgsConstructor
public class ProviderConfigController {

    private final ProviderConfigService providerConfigService;

    /**
     * 获取所有已启用的供应商列表（按显示顺序）
     */
    @GetMapping
    public ResponseEntity<List<ProviderConfig>> getEnabledProviders() {
        return ResponseEntity.ok(providerConfigService.getEnabledProviders());
    }

    /**
     * 获取所有供应商列表（包括禁用的，按显示顺序）
     */
    @GetMapping("/all")
    public ResponseEntity<List<ProviderConfig>> getAllProviders() {
        return ResponseEntity.ok(providerConfigService.getAllProviders());
    }

    /**
     * 获取供应商名称映射（key -> name）
     */
    @GetMapping("/map")
    public ResponseEntity<Map<String, String>> getProviderNameMap() {
        return ResponseEntity.ok(providerConfigService.getProviderNameMap());
    }

    /**
     * 更新供应商配置
     */
    @PutMapping("/{providerKey}")
    public ResponseEntity<ProviderConfig> updateProvider(
            @PathVariable String providerKey,
            @RequestBody ProviderConfig provider) {
        provider.setProviderKey(providerKey);
        return ResponseEntity.ok(providerConfigService.updateProvider(provider));
    }

    /**
     * 批量更新供应商显示顺序
     */
    @PutMapping("/display-order")
    public ResponseEntity<Void> updateDisplayOrders(@RequestBody Map<String, Integer> orders) {
        providerConfigService.updateDisplayOrders(orders);
        return ResponseEntity.ok().build();
    }

    /**
     * 启用/禁用供应商
     */
    @PutMapping("/{providerKey}/enabled")
    public ResponseEntity<Void> setEnabled(
            @PathVariable String providerKey,
            @RequestParam boolean enabled) {
        providerConfigService.setEnabled(providerKey, enabled);
        return ResponseEntity.ok().build();
    }
}
