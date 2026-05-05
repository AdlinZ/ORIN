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
@Table(name = "gateway_rate_limit_policies")
public class UnifiedGatewayRateLimitPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "dimension", length = 20)
    @Builder.Default
    private String dimension = "GLOBAL";

    @Column(name = "capacity")
    @Builder.Default
    private Integer capacity = 100;

    @Column(name = "window_seconds")
    @Builder.Default
    private Integer windowSeconds = 60;

    @Column(name = "burst")
    @Builder.Default
    private Integer burst = 10;

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
