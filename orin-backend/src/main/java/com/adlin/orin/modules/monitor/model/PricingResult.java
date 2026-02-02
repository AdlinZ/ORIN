package com.adlin.orin.modules.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingResult {
    private BigDecimal internalCost;
    private BigDecimal externalPrice;
    private BigDecimal profit;
    private String currency;

    public static PricingResult zero() {
        return PricingResult.builder()
                .internalCost(BigDecimal.ZERO)
                .externalPrice(BigDecimal.ZERO)
                .profit(BigDecimal.ZERO)
                .currency("USD")
                .build();
    }
}
