package com.adlin.orin.modules.alert.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 告警规则实体
 */
@Entity
@Table(name = "alert_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 规则名称
     */
    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    /**
     * 规则类型: HEALTH_CHECK, PERFORMANCE, ERROR_RATE
     */
    @Column(name = "rule_type", length = 50)
    private String ruleType;

    /**
     * 条件表达式
     */
    @Column(name = "condition_expr", length = 500)
    private String conditionExpr;

    /**
     * 阈值
     */
    @Column(name = "threshold_value")
    private Double thresholdValue;

    /**
     * 严重程度: INFO, WARNING, ERROR, CRITICAL
     */
    @Column(name = "severity", length = 20)
    private String severity;

    /**
     * 是否启用
     */
    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 通知渠道: EMAIL,DINGTALK,WECHAT
     */
    @Column(name = "notification_channels")
    private String notificationChannels;

    /**
     * 接收人列表
     */
    @Column(name = "recipient_list", columnDefinition = "TEXT")
    private String recipientList;

    /**
     * 冷却时间（分钟）
     */
    @Column(name = "cooldown_minutes")
    @Builder.Default
    private Integer cooldownMinutes = 5;

    /**
     * 创建时间
     */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
