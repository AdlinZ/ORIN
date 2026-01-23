package com.adlin.orin.modules.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工作流执行事件
 * 用于 WebSocket 实时推送
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowExecutionEvent {

    /**
     * 工作流实例 ID
     */
    private Long instanceId;

    /**
     * 节点 ID
     */
    private String nodeId;

    /**
     * 事件类型
     */
    private EventType eventType;

    /**
     * 节点状态
     */
    private NodeStatus status;

    /**
     * 附加数据
     */
    private Map<String, Object> data;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 消息
     */
    private String message;

    public enum EventType {
        WORKFLOW_STARTED,
        NODE_STARTED,
        NODE_COMPLETED,
        NODE_FAILED,
        WORKFLOW_COMPLETED,
        WORKFLOW_FAILED
    }

    public enum NodeStatus {
        IDLE,
        RUNNING,
        SUCCESS,
        ERROR
    }
}
