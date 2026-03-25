package com.adlin.orin.modules.collaboration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 协作任务包 - 多智能体协作的核心数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationPackage {

    /**
     * 任务包唯一标识
     */
    private String packageId;

    /**
     * 根任务 ID
     */
    private Long rootTaskId;

    /**
     * 用户原始意图描述
     */
    private String intent;

    /**
     * 意图标签：分类、优先级、复杂度
     */
    private IntentTag intentTag;

    /**
     * 任务分解结果
     */
    private List<TaskDecomposition> subTasks;

    /**
     * 当前执行的子任务索引
     */
    private Integer currentSubTaskIndex;

    /**
     * 协作模式：SEQUENTIAL, PARALLEL, CONSENSUS, HIERARCHICAL
     */
    private String collaborationMode;

    /**
     * 共享上下文（黑板）
     */
    private Map<String, Object> sharedContext;

    /**
     * 参与角色
     */
    private List<ParticipantRole> participants;

    /**
     * 执行策略
     */
    private ExecutionStrategy strategy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 超时时间
     */
    private LocalDateTime timeoutAt;

    /**
     * 状态：PLANNING, DECOMPOSING, EXECUTING, CONSENSUS, COMPLETED, FAILED, FALLBACK
     */
    private String status;

    /**
     * 最终结果
     */
    private String result;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 追踪 ID
     */
    private String traceId;

    /**
     * 意图标签
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntentTag {
        /**
         * 任务分类：ANALYSIS, GENERATION, REVIEW, RESEARCH, CODING, TESTING
         */
        private String category;

        /**
         * 优先级：LOW, NORMAL, HIGH, URGENT
         */
        private String priority;

        /**
         * 预估复杂度：SIMPLE, MEDIUM, COMPLEX, VERY_COMPLEX
         */
        private String complexity;

        /**
         * 是否需要审查
         */
        private Boolean needReview;

        /**
         * 是否需要共识
         */
        private Boolean needConsensus;
    }

    /**
     * 任务分解结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskDecomposition {
        /**
         * 子任务唯一标识
         */
        private String subTaskId;

        /**
         * 子任务描述
         */
        private String description;

        /**
         * 期望角色：PLANNER, SPECIALIST, REVIEWER, CRITIC, COORDINATOR
         */
        private String expectedRole;

        /**
         * 依赖的子任务 ID（前置条件）
         */
        private List<String> dependsOn;

        /**
         * 输入数据
         */
        private Map<String, Object> input;

        /**
         * 输出数据
         */
        private Map<String, Object> output;

        /**
         * 置信度：0.0-1.0
         */
        private Double confidence;

        /**
         * 状态：PENDING, ASSIGNED, RUNNING, COMPLETED, FAILED, SKIPPED
         */
        private String status;

        /**
         * 执行结果
         */
        private String result;

        /**
         * 开始时间
         */
        private LocalDateTime startedAt;

        /**
         * 完成时间
         */
        private LocalDateTime completedAt;

        /**
         * 执行 Agent ID
         */
        private String executedBy;

        /**
         * 重试次数
         */
        private Integer retryCount;

        /**
         * 错误信息
         */
        private String errorMessage;
    }

    /**
     * 参与角色
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantRole {
        /**
         * 角色类型：PLANNER, SPECIALIST, REVIEWER, CRITIC, COORDINATOR, MEMORY_KEEPER
         */
        private String roleType;

        /**
         * Agent ID
         */
        private String agentId;

        /**
         * Agent 名称
         */
        private String agentName;

        /**
         * 能力画像：擅长的任务类型
         */
        private List<String> capabilities;

        /**
         * 成本等级：LOW, MEDIUM, HIGH
         */
        private String costLevel;

        /**
         * 状态：IDLE, ASSIGNED, RUNNING, COMPLETED
         */
        private String status;
    }

    /**
     * 执行策略
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionStrategy {
        /**
         * 最大并行数
         */
        private Integer maxParallel;

        /**
         * 超时时间（秒）
         */
        private Integer timeoutSeconds;

        /**
         * 最大重试次数
         */
        private Integer maxRetries;

        /**
         * 重试策略：IMMEDIATE, EXPONENTIAL, LINEAR
         */
        private String retryStrategy;

        /**
         * 回退策略：ROLLBACK, SKIP, MANUAL, ESCALATE
         */
        private String fallbackStrategy;

        /**
         * 共识策略：MAJORITY, UNANIMOUS, WEIGHTED, HUMAN
         */
        private String consensusStrategy;

        /**
         * 是否启用记忆共享
         */
        private Boolean enableMemorySharing;

        /**
         * 是否启用事件驱动
         */
        private Boolean enableEventDriven;
    }
}