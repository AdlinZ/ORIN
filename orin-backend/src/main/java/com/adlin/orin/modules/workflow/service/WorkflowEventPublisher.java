package com.adlin.orin.modules.workflow.service;

import com.adlin.orin.modules.workflow.dto.WorkflowExecutionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 工作流事件发布服务
 * 通过 WebSocket 向客户端推送实时执行事件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 发布工作流开始事件
     */
    public void publishWorkflowStarted(Long instanceId) {
        WorkflowExecutionEvent event = WorkflowExecutionEvent.builder()
                .instanceId(instanceId)
                .eventType(WorkflowExecutionEvent.EventType.WORKFLOW_STARTED)
                .timestamp(System.currentTimeMillis())
                .message("工作流开始执行")
                .build();

        sendEvent(instanceId, event);
    }

    /**
     * 发布节点开始事件
     */
    public void publishNodeStarted(Long instanceId, String nodeId, String nodeName) {
        WorkflowExecutionEvent event = WorkflowExecutionEvent.builder()
                .instanceId(instanceId)
                .nodeId(nodeId)
                .eventType(WorkflowExecutionEvent.EventType.NODE_STARTED)
                .status(WorkflowExecutionEvent.NodeStatus.RUNNING)
                .timestamp(System.currentTimeMillis())
                .message("节点开始执行: " + nodeName)
                .build();

        sendEvent(instanceId, event);
    }

    /**
     * 发布节点完成事件
     */
    public void publishNodeCompleted(Long instanceId, String nodeId, String nodeName, Map<String, Object> result) {
        WorkflowExecutionEvent event = WorkflowExecutionEvent.builder()
                .instanceId(instanceId)
                .nodeId(nodeId)
                .eventType(WorkflowExecutionEvent.EventType.NODE_COMPLETED)
                .status(WorkflowExecutionEvent.NodeStatus.SUCCESS)
                .data(result)
                .timestamp(System.currentTimeMillis())
                .message("节点执行成功: " + nodeName)
                .build();

        sendEvent(instanceId, event);
    }

    /**
     * 发布节点失败事件
     */
    public void publishNodeFailed(Long instanceId, String nodeId, String nodeName, String error) {
        WorkflowExecutionEvent event = WorkflowExecutionEvent.builder()
                .instanceId(instanceId)
                .nodeId(nodeId)
                .eventType(WorkflowExecutionEvent.EventType.NODE_FAILED)
                .status(WorkflowExecutionEvent.NodeStatus.ERROR)
                .timestamp(System.currentTimeMillis())
                .message("节点执行失败: " + nodeName + " - " + error)
                .build();

        sendEvent(instanceId, event);
    }

    /**
     * 发布工作流完成事件
     */
    public void publishWorkflowCompleted(Long instanceId) {
        WorkflowExecutionEvent event = WorkflowExecutionEvent.builder()
                .instanceId(instanceId)
                .eventType(WorkflowExecutionEvent.EventType.WORKFLOW_COMPLETED)
                .timestamp(System.currentTimeMillis())
                .message("工作流执行完成")
                .build();

        sendEvent(instanceId, event);
    }

    /**
     * 发布工作流失败事件
     */
    public void publishWorkflowFailed(Long instanceId, String error) {
        WorkflowExecutionEvent event = WorkflowExecutionEvent.builder()
                .instanceId(instanceId)
                .eventType(WorkflowExecutionEvent.EventType.WORKFLOW_FAILED)
                .timestamp(System.currentTimeMillis())
                .message("工作流执行失败: " + error)
                .build();

        sendEvent(instanceId, event);
    }

    /**
     * 发送事件到指定主题
     */
    private void sendEvent(Long instanceId, WorkflowExecutionEvent event) {
        String destination = "/topic/workflow/" + instanceId;
        log.debug("Sending event to {}: {}", destination, event);
        messagingTemplate.convertAndSend(destination, event);
    }
}
