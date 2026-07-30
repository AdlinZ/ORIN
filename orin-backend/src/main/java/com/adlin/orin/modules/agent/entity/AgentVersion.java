package com.adlin.orin.modules.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 智能体版本实体
 * 用于存储智能体配置的历史版本，支持版本回滚和对比
 */
@Entity
@Table(name = "agent_versions", indexes = {
        @Index(name = "idx_agent_id", columnList = "agent_id"),
        @Index(name = "idx_created_at", columnList = "created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_version", columnNames = { "agent_id", "version_number" })
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 关联的智能体 ID
     */
    @Column(name = "agent_id", nullable = false, length = 50)
    private String agentId;

    /**
     * 版本号 (自动递增)
     */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /**
     * 版本标签 (可选，如 "v1.0-stable")
     */
    @Column(name = "version_tag", length = 50)
    private String versionTag;

    /**
     * 配置快照 (JSON 格式)
     * 存储当前版本的完整配置
     */
    @Column(name = "config_snapshot", columnDefinition = "JSON", nullable = false)
    private String configSnapshot;

    /**
     * 变更说明
     */
    @Column(name = "change_description", columnDefinition = "TEXT")
    private String changeDescription;

    /**
     * 创建者
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 是否为当前激活版本
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
