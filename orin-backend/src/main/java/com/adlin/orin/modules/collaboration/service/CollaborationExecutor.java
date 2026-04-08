package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.service.AgentManageService;
import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.event.CollaborationEventBus;
import com.adlin.orin.modules.collaboration.metrics.CollaborationMetricsService;
import com.adlin.orin.modules.observability.service.LangfuseObservabilityService;
import com.adlin.orin.modules.workflow.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
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
    private final CollaborationMetricsService metricsService;
    private final ObjectMapper objectMapper;
    private final LangfuseObservabilityService langfuseService;
    private final WorkflowService workflowService;

    // 角色 -> 能力关键词映射（从 agent name/description 中匹配）
    private static final Map<String, List<String>> ROLE_CAPABILITY_KEYWORDS = Map.of(
            "PLANNER", List.of("plan", "planner", "规划", "strategy", "strategic"),
            "SPECIALIST", List.of("spec", "specialist", "expert", "专项", "expert"),
            "REVIEWER", List.of("review", "reviewer", "reviewer", "审核", "quality"),
            "CRITIC", List.of("critic", "critique", "crit", "批评", "scrutiny"),
            "COORDINATOR", List.of("coord", "coordinator", "协调", "orchestrat", "manage")
    );

    // 能力 -> 权重分数映射（名字匹配权重更高）
    private static final Map<String, Integer> CAPABILITY_WEIGHTS = Map.ofEntries(
            Map.entry("planner", 10), Map.entry("plan", 8), Map.entry("strategy", 7), Map.entry("strategic", 7),
            Map.entry("specialist", 10), Map.entry("spec", 8), Map.entry("expert", 7),
            Map.entry("reviewer", 10), Map.entry("review", 8), Map.entry("quality", 6),
            Map.entry("critic", 10), Map.entry("critique", 8), Map.entry("crit", 8), Map.entry("scrutiny", 5),
            Map.entry("coordinator", 10), Map.entry("coord", 8), Map.entry("orchestrat", 7), Map.entry("manage", 5),
            Map.entry("analysis", 5), Map.entry("research", 5), Map.entry("coding", 5), Map.entry("generation", 5)
    );

    // 执行器类型常量
    public static final String EXECUTOR_TYPE_AGENT = "AGENT";
    public static final String EXECUTOR_TYPE_WORKFLOW = "WORKFLOW";
    public static final String EXECUTOR_TYPE_HUMAN = "HUMAN";

    // 待完成的人工任务 Future 注册表: packageId_subTaskId ->CompletableFuture
    private final Map<String, CompletableFuture<String>> pendingHumanTasks = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 执行子任务
     * 根据子任务的类型，调用对应的执行器
     */
    public CompletableFuture<String> executeSubtask(CollabSubtaskEntity subtask, String packageId, String traceId) {
        String subTaskId = subtask.getSubTaskId();
        String description = subtask.getDescription();
        String expectedRole = subtask.getExpectedRole();
        String executorType = determineExecutorType(expectedRole);

        log.info("Executing subtask: {} for package: {} with role: {} (type: {})", subTaskId, packageId, expectedRole, executorType);

        return switch (executorType) {
            case EXECUTOR_TYPE_WORKFLOW -> executeWithWorkflow(subTaskId, description, expectedRole, packageId, traceId, subtask);
            case EXECUTOR_TYPE_HUMAN -> executeHumanTask(subTaskId, description, packageId, traceId);
            default -> executeWithAgent(subTaskId, description, expectedRole, packageId, traceId);
        };
    }

    /**
     * 根据 expectedRole 判断执行器类型
     */
    private String determineExecutorType(String expectedRole) {
        if (expectedRole == null) {
            return EXECUTOR_TYPE_AGENT;
        }
        String upper = expectedRole.toUpperCase();
        if ("WORKFLOW".equals(upper)) {
            return EXECUTOR_TYPE_WORKFLOW;
        }
        if ("HUMAN".equals(upper)) {
            return EXECUTOR_TYPE_HUMAN;
        }
        // PLANNER, SPECIALIST, REVIEWER, CRITIC, COORDINATOR 等都是 AGENT 类型
        return EXECUTOR_TYPE_AGENT;
    }

    /**
     * 人工任务完成回调 - 由外部（如 Controller）调用以完成人工任务
     * @param packageId 任务包 ID
     * @param subTaskId 子任务 ID
     * @param humanInput 人工输入内容
     */
    public void completeHumanTask(String packageId, String subTaskId, String humanInput) {
        String key = packageId + "_" + subTaskId;
        CompletableFuture<String> future = pendingHumanTasks.remove(key);
        if (future != null) {
            log.info("Completing human task: {} in package: {}", subTaskId, packageId);

            // 记录 metrics
            metricsService.recordSubtask(packageId, subTaskId, "HUMAN", 0, "COMPLETED");

            // 发布完成事件
            eventBus.publishSubtaskCompleted(packageId, subTaskId, "HUMAN",
                    Map.of("result", humanInput, "type", "HUMAN"), null);

            future.complete(humanInput);
        } else {
            log.warn("No pending human task found for: {}", key);
        }
    }

    /**
     * 使用 Agent 执行任务 - 真实调用
     */
    private CompletableFuture<String> executeWithAgent(String subTaskId, String description, String expectedRole,
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
                metricsService.recordSubtask(packageId, subTaskId, expectedRole,
                        System.currentTimeMillis() - startTime, "FAILED");
                return future;
            }

            // 根据 expectedRole 选择最匹配的 Agent（能力路由）
            AgentMetadata selectedAgent = selectAgentByCapability(agents, expectedRole, description);
            String agentId = selectedAgent.getAgentId();
            log.info("Capability-routed agent: {} (role={}) for subtask: {}", agentId, expectedRole, subTaskId);

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

                // 记录指标
                metricsService.recordSubtask(packageId, subTaskId, expectedRole, durationMs, "COMPLETED");

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
                metricsService.recordSubtask(packageId, subTaskId, expectedRole,
                        System.currentTimeMillis() - startTime, "FAILED");
            }

        } catch (Exception e) {
            log.error("Failed to execute subtask with agent: {}", subTaskId, e);
            future.completeExceptionally(e);

            // 发布子任务失败事件
            eventBus.publishSubtaskFailed(packageId, subTaskId, null, e.getMessage(), traceId);
            metricsService.recordSubtask(packageId, subTaskId, expectedRole,
                    System.currentTimeMillis() - startTime, "FAILED");
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
     * 从 subtask.inputData 中提取 workflowId 和 inputs
     */
    private CompletableFuture<String> executeWithWorkflow(String subTaskId, String description,
                                                          String expectedRole, String packageId, String traceId,
                                                          CollabSubtaskEntity subtask) {
        CompletableFuture<String> future = new CompletableFuture<>();
        long startTime = System.currentTimeMillis();

        try {
            eventBus.publishSubtaskAssigned(packageId, subTaskId, null, "WORKFLOW", traceId);

            // 从 inputData 中提取 workflowId
            Long workflowId = extractWorkflowId(subtask);
            if (workflowId == null) {
                String errorMsg = "No workflowId found in subtask inputData for: " + subTaskId;
                log.warn(errorMsg);
                future.completeExceptionally(new IllegalArgumentException(errorMsg));
                eventBus.publishSubtaskFailed(packageId, subTaskId, null, errorMsg, traceId);
                metricsService.recordSubtask(packageId, subTaskId, expectedRole,
                        System.currentTimeMillis() - startTime, "FAILED");
                return future;
            }

            // 从 inputData 中提取 inputs
            Map<String, Object> inputs = extractWorkflowInputs(subtask, description);

            // 异步触发工作流
            CompletableFuture.runAsync(() -> {
                try {
                    Long instanceId = workflowService.triggerWorkflow(workflowId, inputs, "collab:" + packageId);

                    long durationMs = System.currentTimeMillis() - startTime;
                    String result = "Workflow triggered: instanceId=" + instanceId;

                    log.info("Workflow subtask {} triggered workflow {} with instance {}",
                            subTaskId, workflowId, instanceId);

                    metricsService.recordSubtask(packageId, subTaskId, expectedRole, durationMs, "COMPLETED");
                    eventBus.publishSubtaskCompleted(packageId, subTaskId, null,
                            Map.of("result", result, "workflowId", workflowId, "instanceId", instanceId), traceId);

                    future.complete(result);

                } catch (Exception e) {
                    long durationMs = System.currentTimeMillis() - startTime;
                    log.error("Failed to execute workflow subtask: {}", subTaskId, e);
                    metricsService.recordSubtask(packageId, subTaskId, expectedRole, durationMs, "FAILED");
                    eventBus.publishSubtaskFailed(packageId, subTaskId, null, e.getMessage(), traceId);
                    future.completeExceptionally(e);
                }
            });

        } catch (Exception e) {
            log.error("Failed to execute workflow subtask: {}", subTaskId, e);
            future.completeExceptionally(e);
            eventBus.publishSubtaskFailed(packageId, subTaskId, null, e.getMessage(), traceId);
            metricsService.recordSubtask(packageId, subTaskId, expectedRole,
                    System.currentTimeMillis() - startTime, "FAILED");
        }

        return future;
    }

    /**
     * 从 subtask.inputData 中提取 workflowId
     */
    private Long extractWorkflowId(CollabSubtaskEntity subtask) {
        try {
            String inputData = subtask.getInputData();
            if (inputData == null || inputData.isEmpty()) {
                return null;
            }
            Map<String, Object> data = objectMapper.readValue(inputData, Map.class);
            Object workflowId = data.get("workflowId");
            if (workflowId instanceof Number) {
                return ((Number) workflowId).longValue();
            }
            if (workflowId instanceof String) {
                return Long.parseLong((String) workflowId);
            }
        } catch (Exception e) {
            log.warn("Failed to extract workflowId from inputData: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从 subtask.inputData 中提取 workflow inputs
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractWorkflowInputs(CollabSubtaskEntity subtask, String defaultDescription) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("description", defaultDescription);

        try {
            String inputData = subtask.getInputData();
            if (inputData != null && !inputData.isEmpty()) {
                Map<String, Object> data = objectMapper.readValue(inputData, Map.class);
                Object inputsObj = data.get("inputs");
                if (inputsObj instanceof Map) {
                    inputs.putAll((Map<String, Object>) inputsObj);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract workflow inputs: {}", e.getMessage());
        }

        return inputs;
    }

    /**
     * 人工任务 - 等待外部调用 completeHumanTask 完成
     */
    private CompletableFuture<String> executeHumanTask(String subTaskId, String description,
                                                        String packageId, String traceId) {
        // 发布任务分配给人工的事件
        eventBus.publishSubtaskAssigned(packageId, subTaskId, "HUMAN", "HUMAN", traceId);

        // 注册到待完成列表，等待外部调用 completeHumanTask
        String key = packageId + "_" + subTaskId;
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingHumanTasks.put(key, future);

        log.info("Human task {} in package {} registered, awaiting external completion", subTaskId, packageId);

        // 设置超时，避免永久阻塞
        CompletableFuture<String> timeoutFuture = future.orTimeout(30, java.util.concurrent.TimeUnit.MINUTES);
        timeoutFuture.exceptionally(e -> {
            pendingHumanTasks.remove(key);
            log.warn("Human task {} timed out after 30 minutes", subTaskId);
            metricsService.recordSubtask(packageId, subTaskId, "HUMAN", 30 * 60 * 1000, "FAILED");
            eventBus.publishSubtaskFailed(packageId, subTaskId, "HUMAN", "Human task timeout after 30 minutes", traceId);
            return null;
        });

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

    /**
     * 根据角色能力选择最匹配的 Agent
     *
     * 匹配策略：
     * 1. 从 ROLE_CAPABILITY_KEYWORDS 获取该角色对应的能力关键词列表
     * 2. 对每个 Agent，计算其在 name/description 中的关键词匹配得分
     * 3. description 全文匹配权重低于 name 精确匹配
     * 4. 得分最高者胜出；得分相同则取第一个（稳定排序）
     * 5. 无任何匹配时降级为第一个可用 Agent
     *
     * @param agents 可用 Agent 列表
     * @param expectedRole 子任务期望的角色（PLANNER/SPECIALIST/REVIEWER/CRITIC/COORDINATOR）
     * @param description 子任务描述（用于补充匹配）
     * @return 匹配的 Agent
     */
    private AgentMetadata selectAgentByCapability(List<AgentMetadata> agents, String expectedRole, String description) {
        String role = expectedRole != null ? expectedRole : "SPECIALIST";

        // 获取该角色的能力关键词
        List<String> capabilityKeywords = ROLE_CAPABILITY_KEYWORDS.getOrDefault(role, List.of("specialist"));

        // 找出最高分 Agent
        AgentMetadata bestAgent = null;
        int bestScore = -1;

        for (AgentMetadata agent : agents) {
            int score = calculateCapabilityScore(agent, capabilityKeywords, description);
            if (score > bestScore) {
                bestScore = score;
                bestAgent = agent;
            }
        }

        // 无匹配降级到第一个
        if (bestAgent == null) {
            log.warn("No capability match for role={}, falling back to first available agent", role);
            return agents.get(0);
        }

        log.debug("Agent {} selected for role={} with score={}", bestAgent.getAgentId(), role, bestScore);
        return bestAgent;
    }

    /**
     * 计算 Agent 对给定能力关键词的匹配得分
     */
    private int calculateCapabilityScore(AgentMetadata agent, List<String> keywords, String description) {
        int score = 0;
        String name = agent.getName() != null ? agent.getName().toLowerCase() : "";
        String desc = agent.getDescription() != null ? agent.getDescription().toLowerCase() : "";

        for (String keyword : keywords) {
            String kw = keyword.toLowerCase();

            // name 精确匹配（最高权重）
            if (name.equals(kw)) {
                score += CAPABILITY_WEIGHTS.getOrDefault(kw, 5) * 3;
            } else if (name.contains(kw)) {
                // name 包含匹配
                score += CAPABILITY_WEIGHTS.getOrDefault(kw, 5) * 2;
            }

            // description 全文匹配
            if (desc.contains(kw)) {
                score += CAPABILITY_WEIGHTS.getOrDefault(kw, 5);
            }
        }

        // 任务描述补充匹配（权重较低）
        if (description != null) {
            String taskDesc = description.toLowerCase();
            for (String keyword : keywords) {
                if (taskDesc.contains(keyword.toLowerCase())) {
                    score += 1;
                }
            }
        }

        return score;
    }
}