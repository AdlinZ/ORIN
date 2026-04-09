package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 外部集成配置变更审计日志
 */
@Entity
@Table(name = "knowledge_integration_audit_log", indexes = {
        @Index(name = "idx_integration_id", columnList = "integration_id"),
        @Index(name = "idx_audit_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 集成 ID
     */
    @Column(name = "integration_id")
    private Long integrationId;

    /**
     * 集成名称
     */
    @Column(name = "integration_name", length = 100)
    private String integrationName;

    /**
     * 变更操作: CREATE, UPDATE, DELETE, HEALTH_CHECK, SYNC
     */
    @Column(name = "action", length = 20, nullable = false)
    private String action;

    /**
     * 操作人（从 SecurityContext 获取，匿名时记录 IP）
     */
    @Column(name = "operator", length = 100)
    private String operator;

    /**
     * 变更前配置（脱敏）
     */
    @Column(name = "before_state", columnDefinition = "TEXT")
    private String beforeState;

    /**
     * 变更后配置（脱敏）
     */
    @Column(name = "after_state", columnDefinition = "TEXT")
    private String afterState;

    /**
     * 变更时间
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
