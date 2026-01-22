package com.adlin.orin.modules.trace.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 工作流追踪实体
 */
@Entity
@Table(name = "workflow_traces")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTraceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "step_id")
    private Long stepId;

    @Column(name = "step_name", length = 100)
    private String stepName;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "skill_name", length = 100)
    private String skillName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TraceStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_data", columnDefinition = "JSON")
    private Map<String, Object> inputData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_data", columnDefinition = "JSON")
    private Map<String, Object> outputData;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_details", columnDefinition = "JSON")
    private Map<String, Object> errorDetails;

    @Column(name = "cpu_usage", precision = 5, scale = 2)
    private BigDecimal cpuUsage;

    @Column(name = "memory_usage")
    private Long memoryUsage;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }

    /**
     * 追踪状态枚举
     */
    public enum TraceStatus {
        PENDING, // 待执行
        RUNNING, // 执行中
        SUCCESS, // 成功
        FAILED, // 失败
        SKIPPED // 跳过
    }
}
