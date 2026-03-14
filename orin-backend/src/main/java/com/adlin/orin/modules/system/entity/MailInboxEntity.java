package com.adlin.orin.modules.system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 邮件收件箱实体
 */
@Entity
@Table(name = "sys_mail_inbox")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailInboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", length = 200, unique = true)
    private String messageId;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(name = "from_email", length = 200)
    private String fromEmail;

    @Column(name = "from_name", length = 200)
    private String fromName;

    @Column(name = "to_email", length = 200)
    private String toEmail;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_html", columnDefinition = "TEXT")
    private String contentHtml;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "is_read")
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "is_starred")
    @Builder.Default
    private Boolean isStarred = false;

    @Column(length = 50)
    @Builder.Default
    private String folder = "INBOX";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }
}
