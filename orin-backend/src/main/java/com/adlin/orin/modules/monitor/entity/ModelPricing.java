package com.adlin.orin.modules.monitor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI模型定价规则实体（双轨制：进货价 vs 售卖价）
 */
@Entity
@Table(name = "model_pricing", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "provider_id", "tenant_group" })
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelPricing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 模型/供应商ID (e.g. "gpt-4", "dall-e-3")
     */
    @Column(name = "provider_id", nullable = false)
    private String providerId;

    /**
     * 租户分组 (e.g. "default", "VIP", "internal")
     * 默认为 "default"
     */
    @Builder.Default
    @Column(name = "tenant_group", nullable = false)
    private String tenantGroup = "default";

    /**
     * 计费模式
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_mode", nullable = false)
    private BillingMode billingMode;

    /**
     * 内部成本 - 输入单价 (每 1K Tokens 或 每单位)
     */
    @Column(name = "input_cost_unit", precision = 19, scale = 8)
    private BigDecimal inputCostUnit;

    /**
     * 内部成本 - 输出单价 (每 1K Tokens 或 每单位)
     */
    @Column(name = "output_cost_unit", precision = 19, scale = 8)
    private BigDecimal outputCostUnit;

    /**
     * 外部售价 - 输入单价 (每 1K Tokens 或 每单位)
     */
    @Column(name = "input_price_unit", precision = 19, scale = 8)
    private BigDecimal inputPriceUnit;

    /**
     * 外部售价 - 输出单价 (每 1K Tokens 或 每单位)
     */
    @Column(name = "output_price_unit", precision = 19, scale = 8)
    private BigDecimal outputPriceUnit;

    /**
     * 货币符号 (默认 USD)
     */
    @Builder.Default
    @Column(length = 10)
    private String currency = "USD";

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum BillingMode {
        PER_TOKEN, // 按 Token 计费 (Text)
        PER_REQUEST, // 按次计费 (Image)
        PER_SECOND // 按时长计费 (Audio/Video)
    }
}
