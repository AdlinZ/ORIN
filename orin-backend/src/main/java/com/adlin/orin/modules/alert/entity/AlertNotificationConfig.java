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
