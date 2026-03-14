package com.adlin.orin.modules.system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 邮件服务配置实体
 */
@Entity
@Table(name = "sys_mail_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailConfigEntity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "smtp_host", length = 100)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "password", length = 200)
    private String password;

    @Column(name = "from_email", length = 100)
    private String fromEmail;

    @Column(name = "from_name", length = 100)
    private String fromName;

    @Column(name = "api_key", length = 500)
    private String apiKey;

    @Column(name = "mailer_type", length = 20)
    @Builder.Default
    private String mailerType = "mailersend"; // smtp, mailersend 或 resend

    @Column(name = "ssl_enabled")
    @Builder.Default
    private Boolean sslEnabled = true;

    // IMAP 配置
    @Column(name = "imap_host", length = 100)
    private String imapHost;

    @Column(name = "imap_port")
    @Builder.Default
    private Integer imapPort = 993;

    @Column(name = "imap_username", length = 100)
    private String imapUsername;

    @Column(name = "imap_password", length = 200)
    private String imapPassword;

    @Column(name = "imap_enabled")
    @Builder.Default
    private Boolean imapEnabled = false;

    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = false;

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