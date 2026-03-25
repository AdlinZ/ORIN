package com.adlin.orin.modules.collaboration.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 协作事件 - 多智能体协作过程中的各类事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationEvent {

    /**
     * 事件 ID
     */
    private String eventId;

    /**
     * 任务包 ID
     */
    private String packageId;

    /**
     * 事件类型
     */
    private CollaborationEventType eventType;

    /**
     * 子任务 ID
     */
    private String subTaskId;

    /**
     * Agent ID
     */
    private String agentId;

    /**
     * 角色类型
     */
    private String roleType;

    /**
     * 事件数据
     */
    private Map<String, Object> eventData;

    /**
     * 追踪 ID
     */
    private String traceId;

    /**
     * 事件时间
     */
    private LocalDateTime timestamp;

    /**
     * 事件来源
     */
    private String source;

    /**
     * 是否已处理
     */
    private boolean processed;

    /**
     * 事件类型枚举
     */
    public enum CollaborationEventType {
        // 任务生命周期
        PACKAGE_CREATED("任务包创建"),
        PACKAGE_DECOMPOSED("任务分解完成"),
        PACKAGE_STARTED("任务包开始执行"),
        PACKAGE_COMPLETED("任务包完成"),
        PACKAGE_FAILED("任务包失败"),
        PACKAGE_FALLBACK("任务包回退"),

        // 子任务生命周期
        SUBTASK_CREATED("子任务创建"),
        SUBTASK_ASSIGNED("子任务分配"),
        SUBTASK_STARTED("子任务开始"),
        SUBTASK_COMPLETED("子任务完成"),
        SUBTASK_FAILED("子任务失败"),
        SUBTASK_RETRY("子任务重试"),
        SUBTASK_SKIPPED("子任务跳过"),

        // Agent 生命周期
        AGENT_ASSIGNED("Agent 分配"),
        AGENT_STARTED("Agent 开始执行"),
        AGENT_COMPLETED("Agent 完成"),
        AGENT_FAILED("Agent 失败"),

        // 协作事件
        CONSENSUS_REACHED("达成共识"),
        CONSENSUS_FAILED("共识失败"),
        CRITIC_REVIEW("审查反馈"),
        MEMORY_UPDATED("记忆更新"),
        EVENT_EMITTED("事件发射"),
        EVENT_TRIGGERED("事件触发"),

        // 系统事件
        TIMEOUT("超时"),
        FALLBACK_TRIGGERED("回退触发"),
        CHECKPOINT_SAVED("检查点保存"),
        CHECKPOINT_RESTORED("检查点恢复");

        private final String description;

        CollaborationEventType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}