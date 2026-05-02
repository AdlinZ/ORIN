package com.adlin.orin.modules.integrationsync.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "integration_sync_conflict", indexes = {
        @Index(name = "idx_sync_conflict_integration", columnList = "integration_id"),
        @Index(name = "idx_sync_conflict_resource", columnList = "orin_resource_type,orin_resource_id"),
        @Index(name = "idx_sync_conflict_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncConflict {

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

    @Column(name = "conflict_type", nullable = false, length = 40)
    private String conflictType;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "local_hash", length = 64)
    private String localHash;

    @Column(name = "external_hash", length = 64)
    private String externalHash;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "local_snapshot", columnDefinition = "TEXT")
    private String localSnapshot;

    @Column(name = "external_snapshot", columnDefinition = "TEXT")
    private String externalSnapshot;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
