package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.service.AgentManageService;
import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.event.CollaborationEventBus;
import com.adlin.orin.modules.observability.service.LangfuseObservabilityService;
import com.adlin.orin.modules.workflow.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 协作任务执行器 - 负责执行子任务，调用智能体或工作流
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationExecutor {

    private final AgentManageService agentManageService;
    private final CollaborationEventBus eventBus;
    private final ObjectMapper objectMapper;
    private final LangfuseObservabilityService langfuseService;
    private final WorkflowService workflowService;

    /**
     * 执行子任务
     * 根据子任务的类型，调用对应的执行器
     */
    public CompletableFuture<String> executeSubtask(CollabSubtaskEntity subtask, String packageId, String traceId) {
        String subTaskId = subtask.getSubTaskId();
        String description = subtask.getDescription();
        String expectedRole = subtask.getExpectedRole();

        log.info("Executing subtask: {} for package: {} with role: {}", subTaskId, packageId, expectedRole);

        // 根据角色类型确定调用方式
        return switch (expectedRole != null ? expectedRole : "SPECIALIST") {
            case "PLANNER", "SPECIALIST", "REVIEWER", "CRITIC", "COORDINATOR" ->
                executeWithAgent(subTaskId, description, packageId, traceId);
            default ->
                executeWithAgent(subTaskId, description, packageId, traceId);
        };
    }

    /**
     * 使用 Agent 执行任务 - 真实调用
     */
    private CompletableFuture<String> executeWithAgent(String subTaskId, String description,
                                                        String packageId, String traceId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        long startTime = System.currentTimeMillis();

        // 记录子任务开始到 Langfuse
        recordCollabEvent(traceId, subTaskId, "SUBTASK_STARTED", Map.of(
                "packageId", packageId != null ? packageId : "",
                "description", description != null ? description : ""
        ));

        try {
            // 发布子任务开始事件
            eventBus.publishSubtaskAssigned(packageId, subTaskId, null, "AGENT", traceId);

            // 获取可用的 Agent 列表
            List<AgentMetadata> agents = agentManageService.getAllAgents();

            if (agents == null || agents.isEmpty()) {
                log.warn("No available agents, failing subtask: {}", subTaskId);
                String errorResult = "No available agents to execute subtask: " + description;
                future.complete(errorResult);
                eventBus.publishSubtaskFailed(packageId, subTaskId, null,
                        "No available agents", traceId);
                recordCollabEvent(traceId, subTaskId, "SUBTASK_FAILED", Map.of(
                        "packageId", packageId != null ? packageId : "",
                        "error", "No available agents"
                ));
                return future;
            }

            // 选择第一个可用的 Agent（实际场景中应根据 capability 匹配）
            AgentMetadata selectedAgent = agents.get(0);
            String agentId = selectedAgent.getAgentId();
            log.info("Using agent: {} for subtask: {}", agentId, subTaskId);

            // 调用 Agent 执行任务
            Optional<Object> response = agentManageService.chat(agentId, description, (String) null);

            if (response.isPresent()) {
                String result = convertResponseToString(response.get());
                log.info("Agent execution completed for subtask: {}, result: {}", subTaskId,
                        result.length() > 100 ? result.substring(0, 100) + "..." : result);

                // 记录完成到 Langfuse
                long durationMs = System.currentTimeMillis() - startTime;
                recordCollabEvent(traceId, subTaskId, "SUBTASK_COMPLETED", Map.of(
                        "packageId", packageId != null ? packageId : "",
                        "agentId", agentId != null ? agentId : "",
                        "durationMs", durationMs
                ));

                // 发布子任务完成事件
                eventBus.publishSubtaskCompleted(packageId, subTaskId, agentId,
                        Map.of("result", result, "agentId", agentId), traceId);

                future.complete(result);
            } else {
                String errorResult = "Agent execution returned empty response";
                log.warn(errorResult);
                future.complete(errorResult);
                eventBus.publishSubtaskCompleted(packageId, subTaskId, agentId,
                        Map.of("result", errorResult), traceId);
            }

        } catch (Exception e) {
            log.error("Failed to execute subtask with agent: {}", subTaskId, e);
            future.completeExceptionally(e);

            // 发布子任务失败事件
            eventBus.publishSubtaskFailed(packageId, subTaskId, null, e.getMessage(), traceId);
        }

        return future;
    }

    /**
     * 将 Agent 响应转换为字符串
     */
    private String convertResponseToString(Object response) {
        if (response == null) {
            return "";
        }
        if (response instanceof String) {
            return (String) response;
        }
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return response.toString();
        }
    }

    /**
     * 记录协作子任务事件到 Langfuse
     */
    private void recordCollabEvent(String traceId, String subTaskId, String eventName, Map<String, Object> metadata) {
        if (traceId == null || !langfuseService.isEnabled()) {
            return;
        }

        try {
            // 更新 metadata 添加 subTaskId
            Map<String, Object> eventMetadata = new java.util.HashMap<>(metadata);
            eventMetadata.put("subTaskId", subTaskId);

            langfuseService.recordEvent(traceId, eventName, eventMetadata);
            log.debug("Recorded collaboration event to Langfuse: traceId={}, event={}", traceId, eventName);

        } catch (Exception e) {
            // Langfuse 错误降级，不影响主流程
            log.warn("Failed to record Langfuse collaboration event: {}", e.getMessage());
        }
    }

    /**
     * 使用指定 Agent 执行任务
     */
    public CompletableFuture<String> executeWithSpecificAgent(String subTaskId, String agentId,
                                                                String message, String packageId, String traceId) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            eventBus.publishSubtaskAssigned(packageId, subTaskId, agentId, "AGENT", traceId);

            Optional<Object> response = agentManageService.chat(agentId, message, (String) null);

            if (response.isPresent()) {
                String result = convertResponseToString(response.get());
                eventBus.publishSubtaskCompleted(packageId, subTaskId, agentId,
                        Map.of("result", result), traceId);
                future.complete(result);
            } else {
                future.complete("Agent response is empty");
            }

        } catch (Exception e) {
            log.error("Failed to execute with agent: {}", agentId, e);
            eventBus.publishSubtaskFailed(packageId, subTaskId, agentId, e.getMessage(), traceId);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * 使用工作流执行任务
     * TODO: 集成工作流执行服务
     */
    private CompletableFuture<String> executeWithWorkflow(String subTaskId, Long workflowId,
                                                          String packageId, String traceId) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            eventBus.publishSubtaskAssigned(packageId, subTaskId, null, "WORKFLOW", traceId);

            // TODO: 调用工作流执行服务
            // 使用 WorkflowService.triggerWorkflow 执行工作流
            if (workflowId != null) {
                try {
                    Map<String, Object> inputs = Map.of("subTaskId", subTaskId, "packageId", packageId);
                    Long instanceId = workflowService.triggerWorkflow(workflowId, inputs, "collaboration");
                    future.complete("Workflow triggered, instanceId: " + instanceId);
                } catch (Exception e) {
                    log.error("Failed to trigger workflow: {}", workflowId, e);
                    future.completeExceptionally(e);
                }
            }

            eventBus.publishSubtaskCompleted(packageId, subTaskId, null,
                    Map.of("result", "Workflow completed"), traceId);

        } catch (Exception e) {
            log.error("Failed to execute workflow subtask: {}", subTaskId, e);
            future.completeExceptionally(e);
            eventBus.publishSubtaskFailed(packageId, subTaskId, null, e.getMessage(), traceId);
        }

        return future;
    }

    /**
     * 人工任务占位
     */
    private CompletableFuture<String> executeHumanTask(String subTaskId, String description,
                                                        String packageId, String traceId) {
        // 发布任务分配给人工的事件
        eventBus.publishSubtaskAssigned(packageId, subTaskId, "HUMAN", "HUMAN", traceId);

        // 返回一个等待人工完成的 future
        CompletableFuture<String> future = new CompletableFuture<>();
        // 人工任务需要手动标记完成
        return future;
    }

    /**
     * 重试子任务
     */
    public CompletableFuture<String> retrySubtask(CollabSubtaskEntity subtask, String packageId, String traceId) {
        log.info("Retrying subtask: {} for package: {}", subtask.getSubTaskId(), packageId);
        // 重置重试计数
        subtask.setRetryCount(subtask.getRetryCount() + 1);
        return executeSubtask(subtask, packageId, traceId);
    }

    /**
     * 获取可用的 Agent 列表
     */
    public List<AgentMetadata> getAvailableAgents() {
        return agentManageService.getAllAgents();
    }
}