package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.service.AgentManageService;
import com.adlin.orin.modules.collaboration.config.CollaborationOrchestrationMode;
import com.adlin.orin.modules.collaboration.consumer.CollaborationResultListener;
import com.adlin.orin.modules.collaboration.dto.CollabTaskMessage;
import com.adlin.orin.modules.collaboration.dto.CollaborationPackage;
import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.entity.CollaborationPackageEntity;
import com.adlin.orin.modules.collaboration.event.CollaborationEventBus;
import com.adlin.orin.modules.collaboration.metrics.CollaborationMetricsService;
import com.adlin.orin.modules.collaboration.producer.CollaborationMQProducer;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.collaboration.service.selection.AgentSelectionContext;
import com.adlin.orin.modules.collaboration.service.selection.AgentSelectionResult;
import com.adlin.orin.modules.collaboration.service.selection.BiddingSelector;
import com.adlin.orin.modules.collaboration.service.selection.StaticSelector;
import com.adlin.orin.modules.observability.service.LangfuseObservabilityService;
import com.adlin.orin.modules.workflow.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 协作任务执行器 - 负责执行子任务，调用智能体或工作流
 * 支持 JAVA_NATIVE 和 LANGGRAPH_MQ 两种编舞模式
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
    private final CollaborationOrchestrationMode orchestrationMode;
    private final CollaborationMQProducer mqProducer;
    private final CollaborationResultListener resultListener;
    private final CollaborationPackageRepository packageRepository;
    private final CollaborationMemoryService memoryService;
    private final CollaborationRedisService redisService;
    private final StaticSelector staticSelector;
    private final BiddingSelector biddingSelector;

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
     * 根据协作模式和子任务类型，调用对应的执行器
     */
    public CompletableFuture<String> executeSubtask(CollabSubtaskEntity subtask, String packageId, String traceId) {
        String subTaskId = subtask.getSubTaskId();
        String description = subtask.getDescription();
        String expectedRole = subtask.getExpectedRole();
        String executorType = determineExecutorType(expectedRole);

        log.info("Executing subtask: {} for package: {} with role: {} (type: {})", subTaskId, packageId, expectedRole, executorType);

        String lockToken = UUID.randomUUID().toString();
        if (!redisService.acquireLockWithToken(packageId, subTaskId, lockToken, Duration.ofMinutes(30))) {
            log.info("Skipped duplicate subtask execution due to lock: packageId={}, subTaskId={}", packageId, subTaskId);
            return CompletableFuture.completedFuture("Subtask already in progress");
        }

        // 获取协作模式，判断是否启用 MQ
        String collaborationMode = getCollaborationMode(packageId);
        boolean useMq = orchestrationMode.isMqEnabled(collaborationMode);

        // HUMAN 类型不支持 MQ，必须走本地执行
        if (EXECUTOR_TYPE_HUMAN.equals(executorType)) {
            CompletableFuture<String> humanFuture = executeHumanTask(subTaskId, description, packageId, traceId);
            humanFuture.whenComplete((r, e) -> redisService.releaseLockWithToken(packageId, subTaskId, lockToken));
            return humanFuture;
        }

        // 检查是否启用 MQ 模式
        if (useMq) {
            CompletableFuture<String> mqFuture = executeViaMQ(subtask, packageId, traceId, collaborationMode);
            mqFuture.whenComplete((r, e) -> redisService.releaseLockWithToken(packageId, subTaskId, lockToken));
            return mqFuture;
        }

        // 回退到 Java 原生执行
        CompletableFuture<String> localFuture = switch (executorType) {
            case EXECUTOR_TYPE_WORKFLOW -> executeWithWorkflow(subTaskId, description, expectedRole, packageId, traceId, subtask);
            default -> executeWithAgent(subTaskId, description, expectedRole, packageId, traceId);
        };
        localFuture.whenComplete((r, e) -> redisService.releaseLockWithToken(packageId, subTaskId, lockToken));
        return localFuture;
    }

    /**
     * 获取协作模式
     */
    private String getCollaborationMode(String packageId) {
        return packageRepository.findByPackageId(packageId)
                .map(CollaborationPackageEntity::getCollaborationMode)
                .orElse("SEQUENTIAL");
    }

    /**
     * 通过 MQ 执行子任务
     */
    private CompletableFuture<String> executeViaMQ(CollabSubtaskEntity subtask, String packageId,
                                                   String traceId, String collaborationMode) {
        CompletableFuture<String> future = new CompletableFuture<>();
        String callbackKey = packageId + ":" + subtask.getSubTaskId();

        // 注册回调
        resultListener.registerCallback(callbackKey, future);

        try {
            // 构建上下文快照
            Map<String, Object> contextSnapshot = buildContextSnapshot(packageId);

            // 构建消息
            String sessionId = contextSnapshot.get("sessionId") != null ? String.valueOf(contextSnapshot.get("sessionId")) : null;
            String turnId = contextSnapshot.get("turnId") != null ? String.valueOf(contextSnapshot.get("turnId")) : null;
            CollabTaskMessage message = CollabTaskMessage.builder()
                    .packageId(packageId)
                    .sessionId(sessionId)
                    .turnId(turnId)
                    .subTaskId(subtask.getSubTaskId())
                    .traceId(traceId)
                    .attempt(subtask.getRetryCount() != null ? subtask.getRetryCount() : 0)
                    .stage("DRAFT")
                    .collaborationMode(collaborationMode)
                    .expectedRole(subtask.getExpectedRole())
                    .description(subtask.getDescription())
                    .inputData(subtask.getInputData())
                    .dependsOn(parseDependsOn(subtask.getDependsOn()))
                    .maxRetries(3)
                    .timeoutMillis(300000L)
                    .executionStrategy(determineExecutorType(subtask.getExpectedRole()))
                    .replyTo("collaboration-task-result-queue")
                    .correlationId(callbackKey)
                    .contextSnapshot(contextSnapshot)
                    .enqueuedAt(System.currentTimeMillis())
                    .retryInitialInterval(1000L)
                    .retryMultiplier(2.0)
                    .retryMaxInterval(30000L)
                    .delayedRetry(true)
                    .build();

            // 保存 pending_task 到 Redis（用于重试时恢复）。
            // 这里必须按 subTaskId 隔离，避免并行分支互相覆盖导致重试拿错上下文。
            Map<String, Object> pendingTaskData = new HashMap<>();
            pendingTaskData.put("collaborationMode", collaborationMode);
            pendingTaskData.put("expectedRole", subtask.getExpectedRole());
            pendingTaskData.put("description", subtask.getDescription());
            pendingTaskData.put("inputData", subtask.getInputData());
            pendingTaskData.put("executionStrategy", determineExecutorType(subtask.getExpectedRole()));
            pendingTaskData.put("contextSnapshot", contextSnapshot);
            pendingTaskData.put("maxRetries", 3);
            redisService.updateContextField(packageId, buildPendingTaskField(subtask.getSubTaskId()), pendingTaskData);

            // 发送消息
            mqProducer.sendTask(message);

            log.info("Subtask sent to MQ: packageId={}, subTaskId={}", packageId, subtask.getSubTaskId());

        } catch (Exception e) {
            log.error("Failed to send subtask to MQ: packageId={}, subTaskId={}", packageId, subtask.getSubTaskId(), e);
            future.completeExceptionally(e);
            resultListener.unregisterCallback(callbackKey);
        }

        return future;
    }

    /**
     * 构建上下文快照（用于 MQ Worker）
     */
    private Map<String, Object> buildContextSnapshot(String packageId) {
        Map<String, Object> snapshot = new HashMap<>();

        // 添加黑板数据
        Map<String, Object> blackboard = new HashMap<>(memoryService.readAllBlackboard(packageId));
        snapshot.put("blackboard", blackboard);

        // 添加光标
        snapshot.put("cursor", memoryService.getCursor(packageId));

        // 添加当前包状态
        packageRepository.findByPackageId(packageId)
                .ifPresent(pkg -> {
                    snapshot.put("packageStatus", pkg.getStatus());
                    snapshot.put("collaborationMode", pkg.getCollaborationMode());
                });

        return snapshot;
    }

    private String buildPendingTaskField(String subTaskId) {
        return "pending_task:" + subTaskId;
    }

    /**
     * 解析依赖列表
     */
    @SuppressWarnings("unchecked")
    private List<String> parseDependsOn(String dependsOnJson) {
        if (dependsOnJson == null || dependsOnJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return (List<String>) objectMapper.readValue(dependsOnJson, List.class);
        } catch (Exception e) {
            log.warn("Failed to parse dependsOn: {}", dependsOnJson);
            return Collections.emptyList();
        }
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

            CollaborationPackage.ExecutionStrategy strategy = resolveExecutionStrategy(packageId);
            String policy = strategy != null && strategy.getMainAgentPolicy() != null
                    ? strategy.getMainAgentPolicy().toUpperCase(Locale.ROOT)
                    : "STATIC_THEN_BID";
            double qualityThreshold = strategy != null && strategy.getQualityThreshold() != null
                    ? strategy.getQualityThreshold()
                    : 0.82;

            AgentSelectionContext selectionContext = AgentSelectionContext.builder()
                    .packageId(packageId)
                    .subTaskId(subTaskId)
                    .expectedRole(expectedRole)
                    .description(description)
                    .qualityThreshold(qualityThreshold)
                    .build();

            AgentSelectionResult staticSelection = staticSelector.select(agents, selectionContext, strategy);
            String staticAgentId = staticSelection.getSelectedAgentId();
            String selectedAgentId = staticAgentId;
            String selectionMode = "static";
            String selectionReason = staticSelection.getSelectionReason();

            AgentExecutionAttempt staticAttempt = executeAgentAttempt(staticAgentId, description, qualityThreshold);
            AgentExecutionAttempt finalAttempt = staticAttempt;

            if ("STATIC_THEN_BID".equals(policy) && shouldTriggerBidding(staticAttempt)) {
                AgentSelectionResult bidSelection = biddingSelector.select(agents, selectionContext, strategy, staticAgentId);
                String bidAgentId = bidSelection.getSelectedAgentId();
                if (bidAgentId != null && !bidAgentId.isBlank()) {
                    AgentExecutionAttempt bidAttempt = executeAgentAttempt(bidAgentId, description, qualityThreshold);
                    selectedAgentId = bidAgentId;
                    selectionMode = "bid";
                    selectionReason = "fallback_after_static_" + staticAttempt.failureReason();
                    finalAttempt = bidAttempt;
                    writeSelectionAudit(packageId, subTaskId, bidSelection, selectionReason, staticSelection);
                } else {
                    writeSelectionAudit(packageId, subTaskId, staticSelection, "bid_no_candidate", null);
                }
            } else {
                writeSelectionAudit(packageId, subTaskId, staticSelection, selectionReason, null);
            }

            if (finalAttempt.success()) {
                String result = finalAttempt.result();
                log.info("Agent execution completed for subtask: {}, result: {}", subTaskId,
                        result.length() > 100 ? result.substring(0, 100) + "..." : result);

                long durationMs = System.currentTimeMillis() - startTime;
                recordCollabEvent(traceId, subTaskId, "SUBTASK_COMPLETED", Map.of(
                        "packageId", packageId != null ? packageId : "",
                        "agentId", selectedAgentId != null ? selectedAgentId : "",
                        "selectionMode", selectionMode,
                        "selectionReason", selectionReason != null ? selectionReason : "",
                        "durationMs", durationMs
                ));

                metricsService.recordSubtask(packageId, subTaskId, expectedRole, durationMs, "COMPLETED");
                eventBus.publishSubtaskCompleted(packageId, subTaskId, selectedAgentId,
                        Map.of(
                                "result", result,
                                "agentId", selectedAgentId != null ? selectedAgentId : "",
                                "selectedAgentId", selectedAgentId != null ? selectedAgentId : "",
                                "selectionMode", selectionMode,
                                "selectionReason", selectionReason != null ? selectionReason : ""
                        ), traceId);
                future.complete(result);
            } else {
                String errorMessage = "Agent execution failed: " + finalAttempt.failureReason();
                log.warn("Subtask {} failed with policy {}: {}", subTaskId, policy, errorMessage);
                eventBus.publishSubtaskFailed(packageId, subTaskId, selectedAgentId, errorMessage, traceId);
                metricsService.recordSubtask(packageId, subTaskId, expectedRole,
                        System.currentTimeMillis() - startTime, "FAILED");
                future.completeExceptionally(new RuntimeException(errorMessage));
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

    private AgentExecutionAttempt executeAgentAttempt(String agentId, String prompt, double qualityThreshold) {
        if (agentId == null || agentId.isBlank()) {
            return AgentExecutionAttempt.failed("no_selected_agent");
        }
        try {
            Optional<Object> response = CompletableFuture
                    .supplyAsync(() -> agentManageService.chat(agentId, prompt, (String) null))
                    .orTimeout(60, TimeUnit.SECONDS)
                    .join();
            if (response == null || response.isEmpty()) {
                return AgentExecutionAttempt.failed("empty_response");
            }
            String result = convertResponseToString(response.get());
            if (result == null || result.isBlank()) {
                return AgentExecutionAttempt.failed("blank_response");
            }
            double score = estimateQualityScore(result);
            if (score < qualityThreshold) {
                // 质量阈值用于路由/观测，不应直接导致整轮失败。
                // 否则简短回答（如多模态请求、确认类回复）会被误判并中断协作会话。
                log.info("Agent response below quality threshold but accepted: score={}, threshold={}, agentId={}",
                        score, qualityThreshold, agentId);
            }
            return AgentExecutionAttempt.success(result);
        } catch (Exception e) {
            return AgentExecutionAttempt.failed(e.getClass().getSimpleName() + ":" + e.getMessage());
        }
    }

    private boolean shouldTriggerBidding(AgentExecutionAttempt attempt) {
        return !attempt.success();
    }

    private double estimateQualityScore(String text) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("failed") || lower.contains("error") || lower.contains("超时")) {
            return 0.1;
        }
        int length = text.length();
        if (length >= 1200) {
            return 0.95;
        }
        if (length >= 600) {
            return 0.86;
        }
        if (length >= 300) {
            return 0.8;
        }
        if (length >= 100) {
            return 0.72;
        }
        return 0.55;
    }

    private CollaborationPackage.ExecutionStrategy resolveExecutionStrategy(String packageId) {
        return packageRepository.findByPackageId(packageId)
                .map(CollaborationPackageEntity::getStrategy)
                .filter(s -> s != null && !s.isBlank())
                .map(s -> {
                    try {
                        return objectMapper.readValue(s, CollaborationPackage.ExecutionStrategy.class);
                    } catch (Exception e) {
                        log.warn("Failed to parse execution strategy for package {}: {}", packageId, e.getMessage());
                        return null;
                    }
                })
                .orElse(null);
    }

    private void writeSelectionAudit(String packageId, String subTaskId, AgentSelectionResult selection,
                                     String overrideReason, AgentSelectionResult previous) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("selectedAgentId", selection != null ? selection.getSelectedAgentId() : null);
        payload.put("selectionMode", selection != null ? selection.getSelectionMode() : null);
        payload.put("selectionReason", overrideReason != null ? overrideReason :
                (selection != null ? selection.getSelectionReason() : null));
        payload.put("scoreBreakdown", selection != null ? selection.getScoreBreakdown() : Map.of());
        payload.put("candidates", selection != null ? selection.getCandidates() : List.of());
        if (previous != null) {
            payload.put("previousSelection", previous);
        }
        payload.put("timestamp", System.currentTimeMillis());
        memoryService.writeToBlackboard(packageId, "selection_last", payload);
        memoryService.writeToBlackboard(packageId, "selection_" + subTaskId, payload);
    }

    private record AgentExecutionAttempt(boolean success, String result, String failureReason) {
        static AgentExecutionAttempt success(String result) {
            return new AgentExecutionAttempt(true, result, null);
        }

        static AgentExecutionAttempt failed(String reason) {
            return new AgentExecutionAttempt(false, null, reason);
        }
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
