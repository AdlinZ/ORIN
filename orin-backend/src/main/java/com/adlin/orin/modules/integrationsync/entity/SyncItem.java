package com.adlin.orin.modules.integrationsync.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "integration_sync_item", indexes = {
        @Index(name = "idx_sync_item_job", columnList = "sync_job_id"),
        @Index(name = "idx_sync_item_orin_resource", columnList = "orin_resource_type,orin_resource_id"),
        @Index(name = "idx_sync_item_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sync_job_id", nullable = false)
    private Long syncJobId;

    @Column(name = "orin_resource_type", length = 40)
    private String orinResourceType;

    @Column(name = "orin_resource_id", length = 128)
    private String orinResourceId;

    @Column(name = "external_resource_type", length = 80)
    private String externalResourceType;

    @Column(name = "external_resource_id", length = 128)
    private String externalResourceId;

    @Column(name = "change_log_id")
    private Long changeLogId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
