package com.adlin.orin.modules.monitor.controller;

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

    @GetMapping("/config")
    public List<ModelPricing> getAllPricing() {
        return pricingRepository.findAll();
    }

    @PostMapping("/config")
    public ModelPricing savePricing(@RequestBody ModelPricing pricing) {
        // If updating an existing rule (by ID or provider+group)
        if (pricing.getId() == null) {
            // Check existence
            pricingRepository.findByProviderIdAndTenantGroup(pricing.getProviderId(), pricing.getTenantGroup())
                    .ifPresent(existing -> pricing.setId(existing.getId()));
        }

        ModelPricing saved = pricingRepository.save(pricing);
        pricingService.clearCache(); // Invalidate cache immediately
        return saved;
    }

    @DeleteMapping("/config/{id}")
    public void deletePricing(@PathVariable Long id) {
        pricingRepository.deleteById(id);
        pricingService.clearCache();
    }
}
