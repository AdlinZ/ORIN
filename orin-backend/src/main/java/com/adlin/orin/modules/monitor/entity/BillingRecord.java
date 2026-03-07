package com.adlin.orin.modules.monitor.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 计费记录实体
 * 存储从第三方 API 拉取的计费数据
 */
@Data
@Entity
@Table(name = "billing_records")
public class BillingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(name = "provider_name")
    private String providerName;

    @Column(name = "billing_date", nullable = false)
    private LocalDate billingDate;

    @Column(name = "total_cost", precision = 18, scale = 8)
    private BigDecimal totalCost;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "remark")
    private String remark;
}
