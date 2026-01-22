package com.adlin.orin.modules.workflow.entity;

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
 * 工作流定义实体
 */
@Entity
@Table(name = "workflows")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_name", nullable = false, length = 100)
    private String workflowName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_type", length = 50)
    @Builder.Default
    private WorkflowType workflowType = WorkflowType.SEQUENTIAL;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workflow_definition", nullable = false, columnDefinition = "JSON")
    private Map<String, Object> workflowDefinition;

    @Column(name = "timeout_seconds")
    @Builder.Default
    private Integer timeoutSeconds = 300;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retry_policy", columnDefinition = "JSON")
    private Map<String, Object> retryPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private WorkflowStatus status = WorkflowStatus.DRAFT;

    @Column(name = "version", length = 20)
    @Builder.Default
    private String version = "1.0.0";

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
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

    /**
     * 工作流类型枚举
     */
    public enum WorkflowType {
        SEQUENTIAL, // 顺序执行
        PARALLEL, // 并行执行
        DAG // 有向无环图
    }

    /**
     * 工作流状态枚举
     */
    public enum WorkflowStatus {
        DRAFT, // 草稿
        ACTIVE, // 活跃
        ARCHIVED // 已归档
    }
}
