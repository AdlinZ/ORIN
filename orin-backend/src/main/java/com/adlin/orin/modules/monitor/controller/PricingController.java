package com.adlin.orin.modules.monitor.controller;

import com.adlin.orin.common.dto.Result;
import com.adlin.orin.modules.monitor.entity.ModelPricing;
import com.adlin.orin.modules.monitor.repository.ModelPricingRepository;
import com.adlin.orin.modules.monitor.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final ModelPricingRepository pricingRepository;
    private final PricingService pricingService;

    /**
     * 获取全部定价规则列表
     */
    @GetMapping("/config")
    public Result<List<ModelPricing>> getAllPricing() {
        return Result.success(pricingRepository.findAll());
    }

    /**
     * 精确查询：按模型ID + 租户分组获取单条定价规则
     * 兼容包含斜杠的 providerId（使用 QueryParam）
     */
    @GetMapping("/config/lookup")
    public Result<ModelPricing> lookupPricing(
            @RequestParam String providerId,
            @RequestParam(defaultValue = "default") String tenantGroup) {
        return pricingRepository.findByProviderIdAndTenantGroup(providerId, tenantGroup)
                .map(Result::success)
                .orElse(Result.success(null));
    }

    /**
     * 精确查询：按模型ID + 租户分组获取单条定价规则 (路径参数版，保留兼容性)
     */
    @GetMapping("/config/{providerId}/{tenantGroup}")
    public Result<ModelPricing> getPricingByProviderAndGroup(
            @PathVariable String providerId,
            @PathVariable String tenantGroup) {
        return lookupPricing(providerId, tenantGroup);
    }

    /**
     * 新增或更新定价规则（按 providerId + tenantGroup 去重）
     */
    @PostMapping("/config")
    public Result<ModelPricing> savePricing(@RequestBody ModelPricing pricing) {
        if (pricing.getId() == null) {
            pricingRepository.findByProviderIdAndTenantGroup(pricing.getProviderId(), pricing.getTenantGroup())
                    .ifPresent(existing -> pricing.setId(existing.getId()));
        }
        ModelPricing saved = pricingRepository.save(pricing);
        pricingService.clearCache();
        return Result.success(saved);
    }

    /**
     * 删除定价规则
     */
    @DeleteMapping("/config/{id}")
    public Result<Void> deletePricing(@PathVariable Long id) {
        pricingRepository.deleteById(id);
        pricingService.clearCache();
        return Result.success();
    }
}
