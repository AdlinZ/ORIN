package com.adlin.orin.modules.gateway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "gateway_retry_policies")
public class UnifiedGatewayRetryPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "max_attempts")
    @Builder.Default
    private Integer maxAttempts = 3;

    @Column(name = "retry_on_status_codes", length = 100)
    @Builder.Default
    private String retryOnStatusCodes = "500,502,503,504";

    @Column(name = "retry_on_exceptions", length = 500)
    private String retryOnExceptions;

    @Column(name = "backoff_multiplier")
    @Builder.Default
    private Double backoffMultiplier = 2.0;

    @Column(name = "initial_interval_ms")
    @Builder.Default
    private Integer initialIntervalMs = 100;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
