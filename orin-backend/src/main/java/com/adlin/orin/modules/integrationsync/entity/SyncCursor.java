package com.adlin.orin.modules.integrationsync.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "integration_sync_cursor", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sync_cursor", columnNames = {"integration_id", "resource_type", "direction"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncCursor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "integration_id", nullable = false)
    private Long integrationId;

    @Column(name = "resource_type", nullable = false, length = 40)
    private String resourceType;

    @Column(name = "direction", nullable = false, length = 20)
    private String direction;

    @Column(name = "cursor_value", length = 255)
    private String cursorValue;

    @Column(name = "last_seen_hash", length = 64)
    private String lastSeenHash;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
