package com.adlin.orin.modules.task.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务队列实体
 * 支持优先级管理
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "task_queue")
public class TaskQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 任务内容
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * 优先级: HIGH(10), MEDIUM(5), LOW(1)
     */
    @Builder.Default
    private Integer priority = 5;

    /**
     * 任务状态: PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
     */
    @Builder.Default
    private String status = "PENDING";

    /**
     * 执行结果
     */
    @Column(columnDefinition = "TEXT")
    private String result;

    /**
     * 错误信息
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 重试次数
     */
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 最大重试次数
     */
    @Builder.Default
    private Integer maxRetry = 3;

    /**
     * 执行开始时间
     */
    private LocalDateTime startTime;

    /**
     * 执行结束时间
     */
    private LocalDateTime endTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 创建者 ID
     */
    private String createdBy;

    /**
     * 执行节点 ID
     */
    private String executorNode;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
