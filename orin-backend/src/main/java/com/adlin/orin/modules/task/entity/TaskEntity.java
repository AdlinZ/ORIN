package com.adlin.orin.modules.task.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务实体 - 用于异步队列执行
 */
@Entity
@Table(name = "task_info")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, unique = true, length = 64)
    private String taskId;

    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;

    @Column(name = "workflow_instance_id")
    private Long workflowInstanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_data", columnDefinition = "JSON")
    private Map<String, Object> inputData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_data", columnDefinition = "JSON")
    private Map<String, Object> outputData;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "trigger_source", length = 50)
    private String triggerSource;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_stack", columnDefinition = "TEXT")
    private String errorStack;

    @Column(name = "dead_letter_reason", columnDefinition = "TEXT")
    private String deadLetterReason;

    @Column(name = "queued_at")
    private LocalDateTime queuedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (queuedAt == null) {
            queuedAt = LocalDateTime.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 任务优先级枚举
     */
    public enum TaskPriority {
        HIGH(10, "高优"),
        NORMAL(5, "普通"),
        LOW(1, "低优");

        private final int value;
        private final String description;

        TaskPriority(int value, String description) {
            this.value = value;
            this.description = description;
        }

        public int getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        public static TaskPriority fromString(String priority) {
            if (priority == null || priority.isBlank()) {
                return NORMAL;
            }
            try {
                return valueOf(priority.toUpperCase());
            } catch (IllegalArgumentException e) {
                return NORMAL;
            }
        }
    }

    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        QUEUED("排队中"),
        RUNNING("执行中"),
        RETRYING("重试中"),
        COMPLETED("已完成"),
        FAILED("失败"),
        DEAD("死信");

        private final String description;

        TaskStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
