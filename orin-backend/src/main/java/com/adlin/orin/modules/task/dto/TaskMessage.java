package com.adlin.orin.modules.task.dto;

import com.adlin.orin.modules.task.entity.TaskEntity.TaskPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 任务消息DTO - 用于RabbitMQ消息传递
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务唯一ID
     */
    private String taskId;

    /**
     * 工作流ID
     */
    private Long workflowId;

    /**
     * 工作流实例ID
     */
    private Long workflowInstanceId;

    /**
     * 任务优先级
     */
    private TaskPriority priority;

    /**
     * 输入数据
     */
    private Map<String, Object> inputData;

    /**
     * 触发者
     */
    private String triggeredBy;

    /**
     * 触发来源
     */
    private String triggerSource;

    /**
     * 当前重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetries;

    /**
     * 错误消息（重试时使用）
     */
    private String errorMessage;

    /**
     * 是否为重放任务
     */
    private boolean replay;

    /**
     * 原始任务ID（重放时使用）
     */
    private String originalTaskId;
}
