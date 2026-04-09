package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.modules.collaboration.dto.CollaborationPackage;
import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.entity.CollaborationPackageEntity;
import com.adlin.orin.modules.collaboration.event.CollaborationEventBus;
import com.adlin.orin.modules.collaboration.repository.CollabSubtaskRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 协作编排服务 - 负责任务分解、调度、共识与回退
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationOrchestrator {

    private final CollaborationPackageRepository packageRepository;
    private final CollabSubtaskRepository subtaskRepository;
    private final CollaborationMemoryService memoryService;
    private final CollaborationEventBus eventBus;
    private final AuditHelper auditHelper;
    private final ObjectMapper objectMapper;

    // 角色类型常量
    public static final String ROLE_PLANNER = "PLANNER";
    public static final String ROLE_SPECIALIST = "SPECIALIST";
    public static final String ROLE_REVIEWER = "REVIEWER";
    public static final String ROLE_CRITIC = "CRITIC";
    public static final String ROLE_COORDINATOR = "COORDINATOR";

    // 协作模式
    public static final String MODE_SEQUENTIAL = "SEQUENTIAL";
    public static final String MODE_PARALLEL = "PARALLEL";
    public static final String MODE_CONSENSUS = "CONSENSUS";
    public static final String MODE_HIERARCHICAL = "HIERARCHICAL";

    /**
     * 子任务状态
     */
    public static final String SUBTASK_PENDING = "PENDING";
    public static final String SUBTASK_RUNNING = "RUNNING";
    public static final String SUBTASK_COMPLETED = "COMPLETED";
    public static final String SUBTASK_FAILED = "FAILED";
    public static final String SUBTASK_SKIPPED = "SKIPPED";
    public static final String SUBTASK_CANCELLED = "CANCELLED";
    public static final String SUBTASK_AWAITING_HUMAN = "AWAITING_HUMAN_INPUT";
    public static final String SUBTASK_MANUAL_HANDLING = "MANUAL_HANDLING";

    // 状态流转校验映射：当前状态 -> 可转换的目标状态
    private static final Map<String, Set<String>> SUBTASK_STATUS_TRANSITIONS = Map.of(
            SUBTASK_PENDING, Set.of(SUBTASK_RUNNING, SUBTASK_CANCELLED, SUBTASK_AWAITING_HUMAN),
            SUBTASK_RUNNING, Set.of(SUBTASK_COMPLETED, SUBTASK_FAILED, SUBTASK_CANCELLED, SUBTASK_AWAITING_HUMAN, SUBTASK_MANUAL_HANDLING),
            SUBTASK_COMPLETED, Set.of(),  // 已完成状态不可转换
            SUBTASK_FAILED, Set.of(SUBTASK_PENDING, SUBTASK_SKIPPED),  // 失败后可重试或跳过
            SUBTASK_SKIPPED, Set.of(),  // 已跳过不可转换
            SUBTASK_CANCELLED, Set.of(SUBTASK_PENDING),  // 取消后可重置
            SUBTASK_AWAITING_HUMAN, Set.of(SUBTASK_COMPLETED, SUBTASK_CANCELLED),  // 人工输入后可完成或取消
            SUBTASK_MANUAL_HANDLING, Set.of(SUBTASK_COMPLETED, SUBTASK_CANCELLED)  // 人工接管后可完成或取消
    );

    // 任务状态
    public static final String STATUS_PLANNING = "PLANNING";
    public static final String STATUS_DECOMPOSING = "DECOMPOSING";
    public static final String STATUS_EXECUTING = "EXECUTING";
    public static final String STATUS_CONSENSUS = "CONSENSUS";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_FALLBACK = "FALLBACK";

    /**
     * 创建协作任务包
     */
    @Transactional
    public CollaborationPackage createPackage(String intent, String category, String priority,
                                                String complexity, String collaborationMode,
                                                String createdBy, String traceId) {
        return createPackage(intent, category, priority, complexity, collaborationMode, createdBy, traceId, null);
    }

    @Transactional
    public CollaborationPackage createPackage(String intent, String category, String priority,
                                              String complexity, String collaborationMode,
                                              String createdBy, String traceId,
                                              Map<String, Object> strategyOverrides) {
        String packageId = UUID.randomUUID().toString().replace("-", "");

        // 构建意图标签
        CollaborationPackage.IntentTag intentTag = CollaborationPackage.IntentTag.builder()
                .category(category)
                .priority(priority)
                .complexity(complexity)
                .needReview("HIGH".equals(priority) || "URGENT".equals(priority))
                .needConsensus("COMPLEX".equals(complexity) || "VERY_COMPLEX".equals(complexity))
                .build();

        double qualityThreshold = parseDouble(strategyOverrides, "qualityThreshold", 0.82);
        int maxCritiqueRounds = parseInt(strategyOverrides, "maxCritiqueRounds", 3);
        int draftParallelism = parseInt(strategyOverrides, "draftParallelism", 4);
        String mainAgentPolicy = parseString(strategyOverrides, "mainAgentPolicy", "STATIC_THEN_BID");
        String staticMainAgent = parseString(strategyOverrides, "mainAgentStaticDefault",
                parseString(strategyOverrides, "staticMainAgent", null));
        List<String> bidWhitelist = parseList(strategyOverrides, "bidWhitelist");
        double bidWeightReasoning = parseDouble(strategyOverrides, "bidWeightReasoning", 0.6);
        double bidWeightSpeed = parseDouble(strategyOverrides, "bidWeightSpeed", 0.3);
        double bidWeightCost = parseDouble(strategyOverrides, "bidWeightCost", 0.1);

        // 强制权重顺序：推理能力 > 速度 > 成本
        if (!(bidWeightReasoning > bidWeightSpeed && bidWeightSpeed > bidWeightCost)) {
            bidWeightReasoning = 0.6;
            bidWeightSpeed = 0.3;
            bidWeightCost = 0.1;
        }

        // 构建执行策略
        CollaborationPackage.ExecutionStrategy strategy = CollaborationPackage.ExecutionStrategy.builder()
                .maxParallel(3)
                .timeoutSeconds(300)
                .maxRetries(2)
                .retryStrategy("EXPONENTIAL")
                .fallbackStrategy("ROLLBACK")
                .consensusStrategy("MAJORITY")
                .enableMemorySharing(true)
                .enableEventDriven(true)
                .mainAgentPolicy(mainAgentPolicy)
                .mainAgentStaticDefault(staticMainAgent)
                .bidWhitelist(bidWhitelist)
                .bidWeightReasoning(bidWeightReasoning)
                .bidWeightSpeed(bidWeightSpeed)
                .bidWeightCost(bidWeightCost)
                .qualityThreshold(qualityThreshold)
                .maxCritiqueRounds(maxCritiqueRounds)
                .draftParallelism(draftParallelism)
                .build();

        // 创建实体
        CollaborationPackageEntity entity = CollaborationPackageEntity.builder()
                .packageId(packageId)
                .intent(intent)
                .intentCategory(category)
                .intentPriority(priority)
                .intentComplexity(complexity)
                .needReview(intentTag.getNeedReview())
                .needConsensus(intentTag.getNeedConsensus())
                .collaborationMode(collaborationMode)
                .strategy(toJson(strategy))
                .status(STATUS_PLANNING)
                .traceId(traceId)
                .createdBy(createdBy)
                .timeoutAt(LocalDateTime.now().plusSeconds(strategy.getTimeoutSeconds()))
                .build();

        entity = packageRepository.save(entity);

        // 发布事件
        eventBus.publishPackageCreated(packageId, intent, traceId);

        // 写入初始黑板数据
        memoryService.writeToBlackboard(packageId, "intent", intent);
        memoryService.writeToBlackboard(packageId, "strategy", strategy);

        return toDto(entity);
    }

    /**
     * 任务分解 - 将用户意图分解为子任务
     */
    @Transactional
    public CollaborationPackage decompose(String packageId, List<String> capabilities) {
        Optional<CollaborationPackageEntity> entityOpt = packageRepository.findByPackageId(packageId);
        if (entityOpt.isEmpty()) {
            throw new RuntimeException("Package not found: " + packageId);
        }

        CollaborationPackageEntity entity = entityOpt.get();
        entity.setStatus(STATUS_DECOMPOSING);
        packageRepository.save(entity);

        // 根据意图类型分解任务
        String category = entity.getIntentCategory();
        List<CollabSubtaskEntity> subtasks = generateSubtasks(packageId, category, capabilities);

        entity.setStatus(STATUS_EXECUTING);
        packageRepository.save(entity);

        // 发布任务分解完成事件
        eventBus.publishPackageDecomposed(packageId, subtasks.size(), entity.getTraceId());

        // 保存检查点
        memoryService.saveCheckpoint(packageId, "decomposed", Map.of("subtasks", subtasks.size()));

        return toDto(entity, subtasks);
    }

    /**
     * 生成子任务
     */
    private List<CollabSubtaskEntity> generateSubtasks(String packageId, String category, List<String> capabilities) {
        List<CollabSubtaskEntity> subtasks = new ArrayList<>();

        switch (category != null ? category : "GENERAL") {
            case "ANALYSIS":
            case "RESEARCH":
                // 分析类：先调研，再分析，最后总结
                subtasks.add(createSubtask(packageId, "1", "收集相关信息和数据", ROLE_SPECIALIST, null, 0.8));
                subtasks.add(createSubtask(packageId, "2", "分析数据并提取洞察", ROLE_SPECIALIST, List.of("1"), 0.85));
                subtasks.add(createSubtask(packageId, "3", "总结分析结果", ROLE_REVIEWER, List.of("2"), 0.9));
                break;

            case "GENERATION":
            case "CODING":
                // 生成类：先规划，再生成，最后审查
                subtasks.add(createSubtask(packageId, "1", "制定生成计划", ROLE_PLANNER, null, 0.8));
                subtasks.add(createSubtask(packageId, "2", "执行生成任务", ROLE_SPECIALIST, List.of("1"), 0.85));
                subtasks.add(createSubtask(packageId, "3", "审查生成结果", ROLE_REVIEWER, List.of("2"), 0.9));
                break;

            case "REVIEW":
                // 审查类：先执行，再审查，最后确认
                subtasks.add(createSubtask(packageId, "1", "执行待审查内容", ROLE_SPECIALIST, null, 0.8));
                subtasks.add(createSubtask(packageId, "2", "进行审查评估", ROLE_REVIEWER, List.of("1"), 0.85));
                subtasks.add(createSubtask(packageId, "3", "确认审查结论", ROLE_CRITIC, List.of("2"), 0.9));
                break;

            case "TESTING":
                // 测试类：先执行测试，再分析结果，最后修复
                subtasks.add(createSubtask(packageId, "1", "执行测试用例", ROLE_SPECIALIST, null, 0.8));
                subtasks.add(createSubtask(packageId, "2", "分析测试结果", ROLE_SPECIALIST, List.of("1"), 0.85));
                subtasks.add(createSubtask(packageId, "3", "修复发现的问题", ROLE_SPECIALIST, List.of("2"), 0.8));
                subtasks.add(createSubtask(packageId, "4", "验证修复结果", ROLE_REVIEWER, List.of("3"), 0.9));
                break;

            default:
                // 默认：简单的规划-执行-审查流程
                subtasks.add(createSubtask(packageId, "1", "理解任务需求", ROLE_PLANNER, null, 0.8));
                subtasks.add(createSubtask(packageId, "2", "执行任务", ROLE_SPECIALIST, List.of("1"), 0.85));
                subtasks.add(createSubtask(packageId, "3", "审查结果", ROLE_REVIEWER, List.of("2"), 0.9));
                break;
        }

        return subtaskRepository.saveAll(subtasks);
    }

    private CollabSubtaskEntity createSubtask(String packageId, String subTaskId, String description,
                                               String role, List<String> dependsOn, double confidence) {
        return CollabSubtaskEntity.builder()
                .packageId(packageId)
                .subTaskId(subTaskId)
                .description(description)
                .expectedRole(role)
                .dependsOn(toJson(dependsOn))
                .confidence(confidence)
                .status("PENDING")
                .retryCount(0)
                .build();
    }

    /**
     * 获取可执行的子任务（依赖已满足）
     */
    public List<CollabSubtaskEntity> getExecutableSubtasks(String packageId) {
        List<CollabSubtaskEntity> subtasks = subtaskRepository.findByPackageId(packageId);

        // 找出状态为 PENDING 的子任务
        List<CollabSubtaskEntity> pendingTasks = subtasks.stream()
                .filter(t -> "PENDING".equals(t.getStatus()))
                .collect(Collectors.toList());

        // 检查依赖是否满足
        return pendingTasks.stream()
                .filter(t -> {
                    List<String> dependsOn = fromJsonList(t.getDependsOn());
                    if (dependsOn == null || dependsOn.isEmpty()) {
                        return true;
                    }
                    // 所有依赖的子任务必须已完成
                    return dependsOn.stream().allMatch(depId ->
                            subtasks.stream()
                                    .filter(s -> s.getSubTaskId().equals(depId))
                                    .anyMatch(s -> "COMPLETED".equals(s.getStatus()))
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * 更新子任务状态
     * @throws IllegalStateException 如果状态转换不合法
     */
    @Transactional
    public CollabSubtaskEntity updateSubtaskStatus(String packageId, String subTaskId, String status,
                                                    String result, String errorMessage) {
        Optional<CollabSubtaskEntity> subtaskOpt = subtaskRepository.findByPackageIdAndSubTaskId(packageId, subTaskId);

        if (subtaskOpt.isEmpty()) {
            throw new RuntimeException("Subtask not found: " + subTaskId);
        }

        CollabSubtaskEntity subtask = subtaskOpt.get();
        String currentStatus = subtask.getStatus();

        // 校验状态转换是否合法
        if (!isValidStatusTransition(currentStatus, status)) {
            throw new IllegalStateException(
                    String.format("Invalid status transition: %s -> %s for subtask %s",
                            currentStatus, status, subTaskId));
        }

        subtask.setStatus(status);

        if (result != null) {
            subtask.setResult(result);
        }

        if (errorMessage != null) {
            subtask.setErrorMessage(errorMessage);
        }

        if ("RUNNING".equals(status)) {
            subtask.setStartedAt(LocalDateTime.now());
            // 更新执行游标
            memoryService.updateCursor(packageId, subTaskId, "RUNNING");
            // 发布子任务开始事件
            eventBus.publishSubtaskAssigned(packageId, subTaskId, subtask.getExecutedBy(), subtask.getExpectedRole(), getTraceId(packageId));
        } else if ("COMPLETED".equals(status)) {
            subtask.setCompletedAt(LocalDateTime.now());
            // 写入黑板
            memoryService.writeToBlackboard(packageId, "subtask_" + subTaskId + "_result", result);
            // 发布子任务完成事件
            eventBus.publishSubtaskCompleted(packageId, subTaskId, subtask.getExecutedBy(), Map.of("result", result != null ? result : ""), getTraceId(packageId));
            // 保存检查点
            memoryService.saveCheckpoint(packageId, "subtask_" + subTaskId + "_completed", Map.of("status", "completed", "result", result != null ? result : ""));
        } else if ("FAILED".equals(status)) {
            subtask.setCompletedAt(LocalDateTime.now());
            // 发布子任务失败事件
            eventBus.publishSubtaskFailed(packageId, subTaskId, subtask.getExecutedBy(), errorMessage != null ? errorMessage : "Unknown error", getTraceId(packageId));
        }

        return subtaskRepository.save(subtask);
    }

    /**
     * 获取追踪 ID
     */
    private String getTraceId(String packageId) {
        return packageRepository.findByPackageId(packageId)
                .map(CollaborationPackageEntity::getTraceId)
                .orElse(null);
    }

    /**
     * 检查是否所有子任务都已完成
     */
    public boolean isAllSubtasksCompleted(String packageId) {
        List<CollabSubtaskEntity> subtasks = subtaskRepository.findByPackageId(packageId);
        return subtasks.stream().allMatch(t -> "COMPLETED".equals(t.getStatus()));
    }

    /**
     * 检查是否有子任务失败
     */
    public boolean hasFailedSubtask(String packageId) {
        List<CollabSubtaskEntity> subtasks = subtaskRepository.findByPackageId(packageId);
        return subtasks.stream().anyMatch(t -> "FAILED".equals(t.getStatus()));
    }

    /**
     * 执行回退策略
     */
    @Transactional
    public CollaborationPackage executeFallback(String packageId, String reason) {
        Optional<CollaborationPackageEntity> entityOpt = packageRepository.findByPackageId(packageId);
        if (entityOpt.isEmpty()) {
            throw new RuntimeException("Package not found: " + packageId);
        }

        CollaborationPackageEntity entity = entityOpt.get();
        entity.setStatus(STATUS_FALLBACK);
        entity.setErrorMessage("Fallback triggered: " + reason);

        // 记录当前状态用于回放
        log.warn("Collaboration fallback triggered for package: {}, reason: {}", packageId, reason);

        // 发布回退事件
        eventBus.publishFallbackTriggered(packageId, reason, entity.getTraceId());

        return toDto(packageRepository.save(entity));
    }

    /**
     * 完成协作任务
     */
    @Transactional
    public CollaborationPackage complete(String packageId, String result) {
        Optional<CollaborationPackageEntity> entityOpt = packageRepository.findByPackageId(packageId);
        if (entityOpt.isEmpty()) {
            throw new RuntimeException("Package not found: " + packageId);
        }

        CollaborationPackageEntity entity = entityOpt.get();
        entity.setStatus(STATUS_COMPLETED);
        entity.setResult(result);
        entity.setCompletedAt(LocalDateTime.now());

        // 写入最终结果到黑板
        memoryService.writeToBlackboard(packageId, "final_result", result);

        // 保存最终检查点
        memoryService.saveCheckpoint(packageId, "completed", Map.of("result", result, "timestamp", System.currentTimeMillis()));

        // 记录审计日志
        recordAuditLog(entity, "COMPLETED", result, null);

        // 发布任务包完成事件
        eventBus.publishPackageStatusChanged(packageId, STATUS_COMPLETED, getTraceId(packageId));

        return toDto(packageRepository.save(entity));
    }

    /**
     * 记录协作包审计日志
     */
    private void recordAuditLog(CollaborationPackageEntity entity, String status, String result, String errorMessage) {
        try {
            auditHelper.log(
                    entity.getCreatedBy(),
                    "COLLABORATION_" + status,
                    "/collaboration/package",
                    String.format("任务包[%s]完成: 意图=%s, 状态=%s, 结果=%s",
                            entity.getPackageId(),
                            entity.getIntent() != null ? entity.getIntent().substring(0, Math.min(50, entity.getIntent().length())) : "",
                            status,
                            result != null ? result.substring(0, Math.min(100, result.length())) : ""),
                    errorMessage == null,
                    errorMessage
            );
        } catch (Exception e) {
            log.warn("记录协作审计日志失败: {}", e.getMessage());
        }
    }

    /**
     * 失败协作任务
     */
    @Transactional
    public CollaborationPackage fail(String packageId, String errorMessage) {
        Optional<CollaborationPackageEntity> entityOpt = packageRepository.findByPackageId(packageId);
        if (entityOpt.isEmpty()) {
            throw new RuntimeException("Package not found: " + packageId);
        }

        CollaborationPackageEntity entity = entityOpt.get();
        entity.setStatus(STATUS_FAILED);
        entity.setErrorMessage(errorMessage);
        entity.setCompletedAt(LocalDateTime.now());

        // 清理 Redis 资源
        memoryService.cleanupPackage(packageId);

        // 记录审计日志
        recordAuditLog(entity, "FAILED", null, errorMessage);

        // 发布任务包失败事件
        eventBus.publishPackageStatusChanged(packageId, STATUS_FAILED, getTraceId(packageId));

        return toDto(packageRepository.save(entity));
    }

    /**
     * 暂停协作任务包
     */
    @Transactional
    public CollaborationPackage pause(String packageId) {
        Optional<CollaborationPackageEntity> entityOpt = packageRepository.findByPackageId(packageId);
        if (entityOpt.isEmpty()) {
            throw new RuntimeException("Package not found: " + packageId);
        }

        CollaborationPackageEntity entity = entityOpt.get();
        String currentStatus = entity.getStatus();

        if (!STATUS_EXECUTING.equals(currentStatus) && !STATUS_PLANNING.equals(currentStatus)) {
            throw new IllegalStateException("Only EXECUTING or PLANNING packages can be paused, current: " + currentStatus);
        }

        entity.setStatus(STATUS_PAUSED);
        log.info("Collaboration package paused: {}", packageId);

        eventBus.publishPackageStatusChanged(packageId, STATUS_PAUSED, entity.getTraceId());
        recordAuditLog(entity, "PAUSED", null, null);

        return toDto(packageRepository.save(entity));
    }

    /**
     * 恢复协作任务包
     */
    @Transactional
    public CollaborationPackage resume(String packageId) {
        Optional<CollaborationPackageEntity> entityOpt = packageRepository.findByPackageId(packageId);
        if (entityOpt.isEmpty()) {
            throw new RuntimeException("Package not found: " + packageId);
        }

        CollaborationPackageEntity entity = entityOpt.get();
        String currentStatus = entity.getStatus();

        if (!STATUS_PAUSED.equals(currentStatus)) {
            throw new IllegalStateException("Only PAUSED packages can be resumed, current: " + currentStatus);
        }

        entity.setStatus(STATUS_EXECUTING);
        log.info("Collaboration package resumed: {}", packageId);

        eventBus.publishPackageStatusChanged(packageId, STATUS_EXECUTING, entity.getTraceId());
        recordAuditLog(entity, "RESUMED", null, null);

        return toDto(packageRepository.save(entity));
    }

    /**
     * 取消协作任务包
     */
    @Transactional
    public CollaborationPackage cancel(String packageId) {
        Optional<CollaborationPackageEntity> entityOpt = packageRepository.findByPackageId(packageId);
        if (entityOpt.isEmpty()) {
            throw new RuntimeException("Package not found: " + packageId);
        }

        CollaborationPackageEntity entity = entityOpt.get();
        String currentStatus = entity.getStatus();

        if (STATUS_COMPLETED.equals(currentStatus) || STATUS_FAILED.equals(currentStatus) || STATUS_CANCELLED.equals(currentStatus)) {
            throw new IllegalStateException("Cannot cancel package in terminal state: " + currentStatus);
        }

        entity.setStatus(STATUS_CANCELLED);
        log.info("Collaboration package cancelled: {}", packageId);

        // 取消所有 PENDING/RUNNING 的子任务
        List<CollabSubtaskEntity> subtasks = subtaskRepository.findByPackageId(packageId);
        for (CollabSubtaskEntity subtask : subtasks) {
            if ("PENDING".equals(subtask.getStatus()) || "RUNNING".equals(subtask.getStatus())) {
                subtask.setStatus(SUBTASK_CANCELLED);
                subtask.setCompletedAt(LocalDateTime.now());
            }
        }
        subtaskRepository.saveAll(subtasks);

        eventBus.publishPackageStatusChanged(packageId, STATUS_CANCELLED, entity.getTraceId());
        recordAuditLog(entity, "CANCELLED", null, null);

        return toDto(packageRepository.save(entity));
    }

    /**
     * 获取运行时状态（包含包状态、子任务进度、执行统计）
     */
    public Map<String, Object> getRuntimeStatus(String packageId) {
        Optional<CollaborationPackageEntity> entityOpt = packageRepository.findByPackageId(packageId);
        if (entityOpt.isEmpty()) {
            throw new RuntimeException("Package not found: " + packageId);
        }

        CollaborationPackageEntity entity = entityOpt.get();
        List<CollabSubtaskEntity> subtasks = subtaskRepository.findByPackageId(packageId);

        long pendingCount = subtasks.stream().filter(s -> "PENDING".equals(s.getStatus())).count();
        long runningCount = subtasks.stream().filter(s -> "RUNNING".equals(s.getStatus())).count();
        long completedCount = subtasks.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
        long failedCount = subtasks.stream().filter(s -> "FAILED".equals(s.getStatus())).count();
        long skippedCount = subtasks.stream().filter(s -> "SKIPPED".equals(s.getStatus())).count();
        long cancelledCount = subtasks.stream().filter(s -> "CANCELLED".equals(s.getStatus())).count();

        Map<String, Object> stats = memoryService.getPackageStats(packageId);

        Map<String, Object> runtime = new HashMap<>();
        runtime.put("packageId", packageId);
        runtime.put("status", entity.getStatus());
        runtime.put("intent", entity.getIntent() != null ? entity.getIntent() : "");
        runtime.put("collaborationMode", entity.getCollaborationMode() != null ? entity.getCollaborationMode() : "");
        runtime.put("progress", Map.of(
                "total", subtasks.size(),
                "pending", pendingCount,
                "running", runningCount,
                "completed", completedCount,
                "failed", failedCount,
                "skipped", skippedCount,
                "cancelled", cancelledCount
        ));
        runtime.put("executionStats", stats);
        runtime.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : "");
        runtime.put("timeoutAt", entity.getTimeoutAt() != null ? entity.getTimeoutAt().toString() : "");
        runtime.put("traceId", entity.getTraceId() != null ? entity.getTraceId() : "");

        memoryService.readFromBlackboard(packageId, "selection_last")
                .ifPresent(selection -> runtime.put("selection", selection));

        return runtime;
    }

    /**
     * 人工接管子任务
     */
    @Transactional
    public CollabSubtaskEntity manuallyHandleSubtask(String packageId, String subTaskId, String handlerInput) {
        Optional<CollabSubtaskEntity> subtaskOpt = subtaskRepository.findByPackageIdAndSubTaskId(packageId, subTaskId);
        if (subtaskOpt.isEmpty()) {
            throw new RuntimeException("Subtask not found: " + subTaskId);
        }

        CollabSubtaskEntity subtask = subtaskOpt.get();

        if (!"PENDING".equals(subtask.getStatus()) && !"RUNNING".equals(subtask.getStatus())) {
            throw new IllegalStateException("Only PENDING or RUNNING subtasks can be manually handled");
        }

        subtask.setStatus("MANUAL_HANDLING");
        subtask.setResult("Manually handled by user: " + handlerInput);
        subtask.setCompletedAt(LocalDateTime.now());

        log.info("Subtask manually handled: {} in package: {}", subTaskId, packageId);

        eventBus.publishSubtaskManuallyHandled(packageId, subTaskId, handlerInput, getTraceId(packageId));

        return subtaskRepository.save(subtask);
    }

    /**
     * 依赖驱动自动调度 - 子任务完成后自动触发满足依赖的后续子任务
     *
     * @param packageId 任务包 ID
     * @return 触发调度的子任务列表（subTaskId 列表）
     */
    public List<CollabSubtaskEntity> autoScheduleIfPossible(String packageId) {
        Optional<CollaborationPackageEntity> entityOpt = packageRepository.findByPackageId(packageId);
        if (entityOpt.isEmpty()) {
            return Collections.emptyList();
        }

        CollaborationPackageEntity entity = entityOpt.get();
        String collaborationMode = entity.getCollaborationMode();

        List<CollabSubtaskEntity> executable = getExecutableSubtasks(packageId);
        if (executable.isEmpty()) {
            return Collections.emptyList();
        }

        List<CollabSubtaskEntity> toExecute;

        if (MODE_PARALLEL.equals(collaborationMode)) {
            // PARALLEL 模式：执行所有可执行的子任务
            toExecute = executable;
        } else {
            // SEQUENTIAL 模式（默认）：只执行最靠前的子任务
            toExecute = List.of(executable.get(0));
        }

        // 更新状态为 RUNNING
        for (CollabSubtaskEntity subtask : toExecute) {
            try {
                updateSubtaskStatus(packageId, subtask.getSubTaskId(), SUBTASK_RUNNING, null, null);
            } catch (IllegalStateException e) {
                // 状态已被其他调度更改，跳过
                log.warn("Skipping subtask {} due to status conflict: {}", subtask.getSubTaskId(), e.getMessage());
            }
        }

        log.info("Auto-scheduled {} subtasks for package {} (mode={})", toExecute.size(), packageId, collaborationMode);
        return toExecute;
    }

    /**
     * 清理协作任务资源
     */
    public void cleanup(String packageId) {
        memoryService.cleanupPackage(packageId);
    }

    /**
     * 获取协作包统计信息
     */
    public Map<String, Object> getPackageStats(String packageId) {
        return memoryService.getPackageStats(packageId);
    }

    /**
     * 获取任务包详情（包含子任务）
     */
    public Optional<CollaborationPackage> getPackage(String packageId) {
        return packageRepository.findByPackageId(packageId)
                .map(entity -> toDto(entity, subtaskRepository.findByPackageId(packageId)));
    }

    /**
     * 获取所有任务包（包含子任务）
     */
    public List<CollaborationPackage> getAllPackages() {
        return packageRepository.findAll().stream()
                .map(entity -> toDto(entity, subtaskRepository.findByPackageId(entity.getPackageId())))
                .collect(Collectors.toList());
    }

    /**
     * 获取用户的任务包
     */
    public List<CollaborationPackage> getPackagesByUser(String createdBy) {
        return packageRepository.findByCreatedByOrderByCreatedAtDesc(createdBy)
                .stream()
                .map(entity -> toDto(entity, subtaskRepository.findByPackageId(entity.getPackageId())))
                .collect(Collectors.toList());
    }

    /**
     * 筛选任务包
     */
    public List<CollaborationPackage> filterPackages(String status, String createdBy, String priority, String category) {
        List<CollaborationPackageEntity> entities;

        if (status != null && createdBy != null) {
            entities = packageRepository.findByStatusAndCreatedBy(status, createdBy);
        } else if (priority != null && createdBy != null) {
            entities = packageRepository.findByIntentPriorityAndCreatedBy(priority, createdBy);
        } else if (status != null) {
            entities = packageRepository.findByStatus(status);
        } else if (priority != null) {
            entities = packageRepository.findByIntentPriority(priority);
        } else if (category != null) {
            entities = packageRepository.findByIntentCategory(category);
        } else if (createdBy != null) {
            entities = packageRepository.findByCreatedByOrderByCreatedAtDesc(createdBy);
        } else {
            entities = packageRepository.findAll();
        }

        return entities.stream()
                .map(entity -> toDto(entity, subtaskRepository.findByPackageId(entity.getPackageId())))
                .collect(Collectors.toList());
    }

    /**
     * 获取任务包的子任务列表
     */
    public List<CollabSubtaskEntity> getSubtasks(String packageId) {
        return subtaskRepository.findByPackageId(packageId);
    }

    // ========== 辅助方法 ==========

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize to JSON", e);
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON", e);
            return null;
        }
    }

    private List<String> fromJsonList(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /**
     * 校验子任务状态转换是否合法
     */
    private boolean isValidStatusTransition(String currentStatus, String newStatus) {
        if (currentStatus == null || newStatus == null) {
            return false;
        }
        Set<String> allowedTransitions = SUBTASK_STATUS_TRANSITIONS.get(currentStatus);
        return allowedTransitions != null && allowedTransitions.contains(newStatus);
    }

    private CollaborationPackage toDto(CollaborationPackageEntity entity) {
        return toDto(entity, Collections.emptyList());
    }

    private CollaborationPackage toDto(CollaborationPackageEntity entity, List<CollabSubtaskEntity> subtasks) {
        CollaborationPackage.ExecutionStrategy strategy = null;
        if (entity.getStrategy() != null && !entity.getStrategy().isBlank()) {
            strategy = fromJson(entity.getStrategy(), CollaborationPackage.ExecutionStrategy.class);
        }
        return CollaborationPackage.builder()
                .packageId(entity.getPackageId())
                .rootTaskId(entity.getRootTaskId())
                .intent(entity.getIntent())
                .intentTag(CollaborationPackage.IntentTag.builder()
                        .category(entity.getIntentCategory())
                        .priority(entity.getIntentPriority())
                        .complexity(entity.getIntentComplexity())
                        .needReview(entity.getNeedReview())
                        .needConsensus(entity.getNeedConsensus())
                        .build())
                .collaborationMode(entity.getCollaborationMode())
                .strategy(strategy)
                .status(entity.getStatus())
                .result(entity.getResult())
                .errorMessage(entity.getErrorMessage())
                .traceId(entity.getTraceId())
                .createdAt(entity.getCreatedAt())
                .timeoutAt(entity.getTimeoutAt())
                .build();
    }

    private String parseString(Map<String, Object> source, String key, String defaultValue) {
        if (source == null) {
            return defaultValue;
        }
        Object val = source.get(key);
        if (val == null) {
            return defaultValue;
        }
        String value = String.valueOf(val).trim();
        return value.isEmpty() ? defaultValue : value;
    }

    private int parseInt(Map<String, Object> source, String key, int defaultValue) {
        if (source == null || source.get(key) == null) {
            return defaultValue;
        }
        Object val = source.get(key);
        if (val instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double parseDouble(Map<String, Object> source, String key, double defaultValue) {
        if (source == null || source.get(key) == null) {
            return defaultValue;
        }
        Object val = source.get(key);
        if (val instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(val));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseList(Map<String, Object> source, String key) {
        if (source == null || source.get(key) == null) {
            return Collections.emptyList();
        }
        Object val = source.get(key);
        if (val instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toList());
        }
        String raw = String.valueOf(val);
        if (raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }
}
