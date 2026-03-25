package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.modules.collaboration.dto.CollaborationPackage;
import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.entity.CollaborationPackageEntity;
import com.adlin.orin.modules.collaboration.event.CollaborationEventBus;
import com.adlin.orin.modules.collaboration.repository.CollabSubtaskRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
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

    // 任务状态
    public static final String STATUS_PLANNING = "PLANNING";
    public static final String STATUS_DECOMPOSING = "DECOMPOSING";
    public static final String STATUS_EXECUTING = "EXECUTING";
    public static final String STATUS_CONSENSUS = "CONSENSUS";
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
        String packageId = UUID.randomUUID().toString().replace("-", "");

        // 构建意图标签
        CollaborationPackage.IntentTag intentTag = CollaborationPackage.IntentTag.builder()
                .category(category)
                .priority(priority)
                .complexity(complexity)
                .needReview("HIGH".equals(priority) || "URGENT".equals(priority))
                .needConsensus("COMPLEX".equals(complexity) || "VERY_COMPLEX".equals(complexity))
                .build();

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
     */
    @Transactional
    public CollabSubtaskEntity updateSubtaskStatus(String packageId, String subTaskId, String status,
                                                    String result, String errorMessage) {
        Optional<CollabSubtaskEntity> subtaskOpt = subtaskRepository.findByPackageIdAndSubTaskId(packageId, subTaskId);

        if (subtaskOpt.isEmpty()) {
            throw new RuntimeException("Subtask not found: " + subTaskId);
        }

        CollabSubtaskEntity subtask = subtaskOpt.get();
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

        return toDto(packageRepository.save(entity));
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

        return toDto(packageRepository.save(entity));
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
     * 获取任务包
     */
    public Optional<CollaborationPackage> getPackage(String packageId) {
        return packageRepository.findByPackageId(packageId).map(this::toDto);
    }

    /**
     * 获取所有任务包
     */
    public List<CollaborationPackage> getAllPackages() {
        return packageRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * 获取用户的任务包
     */
    public List<CollaborationPackage> getPackagesByUser(String createdBy) {
        return packageRepository.findByCreatedByOrderByCreatedAtDesc(createdBy)
                .stream().map(this::toDto).collect(Collectors.toList());
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

    private CollaborationPackage toDto(CollaborationPackageEntity entity) {
        return toDto(entity, Collections.emptyList());
    }

    private CollaborationPackage toDto(CollaborationPackageEntity entity, List<CollabSubtaskEntity> subtasks) {
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
                .status(entity.getStatus())
                .result(entity.getResult())
                .errorMessage(entity.getErrorMessage())
                .traceId(entity.getTraceId())
                .createdAt(entity.getCreatedAt())
                .timeoutAt(entity.getTimeoutAt())
                .build();
    }
}