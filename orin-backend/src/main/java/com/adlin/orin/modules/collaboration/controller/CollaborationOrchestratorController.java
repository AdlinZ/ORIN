package com.adlin.orin.modules.collaboration.controller;

import com.adlin.orin.modules.collaboration.dto.CollaborationPackage;
import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.consumer.CollaborationResultListener;
import com.adlin.orin.modules.collaboration.event.CollaborationEvent;
import com.adlin.orin.modules.collaboration.event.CollaborationEventBus;
import com.adlin.orin.modules.collaboration.metrics.CollaborationMetricsService;
import com.adlin.orin.modules.collaboration.service.CollaborationExecutor;
import com.adlin.orin.modules.collaboration.service.CollaborationMemoryService;
import com.adlin.orin.modules.collaboration.service.CollaborationOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 多智能体协作编排 API
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/collaboration")
@Tag(name = "Collaboration Orchestrator", description = "多智能体协作编排API")
public class CollaborationOrchestratorController {

    private final CollaborationOrchestrator orchestrator;
    private final CollaborationEventBus eventBus;
    private final CollaborationExecutor executor;
    private final CollaborationMemoryService memoryService;
    private final CollaborationMetricsService metricsService;
    private final CollaborationResultListener resultListener;

    @Autowired
    public CollaborationOrchestratorController(
            CollaborationOrchestrator orchestrator,
            CollaborationEventBus eventBus,
            CollaborationExecutor executor,
            CollaborationMemoryService memoryService,
            CollaborationMetricsService metricsService,
            CollaborationResultListener resultListener) {
        this.orchestrator = orchestrator;
        this.eventBus = eventBus;
        this.executor = executor;
        this.memoryService = memoryService;
        this.metricsService = metricsService;
        this.resultListener = resultListener;
    }

    public CollaborationOrchestratorController(
            CollaborationOrchestrator orchestrator,
            CollaborationEventBus eventBus,
            CollaborationExecutor executor,
            CollaborationMemoryService memoryService,
            CollaborationMetricsService metricsService) {
        this(orchestrator, eventBus, executor, memoryService, metricsService, null);
    }

    @Operation(summary = "创建协作任务包")
    @PostMapping("/packages")
    public ResponseEntity<CollaborationPackage> createPackage(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {

        String intent = (String) request.get("intent");
        String category = (String) request.getOrDefault("category", "GENERAL");
        String priority = (String) request.getOrDefault("priority", "NORMAL");
        String complexity = (String) request.getOrDefault("complexity", "SIMPLE");
        String mode = (String) request.getOrDefault("collaborationMode", "SEQUENTIAL");
        Map<String, Object> strategyOverrides = new HashMap<>();
        copyIfPresent(request, strategyOverrides, "mainAgentPolicy");
        copyIfPresent(request, strategyOverrides, "qualityThreshold");
        copyIfPresent(request, strategyOverrides, "maxCritiqueRounds");
        copyIfPresent(request, strategyOverrides, "draftParallelism");
        copyIfPresent(request, strategyOverrides, "mainAgentStaticDefault");
        copyIfPresent(request, strategyOverrides, "staticMainAgent");
        copyIfPresent(request, strategyOverrides, "bidWhitelist");
        copyIfPresent(request, strategyOverrides, "bidWeightReasoning");
        copyIfPresent(request, strategyOverrides, "bidWeightSpeed");
        copyIfPresent(request, strategyOverrides, "bidWeightCost");

        CollaborationPackage pkg = orchestrator.createPackage(
                intent, category, priority, complexity, mode, userId, traceId, strategyOverrides
        );
        return ResponseEntity.ok(pkg);
    }

    @Operation(summary = "分解任务包为子任务")
    @PostMapping("/packages/{packageId}/decompose")
    public ResponseEntity<CollaborationPackage> decompose(
            @PathVariable String packageId,
            @RequestBody(required = false) Map<String, Object> request) {

        @SuppressWarnings("unchecked")
        List<String> capabilities = request != null && request.containsKey("capabilities")
                ? (List<String>) request.get("capabilities")
                : List.of("analysis", "generation", "review");

        CollaborationPackage pkg = orchestrator.decompose(packageId, capabilities);
        return ResponseEntity.ok(pkg);
    }

    @Operation(summary = "获取可执行的子任务")
    @GetMapping("/packages/{packageId}/executable")
    public ResponseEntity<List<CollabSubtaskEntity>> getExecutableSubtasks(@PathVariable String packageId) {
        List<CollabSubtaskEntity> subtasks = orchestrator.getExecutableSubtasks(packageId);
        return ResponseEntity.ok(subtasks);
    }

    @Operation(summary = "更新子任务状态")
    @PutMapping("/packages/{packageId}/subtasks/{subTaskId}/status")
    public ResponseEntity<CollabSubtaskEntity> updateSubtaskStatus(
            @PathVariable String packageId,
            @PathVariable String subTaskId,
            @RequestBody Map<String, String> request) {

        String status = request.get("status");
        String result = request.get("result");
        String errorMessage = request.get("errorMessage");

        CollabSubtaskEntity subtask = orchestrator.updateSubtaskStatus(packageId, subTaskId, status, result, errorMessage);
        return ResponseEntity.ok(subtask);
    }

    @Operation(summary = "检查协作任务是否完成")
    @GetMapping("/packages/{packageId}/completed")
    public ResponseEntity<Map<String, Boolean>> checkCompleted(@PathVariable String packageId) {
        boolean allDone = orchestrator.isAllSubtasksCompleted(packageId);
        boolean hasFailed = orchestrator.hasFailedSubtask(packageId);
        return ResponseEntity.ok(Map.of("allCompleted", allDone, "hasFailed", hasFailed));
    }

    @Operation(summary = "触发回退策略")
    @PostMapping("/packages/{packageId}/fallback")
    public ResponseEntity<CollaborationPackage> triggerFallback(
            @PathVariable String packageId,
            @RequestBody Map<String, String> request) {

        String reason = request.get("reason");
        CollaborationPackage pkg = orchestrator.executeFallback(packageId, reason);
        return ResponseEntity.ok(pkg);
    }

    @Operation(summary = "完成协作任务")
    @PostMapping("/packages/{packageId}/complete")
    public ResponseEntity<CollaborationPackage> complete(
            @PathVariable String packageId,
            @RequestBody Map<String, String> request) {

        String result = request.get("result");
        CollaborationPackage pkg = orchestrator.complete(packageId, result);
        return ResponseEntity.ok(pkg);
    }

    @Operation(summary = "标记任务失败")
    @PostMapping("/packages/{packageId}/fail")
    public ResponseEntity<CollaborationPackage> fail(
            @PathVariable String packageId,
            @RequestBody Map<String, String> request) {

        String errorMessage = request.get("errorMessage");
        CollaborationPackage pkg = orchestrator.fail(packageId, errorMessage);
        return ResponseEntity.ok(pkg);
    }

    @Operation(summary = "获取任务包详情")
    @GetMapping("/packages/{packageId}")
    public ResponseEntity<CollaborationPackage> getPackage(@PathVariable String packageId) {
        return orchestrator.getPackage(packageId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "获取所有任务包")
    @GetMapping("/packages")
    public ResponseEntity<List<CollaborationPackage>> getAllPackages() {
        return ResponseEntity.ok(orchestrator.getAllPackages());
    }

    @Operation(summary = "获取用户的任务包")
    @GetMapping("/packages/user")
    public ResponseEntity<List<CollaborationPackage>> getMyPackages(
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ResponseEntity.ok(orchestrator.getPackagesByUser(userId));
    }

    @Operation(summary = "获取协作统计")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCollaborationStats() {
        List<CollaborationPackage> allPackages = orchestrator.getAllPackages();
        long total = allPackages.size();
        long planning = allPackages.stream().filter(p -> "PLANNING".equals(p.getStatus())).count();
        long decomposing = allPackages.stream().filter(p -> "DECOMPOSING".equals(p.getStatus())).count();
        long executing = allPackages.stream().filter(p -> "EXECUTING".equals(p.getStatus())).count();
        long paused = allPackages.stream().filter(p -> "PAUSED".equals(p.getStatus())).count();
        long completed = allPackages.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
        long failed = allPackages.stream().filter(p -> "FAILED".equals(p.getStatus())).count();
        long cancelled = allPackages.stream().filter(p -> "CANCELLED".equals(p.getStatus())).count();

        // 基于协作指标服务做聚合
        Map<String, CollaborationMetricsService.AgentMetrics> allAgentMetrics = metricsService.getAllAgentMetrics();
        long activeAgents = executor.getAvailableAgents() != null ? executor.getAvailableAgents().size() : 0;

        long todayTokens = allAgentMetrics.values().stream()
                .mapToLong(CollaborationMetricsService.AgentMetrics::getTotalTokens)
                .sum();

        long totalRequests = allAgentMetrics.values().stream()
                .mapToLong(CollaborationMetricsService.AgentMetrics::getTotalRequests)
                .sum();
        long successRequests = allAgentMetrics.values().stream()
                .mapToLong(CollaborationMetricsService.AgentMetrics::getSuccessCount)
                .sum();
        long totalLatencyMs = allAgentMetrics.values().stream()
                .mapToLong(CollaborationMetricsService.AgentMetrics::getTotalLatencyMs)
                .sum();

        double avgLatency = totalRequests > 0 ? (double) totalLatencyMs / totalRequests : 0.0;
        double successRate = totalRequests > 0 ? ((double) successRequests / totalRequests) * 100.0 : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("planning", planning);
        stats.put("decomposing", decomposing);
        stats.put("executing", executing);
        stats.put("paused", paused);
        stats.put("completed", completed);
        stats.put("failed", failed);
        stats.put("cancelled", cancelled);
        stats.put("activeAgents", activeAgents);
        stats.put("todayTokens", todayTokens);
        stats.put("avgLatency", Math.round(avgLatency * 100.0) / 100.0);
        stats.put("successRate", Math.round(successRate * 100.0) / 100.0);

        return ResponseEntity.ok(stats);
    }

    @Operation(summary = "筛选任务包")
    @GetMapping("/packages/filter")
    public ResponseEntity<List<CollaborationPackage>> filterPackages(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(orchestrator.filterPackages(status, createdBy, priority, category));
    }

    @Operation(summary = "获取任务包的子任务列表")
    @GetMapping("/packages/{packageId}/subtasks")
    public ResponseEntity<List<CollabSubtaskEntity>> getSubtasks(@PathVariable String packageId) {
        return ResponseEntity.ok(orchestrator.getSubtasks(packageId));
    }

    @Operation(summary = "获取协作事件历史")
    @GetMapping("/events/{packageId}")
    public ResponseEntity<List<CollaborationEvent>> getEventHistory(@PathVariable String packageId) {
        return ResponseEntity.ok(eventBus.getEventHistory(packageId));
    }

    // ==================== 检查点管理接口 ====================

    @Operation(summary = "获取黑板结构")
    @GetMapping("/packages/{packageId}/blackboard")
    public ResponseEntity<Map<String, Object>> getBlackboard(@PathVariable String packageId) {
        return ResponseEntity.ok(memoryService.getBlackboardStructure(packageId));
    }

    @Operation(summary = "保存检查点")
    @PostMapping("/packages/{packageId}/checkpoints")
    public ResponseEntity<Map<String, Object>> saveCheckpoint(
            @PathVariable String packageId,
            @RequestBody Map<String, Object> request) {

        String checkpointId = (String) request.get("checkpointId");
        Object dataObj = request.get("data");
        Map<String, Object> data = dataObj instanceof Map ? (Map<String, Object>) dataObj : Collections.emptyMap();
        memoryService.saveCheckpoint(packageId, checkpointId, data);

        return ResponseEntity.ok(Map.of("status", "saved", "checkpointId", checkpointId));
    }

    @Operation(summary = "获取检查点")
    @GetMapping("/packages/{packageId}/checkpoints/{checkpointId}")
    public ResponseEntity<Map<String, Object>> getCheckpoint(
            @PathVariable String packageId,
            @PathVariable String checkpointId) {

        return memoryService.readCheckpoint(packageId, checkpointId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "列出所有检查点")
    @GetMapping("/packages/{packageId}/checkpoints")
    public ResponseEntity<List<String>> listCheckpoints(@PathVariable String packageId) {
        return ResponseEntity.ok(memoryService.listCheckpoints(packageId));
    }

    @Operation(summary = "回滚到检查点")
    @PostMapping("/packages/{packageId}/checkpoints/{checkpointId}/rollback")
    public ResponseEntity<Map<String, Object>> rollbackToCheckpoint(
            @PathVariable String packageId,
            @PathVariable String checkpointId) {

        return memoryService.rollbackToCheckpoint(packageId, checkpointId)
                .map(data -> ResponseEntity.ok(Map.of("status", "rolled_back", "checkpointId", checkpointId, "data", data)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "删除检查点")
    @DeleteMapping("/packages/{packageId}/checkpoints/{checkpointId}")
    public ResponseEntity<Map<String, Object>> deleteCheckpoint(
            @PathVariable String packageId,
            @PathVariable String checkpointId) {

        memoryService.deleteCheckpoint(packageId, checkpointId);
        return ResponseEntity.ok(Map.of("status", "deleted", "checkpointId", checkpointId));
    }

    // ==================== 运行时状态与控制接口 ====================

    @Operation(summary = "获取运行时状态")
    @GetMapping("/packages/{packageId}/runtime")
    public ResponseEntity<Map<String, Object>> getRuntimeStatus(@PathVariable String packageId) {
        try {
            Map<String, Object> status = orchestrator.getRuntimeStatus(packageId);
            status.put("pendingCallbacks", resultListener != null ? resultListener.getPendingCallbackCount() : 0);
            return ResponseEntity.ok(status);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "获取协作诊断状态")
    @GetMapping("/packages/{packageId}/diagnostics")
    public ResponseEntity<Map<String, Object>> getDiagnostics(@PathVariable String packageId) {
        try {
            Map<String, Object> runtime = orchestrator.getRuntimeStatus(packageId);
            List<Map<String, Object>> failedSubtasks = orchestrator.getSubtasks(packageId).stream()
                    .filter(subtask -> "FAILED".equals(subtask.getStatus()))
                    .map(subtask -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("packageId", packageId);
                        item.put("subTaskId", subtask.getSubTaskId());
                        item.put("attempt", subtask.getRetryCount() != null ? subtask.getRetryCount() : 0);
                        item.put("errorMessage", subtask.getErrorMessage() != null ? subtask.getErrorMessage() : "");
                        runtime.getOrDefault("traceId", "");
                        item.put("traceId", runtime.getOrDefault("traceId", ""));
                        return item;
                    })
                    .toList();
            Map<String, Object> diagnostics = new HashMap<>();
            diagnostics.put("runtime", runtime);
            diagnostics.put("pendingCallbacks", resultListener != null ? resultListener.getPendingCallbackCount() : 0);
            diagnostics.put("failedSubtasks", failedSubtasks);
            return ResponseEntity.ok(diagnostics);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "暂停协作任务")
    @PostMapping("/packages/{packageId}/pause")
    public ResponseEntity<CollaborationPackage> pauseCollaboration(@PathVariable String packageId) {
        try {
            CollaborationPackage pkg = orchestrator.pause(packageId);
            return ResponseEntity.ok(pkg);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "恢复协作任务")
    @PostMapping("/packages/{packageId}/resume")
    public ResponseEntity<CollaborationPackage> resumeCollaboration(@PathVariable String packageId) {
        try {
            CollaborationPackage pkg = orchestrator.resume(packageId);
            return ResponseEntity.ok(pkg);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "取消协作任务")
    @PostMapping("/packages/{packageId}/cancel")
    public ResponseEntity<CollaborationPackage> cancelCollaboration(@PathVariable String packageId) {
        try {
            CollaborationPackage pkg = orchestrator.cancel(packageId);
            return ResponseEntity.ok(pkg);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== 运行时执行接口 ====================

    @Operation(summary = "启动子任务执行")
    @PostMapping("/packages/{packageId}/subtasks/{subTaskId}/execute")
    public ResponseEntity<Map<String, Object>> executeSubtask(
            @PathVariable String packageId,
            @PathVariable String subTaskId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-Orchestrator-Mode", required = false) String orchestratorMode) {

        // 获取子任务
        List<CollabSubtaskEntity> subtasks = orchestrator.getSubtasks(packageId);
        CollabSubtaskEntity subtask = subtasks.stream()
                .filter(s -> s.getSubTaskId().equals(subTaskId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Subtask not found: " + subTaskId));

        // 检查状态是否为 PENDING
        if (!"PENDING".equals(subtask.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Subtask status is not PENDING",
                    "currentStatus", subtask.getStatus()
            ));
        }

        // 更新状态为 RUNNING
        orchestrator.updateSubtaskStatus(packageId, subTaskId, "RUNNING", null, null);

        boolean langGraphOrchestrator = "LANGGRAPH".equalsIgnoreCase(orchestratorMode);

        // 异步执行
        executor.executeSubtask(subtask, packageId, traceId).thenAccept(result -> {
            // 执行完成后更新状态（仅在 RUNNING 状态下推进，避免 MQ 回调与 Controller 回调重复更新）
            safeUpdateSubtaskStatus(packageId, subTaskId, "COMPLETED", result, null);

            // 依赖驱动自动调度：LangGraph 编排模式下由 Python 侧推进，避免双编排撞车。
            if (!langGraphOrchestrator) {
                scheduleNextSubtasks(packageId, traceId);
            }
        }).exceptionally(e -> {
            log.error("Subtask execution failed: {}", subTaskId, e);
            // 仅在 RUNNING 状态下推进，避免重复失败流转
            safeUpdateSubtaskStatus(packageId, subTaskId, "FAILED", null, e.getMessage());
            // 失败后也尝试调度（可能有依赖该失败任务的替代路径）。
            // LangGraph 编排模式下由 Python 侧决定下一步。
            if (!langGraphOrchestrator) {
                scheduleNextSubtasks(packageId, traceId);
            }
            return null;
        });

        return ResponseEntity.accepted().body(Map.of(
                "status", "STARTED",
                "subTaskId", subTaskId,
                "correlationId", packageId + ":" + subTaskId,
                "message", "Subtask execution started"
        ));
    }

    @Operation(summary = "重试子任务")
    @PostMapping("/packages/{packageId}/subtasks/{subTaskId}/retry")
    public ResponseEntity<Map<String, Object>> retrySubtask(
            @PathVariable String packageId,
            @PathVariable String subTaskId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {

        List<CollabSubtaskEntity> subtasks = orchestrator.getSubtasks(packageId);
        CollabSubtaskEntity subtask = subtasks.stream()
                .filter(s -> s.getSubTaskId().equals(subTaskId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Subtask not found: " + subTaskId));

        // 检查状态是否为 FAILED
        if (!"FAILED".equals(subtask.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Only FAILED subtasks can be retried",
                    "currentStatus", subtask.getStatus()
            ));
        }

        // 重置为 PENDING
        orchestrator.updateSubtaskStatus(packageId, subTaskId, "PENDING", null, null);

        // 异步重试执行
        executor.retrySubtask(subtask, packageId, traceId).thenAccept(result -> {
            safeUpdateSubtaskStatus(packageId, subTaskId, "COMPLETED", result, null);
            // 依赖驱动自动调度
            scheduleNextSubtasks(packageId, traceId);
        }).exceptionally(e -> {
            log.error("Subtask retry failed: {}", subTaskId, e);
            safeUpdateSubtaskStatus(packageId, subTaskId, "FAILED", null, e.getMessage());
            scheduleNextSubtasks(packageId, traceId);
            return null;
        });

        return ResponseEntity.accepted().body(Map.of(
                "status", "RETRY_STARTED",
                "subTaskId", subTaskId,
                "retryCount", subtask.getRetryCount() + 1
        ));
    }

    // ==================== 人工干预接口 ====================

    @Operation(summary = "跳过子任务")
    @PostMapping("/packages/{packageId}/subtasks/{subTaskId}/skip")
    public ResponseEntity<CollabSubtaskEntity> skipSubtask(
            @PathVariable String packageId,
            @PathVariable String subTaskId) {

        List<CollabSubtaskEntity> subtasks = orchestrator.getSubtasks(packageId);
        CollabSubtaskEntity subtask = subtasks.stream()
                .filter(s -> s.getSubTaskId().equals(subTaskId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Subtask not found: " + subTaskId));

        if (!"PENDING".equals(subtask.getStatus()) && !"FAILED".equals(subtask.getStatus())) {
            throw new IllegalStateException("Only PENDING or FAILED subtasks can be skipped");
        }

        return ResponseEntity.ok(orchestrator.updateSubtaskStatus(packageId, subTaskId, "SKIPPED", null, "Skipped by user"));
    }

    @Operation(summary = "手动写入子任务结果")
    @PostMapping("/packages/{packageId}/subtasks/{subTaskId}/manual-complete")
    public ResponseEntity<CollabSubtaskEntity> manualComplete(
            @PathVariable String packageId,
            @PathVariable String subTaskId,
            @RequestBody Map<String, String> request) {

        String result = request.get("result");

        // 完成等待中的人工任务（如果存在）
        executor.completeHumanTask(packageId, subTaskId, result);

        return ResponseEntity.ok(orchestrator.updateSubtaskStatus(packageId, subTaskId, "COMPLETED", result, null));
    }

    @Operation(summary = "手动完成协作包")
    @PostMapping("/packages/{packageId}/manual-complete")
    public ResponseEntity<CollaborationPackage> manualCompletePackage(
            @PathVariable String packageId,
            @RequestBody Map<String, String> request) {

        String result = request.get("result");
        return ResponseEntity.ok(orchestrator.complete(packageId, result));
    }

    @Operation(summary = "人工接管子任务")
    @PostMapping("/packages/{packageId}/subtasks/{subTaskId}/manual")
    public ResponseEntity<CollabSubtaskEntity> manuallyHandleSubtask(
            @PathVariable String packageId,
            @PathVariable String subTaskId,
            @RequestBody Map<String, String> request) {

        String handlerInput = request.get("handlerInput");
        try {
            CollabSubtaskEntity subtask = orchestrator.manuallyHandleSubtask(packageId, subTaskId, handlerInput);
            return ResponseEntity.ok(subtask);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== 指标查询接口 ====================

    @Operation(summary = "获取 Agent 指标")
    @GetMapping("/metrics/agent/{agentId}")
    public ResponseEntity<CollaborationMetricsService.AgentMetrics> getAgentMetrics(@PathVariable String agentId) {
        return ResponseEntity.ok(metricsService.getAgentMetrics(agentId));
    }

    @Operation(summary = "获取降级建议")
    @GetMapping("/metrics/suggestion")
    public ResponseEntity<Map<String, Object>> getDegradationSuggestion(
            @RequestParam String agentId,
            @RequestParam(required = false, defaultValue = "10.0") Double budgetThreshold,
            @RequestParam(required = false, defaultValue = "5000") Long latencyThresholdMs) {

        String suggestion = metricsService.getDegradationSuggestion(agentId, budgetThreshold, latencyThresholdMs);
        CollaborationMetricsService.AgentMetrics metrics = metricsService.getAgentMetrics(agentId);

        return ResponseEntity.ok(Map.of(
                "agentId", agentId,
                "suggestion", suggestion,
                "metrics", Map.of(
                        "totalTokens", metrics.getTotalTokens(),
                        "totalCost", metrics.getTotalCost(),
                        "successRate", metrics.getSuccessRate(),
                        "averageLatencyMs", metrics.getAverageLatencyMs(),
                        "totalRequests", metrics.getTotalRequests()
                )
        ));
    }

    /**
     * 检查并完成协作包
     */
    private void checkAndCompletePackage(String packageId) {
        if (orchestrator.isAllSubtasksCompleted(packageId)) {
            orchestrator.complete(packageId, "All subtasks completed");
        }
    }

    /**
     * 依赖驱动自动调度 - 查询可执行子任务并异步执行
     */
    private void scheduleNextSubtasks(String packageId, String traceId) {
        try {
            List<CollabSubtaskEntity> toSchedule = orchestrator.autoScheduleIfPossible(packageId);

            for (CollabSubtaskEntity subtask : toSchedule) {
                // 跳过已完成/失败/取消的（状态已被其他调度处理）
                String status = subtask.getStatus();
                if (!"RUNNING".equals(status)) {
                    continue;
                }

                final String subTaskId = subtask.getSubTaskId();
                executor.executeSubtask(subtask, packageId, traceId).thenAccept(result -> {
                    safeUpdateSubtaskStatus(packageId, subTaskId, "COMPLETED", result, null);
                    scheduleNextSubtasks(packageId, traceId);
                }).exceptionally(e -> {
                    log.error("Auto-scheduled subtask failed: {}", subTaskId, e);
                    safeUpdateSubtaskStatus(packageId, subTaskId, "FAILED", null, e.getMessage());
                    scheduleNextSubtasks(packageId, traceId);
                    return null;
                });
            }

            // 如果没有可调度的子任务，检查是否全部完成
            if (toSchedule.isEmpty()) {
                checkAndCompletePackage(packageId);
            }
        } catch (Exception e) {
            log.error("Error in auto-schedule for package {}: {}", packageId, e.getMessage());
        }
    }

    /**
     * 仅在子任务当前状态为 RUNNING 时推进状态，避免重复流转导致 IllegalStateException
     */
    private void safeUpdateSubtaskStatus(String packageId, String subTaskId, String targetStatus,
                                         String result, String errorMessage) {
        try {
            List<CollabSubtaskEntity> subtasks = orchestrator.getSubtasks(packageId);
            Optional<CollabSubtaskEntity> subtaskOpt = subtasks.stream()
                    .filter(s -> s.getSubTaskId().equals(subTaskId))
                    .findFirst();

            if (subtaskOpt.isEmpty()) {
                log.warn("safeUpdateSubtaskStatus skipped, subtask not found: packageId={}, subTaskId={}",
                        packageId, subTaskId);
                return;
            }

            String currentStatus = subtaskOpt.get().getStatus();
            if (!"RUNNING".equals(currentStatus)) {
                log.info("safeUpdateSubtaskStatus skipped, currentStatus={}, targetStatus={}, packageId={}, subTaskId={}",
                        currentStatus, targetStatus, packageId, subTaskId);
                return;
            }

            orchestrator.updateSubtaskStatus(packageId, subTaskId, targetStatus, result, errorMessage);
        } catch (IllegalStateException e) {
            log.info("safeUpdateSubtaskStatus ignored invalid transition: packageId={}, subTaskId={}, targetStatus={}, msg={}",
                    packageId, subTaskId, targetStatus, e.getMessage());
        } catch (Exception e) {
            log.error("safeUpdateSubtaskStatus failed: packageId={}, subTaskId={}, targetStatus={}",
                    packageId, subTaskId, targetStatus, e);
        }
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }
}
