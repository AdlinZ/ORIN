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
     * 消息范围: USER (用户消息) 或 BROADCAST (系统广播)
     */
    @Builder.Default
    private String scope = "USER";

    /**
     * 聚合去重键，用于同一告警实例更新同一条通知
     */
    @Column(name = "dedupe_key", length = 160)
    private String dedupeKey;

    /**
     * 告警指纹，便于前端按告警实例展示
     */
    @Column(name = "fingerprint", length = 160)
    private String fingerprint;

    /**
     * 消息来源: SYSTEM, ALERT 等
     */
    @Column(name = "source_type", length = 40)
    private String sourceType;

    /**
     * 聚合状态: TRIGGERED, RESOLVED
     */
    @Column(name = "status", length = 20)
    private String status;

    /**
     * 同一聚合键重复发生次数
     */
    @Builder.Default
    @Column(name = "repeat_count")
    private Integer repeatCount = 1;

    /**
     * 最近发生时间
     */
    private LocalDateTime lastOccurredAt;

    /**
     * 恢复时间
     */
    private LocalDateTime resolvedAt;

    /**
     * 聚合摘要
     */
    @Column(columnDefinition = "TEXT")
    private String summary;

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
    @Column(name = "`read`")
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
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.lastOccurredAt == null) {
            this.lastOccurredAt = this.createdAt;
        }
        if (this.repeatCount == null || this.repeatCount < 1) {
            this.repeatCount = 1;
        }
    }
}
