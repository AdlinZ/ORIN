package com.adlin.orin.modules.integrationsync.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "integration_external_resource_mapping", indexes = {
        @Index(name = "idx_mapping_orin_resource", columnList = "orin_resource_type,orin_resource_id"),
        @Index(name = "idx_mapping_external_resource", columnList = "platform_type,external_resource_type,external_resource_id"),
        @Index(name = "idx_mapping_integration", columnList = "integration_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_mapping_integration_orin", columnNames = {"integration_id", "orin_resource_type", "orin_resource_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalResourceMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "integration_id", nullable = false)
    private Long integrationId;

    @Column(name = "platform_type", nullable = false, length = 30)
    private String platformType;

    @Column(name = "orin_resource_type", nullable = false, length = 40)
    private String orinResourceType;

    @Column(name = "orin_resource_id", nullable = false, length = 128)
    private String orinResourceId;

    @Column(name = "external_resource_type", length = 80)
    private String externalResourceType;

    @Column(name = "external_resource_id", length = 128)
    private String externalResourceId;

    @Column(name = "external_version", length = 128)
    private String externalVersion;

    @Column(name = "external_updated_at")
    private LocalDateTime externalUpdatedAt;

    @Column(name = "last_synced_hash", length = 64)
    private String lastSyncedHash;

    @Column(name = "sync_direction", length = 20)
    private String syncDirection;

    @Column(name = "sync_status", length = 20)
    @Builder.Default
    private String syncStatus = "PENDING";

    @Column(name = "raw_snapshot", columnDefinition = "TEXT")
    private String rawSnapshot;

    @Column(name = "compatibility_report", columnDefinition = "TEXT")
    private String compatibilityReport;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
