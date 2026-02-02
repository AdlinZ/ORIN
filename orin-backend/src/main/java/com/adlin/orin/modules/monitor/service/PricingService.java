package com.adlin.orin.modules.monitor.service;

import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.monitor.entity.ModelPricing;
import com.adlin.orin.modules.monitor.model.PricingResult;
import com.adlin.orin.modules.monitor.repository.ModelPricingRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingService {

    private final ModelPricingRepository pricingRepository;

    private Cache<String, ModelPricing> pricingCache;

    @PostConstruct
    public void init() {
        pricingCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
    }

    /**
     * 计算审计日志的费用
     */
    public PricingResult calculate(AuditLog auditLog) {
        if (auditLog == null || !StringUtils.hasText(auditLog.getProviderId())) {
            return PricingResult.zero();
        }

        // 1. Determine Tenant Group (Future: look up tenant config, for now default)
        String tenantGroup = "default";
        // String tenantId = auditLog.getTenantId(); ... lookup group

        // 2. Get Pricing Rule (Cache -> DB)
        String cacheKey = auditLog.getProviderId() + ":" + tenantGroup;
        ModelPricing pricing = pricingCache.get(cacheKey,
                key -> pricingRepository.findByProviderIdAndTenantGroup(auditLog.getProviderId(), tenantGroup)
                        .orElse(null));

        if (pricing == null) {
            // Try fallback to just providerId (if specific group missing)
            // or fallback to generic "default" rule...
            // For now, if no rule, return zero cost.
            return PricingResult.zero();
        }

        // 3. Calculate
        BigDecimal internalCost = BigDecimal.ZERO;
        BigDecimal externalPrice = BigDecimal.ZERO;

        try {
            switch (pricing.getBillingMode()) {
                case PER_TOKEN:
                    // input
                    if (auditLog.getPromptTokens() != null && auditLog.getPromptTokens() > 0) {
                        BigDecimal inputs = BigDecimal.valueOf(auditLog.getPromptTokens()).divide(
                                BigDecimal.valueOf(1000),
                                8, RoundingMode.HALF_UP);
                        if (pricing.getInputCostUnit() != null) {
                            internalCost = internalCost.add(inputs.multiply(pricing.getInputCostUnit()));
                        }
                        if (pricing.getInputPriceUnit() != null) {
                            externalPrice = externalPrice.add(inputs.multiply(pricing.getInputPriceUnit()));
                        }
                    }
                    // output
                    if (auditLog.getCompletionTokens() != null && auditLog.getCompletionTokens() > 0) {
                        BigDecimal outputs = BigDecimal.valueOf(auditLog.getCompletionTokens())
                                .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);
                        if (pricing.getOutputCostUnit() != null) {
                            internalCost = internalCost.add(outputs.multiply(pricing.getOutputCostUnit()));
                        }
                        if (pricing.getOutputPriceUnit() != null) {
                            externalPrice = externalPrice.add(outputs.multiply(pricing.getOutputPriceUnit()));
                        }
                    }
                    break;

                case PER_REQUEST:
                    // Sum both input and output units as flat fee per request
                    if (pricing.getInputCostUnit() != null) {
                        internalCost = internalCost.add(pricing.getInputCostUnit());
                    }
                    if (pricing.getOutputCostUnit() != null) {
                        internalCost = internalCost.add(pricing.getOutputCostUnit());
                    }
                    if (pricing.getInputPriceUnit() != null) {
                        externalPrice = externalPrice.add(pricing.getInputPriceUnit());
                    }
                    if (pricing.getOutputPriceUnit() != null) {
                        externalPrice = externalPrice.add(pricing.getOutputPriceUnit());
                    }
                    break;

                case PER_SECOND:
                    // Duration based (use responseTime as proxy for duration if needed? or custom
                    // field)
                    // Currently AuditLog has responseTime (ms).
                    // Let's assume outputUnit is "Price Per Second".
                    if (auditLog.getResponseTime() != null) {
                        BigDecimal seconds = BigDecimal.valueOf(auditLog.getResponseTime()).divide(
                                BigDecimal.valueOf(1000),
                                8, RoundingMode.HALF_UP);
                        if (pricing.getOutputCostUnit() != null) {
                            internalCost = seconds.multiply(pricing.getOutputCostUnit());
                        }
                        if (pricing.getOutputPriceUnit() != null) {
                            externalPrice = seconds.multiply(pricing.getOutputPriceUnit());
                        }
                    }
                    break;
            }
        } catch (Exception e) {
            log.error("Error calculating price for {}: {}", auditLog.getId(), e.getMessage());
            return PricingResult.zero();
        }

        return PricingResult.builder()
                .internalCost(internalCost)
                .externalPrice(externalPrice)
                .profit(externalPrice.subtract(internalCost))
                .currency(pricing.getCurrency())
                .build();
    }

    public void clearCache() {
        pricingCache.invalidateAll();
    }
}
