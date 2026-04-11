package com.adlin.orin.modules.knowledge.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Dify 工作流实体
 */
@Entity
@Table(name = "dify_workflows")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifyWorkflow {

    @Id
    @Column(length = 64)
    private String id;

    @Column(length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "agent_id", length = 64)
    private String agentId; // 关联的 ORIN Agent ID

    @Column(name = "app_id", length = 64)
    private String appId; // Dify 应用 ID

    @Column(name = "dsl_definition", columnDefinition = "LONGTEXT")
    private String dslDefinition; // 工作流 DSL 定义

    @Column(length = 32)
    private String status; // draft/published

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
