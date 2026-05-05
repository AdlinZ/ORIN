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
@Table(name = "gateway_acl_rules")
public class UnifiedGatewayAclRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "ip_pattern", length = 500)
    private String ipPattern;

    @Column(name = "path_pattern", length = 500)
    private String pathPattern;

    @Column(name = "api_key_required")
    @Builder.Default
    private Boolean apiKeyRequired = false;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

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
