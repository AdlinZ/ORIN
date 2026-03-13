package com.adlin.orin.modules.system.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 邮件发送日志实体
 */
@Data
@Entity
@Table(name = "sys_mail_send_log")
public class MailSendLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 1000)
    private String recipients;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 20)
    private String status = "PENDING";

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "mailer_type", length = 20)
    private String mailerType;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * 发送成功状态
     */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /**
     * 发送失败状态
     */
    public static final String STATUS_FAILED = "FAILED";
    /**
     * 待发送状态
     */
    public static final String STATUS_PENDING = "PENDING";
}
