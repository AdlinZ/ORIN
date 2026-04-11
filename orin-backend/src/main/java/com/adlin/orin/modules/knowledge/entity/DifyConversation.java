package com.adlin.orin.modules.knowledge.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Dify 对话历史实体
 */
@Entity
@Table(name = "dify_conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifyConversation {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "app_id", length = 64)
    private String appId;

    @Column(name = "agent_id", length = 64)
    private String agentId; // 关联的 ORIN Agent ID

    @Column(length = 128)
    private String name;

    @Column(length = 32)
    private String mode; // chat/workflow

    @Column(name = "from_source")
    private String fromSource;

    @Column(name = "from_end_user_id")
    private String fromEndUserId;

    @Column(name = "is_deleted")
    private Boolean isDeleted;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
}
