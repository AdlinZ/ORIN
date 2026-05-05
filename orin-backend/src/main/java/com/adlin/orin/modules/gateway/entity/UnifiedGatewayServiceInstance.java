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
@Table(name = "gateway_service_instances")
public class UnifiedGatewayServiceInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "host", nullable = false, length = 200)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port;

    @Column(name = "weight")
    @Builder.Default
    private Integer weight = 100;

    @Column(name = "health_check_path", length = 200)
    private String healthCheckPath;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "UP";

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @Column(name = "consecutive_failures")
    @Builder.Default
    private Integer consecutiveFailures = 0;

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
        lastHeartbeat = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
