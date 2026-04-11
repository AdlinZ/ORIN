package com.adlin.orin.modules.knowledge.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Dify 应用实体
 */
@Entity
@Table(name = "dify_apps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifyApp {

    @Id
    @Column(length = 64)
    private String id;

    @Column(length = 255)
    private String name;

    @Column(length = 32)
    private String type; // chat/agent/workflow/completion

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "agent_id", length = 64)
    private String agentId; // 关联的 ORIN Agent ID

    @Column(name = "icon_url", length = 512)
    private String iconUrl;

    @Column(length = 32)
    private String mode;

    @Column(name = "created_from")
    private String createdFrom;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @PrePersist
    public void prePersist() {
        this.lastSyncedAt = LocalDateTime.now();
    }
}
