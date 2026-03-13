package com.adlin.orin.modules.alert.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 告警记录实体
 */
@Entity
@Table(name = "alert_record", indexes = {
        @Index(name = "idx_alert_agent_id", columnList = "agent_id"),
        @Index(name = "idx_alert_type", columnList = "alert_type"),
        @Index(name = "idx_alert_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 关联的智能体 ID
     */
    @Column(name = "agent_id", length = 50)
    private String agentId;

    /**
     * 告警类型
     */
    @Column(name = "alert_type", length = 50)
    private String alertType;

    /**
     * 严重程度: INFO, WARNING, ERROR, CRITICAL
     */
    @Column(name = "severity", length = 20)
    private String severity;

    /**
     * 告警消息
     */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    /**
     * 创建时间
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
