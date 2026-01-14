package com.adlin.orin.modules.alert.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 告警历史记录实体
 */
@Entity
@Table(name = "alert_history", indexes = {
        @Index(name = "idx_rule_id", columnList = "rule_id"),
        @Index(name = "idx_agent_id", columnList = "agent_id"),
        @Index(name = "idx_triggered_at", columnList = "triggered_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 关联的规则 ID
     */
    @Column(name = "rule_id", length = 50)
    private String ruleId;

    /**
     * 关联的智能体 ID
     */
    @Column(name = "agent_id", length = 50)
    private String agentId;

    /**
     * 告警消息
     */
    @Column(name = "alert_message", columnDefinition = "TEXT")
    private String alertMessage;

    /**
     * 严重程度
     */
    @Column(name = "severity", length = 20)
    private String severity;

    /**
     * 触发时间
     */
    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    /**
     * 解决时间
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * 状态: TRIGGERED, RESOLVED
     */
    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "TRIGGERED";

    @PrePersist
    protected void onCreate() {
        if (triggeredAt == null) {
            triggeredAt = LocalDateTime.now();
        }
    }
}
