package com.adlin.orin.modules.collaboration.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 多智能体协作任务实体
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "collab_task")
public class CollaborationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 任务描述
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 任务状态: PENDING, RUNNING, COMPLETED, FAILED
     */
    @Builder.Default
    private String status = "PENDING";

    /**
     * 任务类型: SEQUENTIAL, PARALLEL, ROUND_ROBIN
     */
    @Builder.Default
    private String taskType = "SEQUENTIAL";

    /**
     * 参与的 Agent ID 列表
     */
    @ElementCollection
    @CollectionTable(name = "collab_task_agents", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "agent_id")
    private List<String> agentIds;

    /**
     * 当前执行的 Agent 索引
     */
    @Builder.Default
    private Integer currentAgentIndex = 0;

    /**
     * 任务结果
     */
    @Column(columnDefinition = "TEXT")
    private String result;

    /**
     * 错误信息
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 创建者 ID
     */
    private String createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

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
