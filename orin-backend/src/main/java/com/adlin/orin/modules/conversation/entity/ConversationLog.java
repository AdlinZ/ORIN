package com.adlin.orin.modules.conversation.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 智能体对话日志 - 独立于系统审计日志
 */
@Entity
@Table(name = "conversation_logs", indexes = {
        @Index(name = "idx_conv_id", columnList = "conversationId"),
        @Index(name = "idx_agent_id", columnList = "agentId"),
        @Index(name = "idx_user_id", columnList = "userId"),
        @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "conversation_id", length = 100)
    private String conversationId;

    @Column(name = "agent_id", length = 100)
    private String agentId;

    @Column(name = "user_id")
    private String userId;

    @Column(length = 100)
    private String model;

    @Column(columnDefinition = "TEXT")
    private String query;

    @Column(columnDefinition = "TEXT")
    private String response;

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    private Long responseTime;

    private Boolean success;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
