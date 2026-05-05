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
@Table(name = "gateway_routes")
public class UnifiedGatewayRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "path_pattern", nullable = false, length = 500)
    private String pathPattern;

    @Column(name = "method", length = 10)
    @Builder.Default
    private String method = "ALL";

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    @Column(name = "strip_prefix")
    @Builder.Default
    private Boolean stripPrefix = false;

    @Column(name = "rewrite_path", length = 500)
    private String rewritePath;

    @Column(name = "timeout_ms")
    @Builder.Default
    private Integer timeoutMs = 30000;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "load_balance", length = 20)
    @Builder.Default
    private String loadBalance = "ROUND_ROBIN";

    @Column(name = "auth_required")
    @Builder.Default
    private Boolean authRequired = true;

    @Column(name = "rate_limit_policy_id")
    private Long rateLimitPolicyId;

    @Column(name = "circuit_breaker_policy_id")
    private Long circuitBreakerPolicyId;

    @Column(name = "retry_policy_id")
    private Long retryPolicyId;

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "description", length = 500)
    private String description;

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
