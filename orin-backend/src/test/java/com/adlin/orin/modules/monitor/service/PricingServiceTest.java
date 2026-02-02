package com.adlin.orin.modules.monitor.service;

import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.monitor.entity.ModelPricing;
import com.adlin.orin.modules.monitor.model.PricingResult;
import com.adlin.orin.modules.monitor.repository.ModelPricingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PricingServiceTest {

    @Mock
    private ModelPricingRepository pricingRepository;

    @InjectMocks
    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService.init(); // Initialize Cache
    }

    @Test
    void calculate_PerToken_ShouldReturnCorrectValues() {
        // Setup
        String providerId = "gpt-4";
        ModelPricing pricing = ModelPricing.builder()
                .providerId(providerId)
                .billingMode(ModelPricing.BillingMode.PER_TOKEN)
                .inputCostUnit(new BigDecimal("0.03")) // $0.03 per 1k input
                .outputCostUnit(new BigDecimal("0.06")) // $0.06 per 1k output
                .inputPriceUnit(new BigDecimal("0.06")) // $0.06 per 1k input (User Price)
                .outputPriceUnit(new BigDecimal("0.12")) // $0.12 per 1k output (User Price)
                .currency("USD")
                .build();

        when(pricingRepository.findByProviderIdAndTenantGroup(providerId, "default"))
                .thenReturn(Optional.of(pricing));

        AuditLog log = AuditLog.builder()
                .providerId(providerId)
                .promptTokens(1000)
                .completionTokens(1000)
                .build();

        // Execution
        PricingResult result = pricingService.calculate(log);

        // Verification
        // Internal Cost: (1000/1000 * 0.03) + (1000/1000 * 0.06) = 0.09
        assertEquals(0, new BigDecimal("0.09").compareTo(result.getInternalCost()));

        // External Price: (1000/1000 * 0.06) + (1000/1000 * 0.12) = 0.18
        assertEquals(0, new BigDecimal("0.18").compareTo(result.getExternalPrice()));

        // Profit: 0.18 - 0.09 = 0.09
        assertEquals(0, new BigDecimal("0.09").compareTo(result.getProfit()));
    }

    @Test
    void calculate_PerRequest_ShouldReturnCorrectValues() {
        // Setup
        String providerId = "dall-e-3";
        ModelPricing pricing = ModelPricing.builder()
                .providerId(providerId)
                .billingMode(ModelPricing.BillingMode.PER_REQUEST)
                .inputCostUnit(new BigDecimal("0.040")) // Cost per request
                .outputCostUnit(BigDecimal.ZERO)
                .inputPriceUnit(new BigDecimal("0.080")) // Price per request
                .outputPriceUnit(BigDecimal.ZERO)
                .currency("USD")
                .build();

        when(pricingRepository.findByProviderIdAndTenantGroup(providerId, "default"))
                .thenReturn(Optional.of(pricing));

        AuditLog log = AuditLog.builder()
                .providerId(providerId)
                .build();

        // Execution
        PricingResult result = pricingService.calculate(log);

        // Verification
        // Cost: 0.040
        assertEquals(0, new BigDecimal("0.040").compareTo(result.getInternalCost()));
        // Price: 0.080
        assertEquals(0, new BigDecimal("0.080").compareTo(result.getExternalPrice()));
    }

    @Test
    void calculate_NoRule_ShouldReturnZero() {
        String providerId = "unknown";
        when(pricingRepository.findByProviderIdAndTenantGroup(providerId, "default"))
                .thenReturn(Optional.empty());

        AuditLog log = AuditLog.builder()
                .providerId(providerId)
                .build();

        PricingResult result = pricingService.calculate(log);

        assertEquals(BigDecimal.ZERO, result.getInternalCost());
        assertEquals(BigDecimal.ZERO, result.getExternalPrice());
    }
}
