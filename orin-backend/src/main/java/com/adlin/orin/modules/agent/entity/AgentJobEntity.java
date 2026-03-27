package com.adlin.orin.modules.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 智能体异步任务实体
 * 用于追踪批量导出、批量导入、刷新元数据等异步操作的状态
 */
@Entity
@Table(name = "agent_job")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, unique = true, length = 64)
    private String jobId;

    @Column(name = "agent_id", length = 64)
    private String agentId;

    @Column(name = "job_type", nullable = false, length = 32)
    private String jobType; // EXPORT, IMPORT, REFRESH_METADATA

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "progress")
    private Integer progress; // 0-100

    @Column(name = "result_data", columnDefinition = "TEXT")
    private String resultData; // JSON result or error message

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (progress == null) {
            progress = 0;
        }
    }

    /**
     * 任务状态枚举
     */
    public enum JobStatus {
        PENDING("等待中"),
        RUNNING("执行中"),
        SUCCESS("成功"),
        FAILED("失败");

        private final String description;

        JobStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}