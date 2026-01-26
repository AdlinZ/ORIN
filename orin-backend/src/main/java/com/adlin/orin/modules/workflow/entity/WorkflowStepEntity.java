package com.adlin.orin.modules.workflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作流步骤实体
 */
@Entity
@Table(name = "workflow_steps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "step_name", nullable = false, length = 100)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", length = 20, nullable = false)
    @Builder.Default
    private StepType stepType = StepType.SKILL;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "agent_id")
    private Long agentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_mapping", columnDefinition = "JSON")
    private Map<String, Object> inputMapping;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_mapping", columnDefinition = "JSON")
    private Map<String, Object> outputMapping;

    @Column(name = "condition_expression", length = 500)
    private String conditionExpression;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "depends_on", columnDefinition = "JSON")
    private List<Long> dependsOn;

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
     * 步骤类型枚举
     */
    public enum StepType {
        SKILL, // 技能调用
        AGENT, // 智能体调用
        LOGIC, // 逻辑控制

        // New Types
        LLM, // 大模型调用
        KNOWLEDGE_RETRIEVAL, // 知识检索
        ANSWER, // 直接回复
        QUESTION_CLASSIFIER, // 问题分类器
        IF_ELSE, // 条件分支
        ITERATION, // 迭代
        LOOP, // 循环
        CODE, // 代码执行
        TEMPLATE_TRANSFORM, // 模板转换
        VARIABLE_AGGREGATOR, // 变量聚合器
        DOCUMENT_EXTRACTOR, // 文档提取器
        PARAMETER_EXTRACTOR, // 参数提取器
        HTTP_REQUEST, // HTTP 请求
        TOOL, // 工具调用
        NOTE, // 注释/笔记
        END // 结束节点
    }
}
