package com.adlin.orin.modules.notification.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统消息实体
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "system_message")
public class SystemMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 消息标题
     */
    private String title;

    /**
     * 消息内容
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * 消息类型: INFO, WARNING, ERROR, SUCCESS, SYSTEM
     */
    @Builder.Default
    private String type = "INFO";

    /**
     * 接收者 ID（为空表示广播给所有人）
     */
    private String receiverId;

    /**
     * 发送者 ID
     */
    private String senderId;

    /**
     * 是否已读
     */
    @Builder.Default
    private Boolean read = false;

    /**
     * 过期时间（为空表示永不过期）
     */
    private LocalDateTime expireAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
