package com.adlin.orin.modules.alert.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 告警通知配置实体
 */
@Data
@Entity
@Table(name = "alert_notification_config")
public class AlertNotificationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==================== 通知渠道 ====================
    @Column(name = "email_enabled")
    private Boolean emailEnabled = true;

    @Column(name = "email_recipients", length = 500)
    private String emailRecipients;

    @Column(name = "dingtalk_enabled")
    private Boolean dingtalkEnabled = false;

    @Column(name = "dingtalk_webhook", length = 500)
    private String dingtalkWebhook;

    @Column(name = "wecom_enabled")
    private Boolean wecomEnabled = false;

    @Column(name = "wecom_webhook", length = 500)
    private String wecomWebhook;

    // ==================== 通知偏好设置 ====================
    /**
     * 仅接收关键告警（CRITICAL/ERROR）
     */
    @Column(name = "critical_only")
    private Boolean criticalOnly = false;

    /**
     * 失败立即推送（不合并）
     */
    @Column(name = "instant_push")
    private Boolean instantPush = true;

    /**
     * 低优先级合并推送间隔（分钟），0表示不合并
     */
    @Column(name = "merge_interval_minutes")
    private Integer mergeIntervalMinutes = 0;

    /**
     * 启用桌面通知
     */
    @Column(name = "desktop_notification")
    private Boolean desktopNotification = true;

    /**
     * 启用邮件通知
     */
    @Column(name = "notify_email")
    private Boolean notifyEmail = true;

    /**
     * 启用站内通知
     */
    @Column(name = "notify_inapp")
    private Boolean notifyInapp = true;

    // ==================== 时间戳 ====================
    @Column(name = "created_at")
    private LocalDateTime createdAt;

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
