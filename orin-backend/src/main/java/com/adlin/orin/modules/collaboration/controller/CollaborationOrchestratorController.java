package com.adlin.orin.modules.collaboration.controller;

import com.adlin.orin.modules.collaboration.dto.CollaborationPackage;
import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.event.CollaborationEvent;
import com.adlin.orin.modules.collaboration.event.CollaborationEventBus;
import com.adlin.orin.modules.collaboration.service.CollaborationExecutor;
import com.adlin.orin.modules.collaboration.service.CollaborationMemoryService;
import com.adlin.orin.modules.collaboration.service.CollaborationOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 多智能体协作编排 API
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/collaboration")
@RequiredArgsConstructor
@Tag(name = "Collaboration Orchestrator", description = "多智能体协作编排API")
public class CollaborationOrchestratorController {

    private final CollaborationOrchestrator orchestrator;
    private final CollaborationEventBus eventBus;
    private final CollaborationExecutor executor;
    private final CollaborationMemoryService memoryService;

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

        CollaborationPackage pkg = orchestrator.createPackage(intent, category, priority, complexity, mode, userId, traceId);
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

    // ==================== 运行时执行接口 ====================

    @Operation(summary = "启动子任务执行")
    @PostMapping("/packages/{packageId}/subtasks/{subTaskId}/execute")
    public ResponseEntity<Map<String, Object>> executeSubtask(
            @PathVariable String packageId,
            @PathVariable String subTaskId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {

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

        // 异步执行
        executor.executeSubtask(subtask, packageId, traceId).thenAccept(result -> {
            // 执行完成后更新状态
            orchestrator.updateSubtaskStatus(packageId, subTaskId, "COMPLETED", result, null);

            // 检查是否所有子任务都完成
            checkAndCompletePackage(packageId);
        }).exceptionally(e -> {
            log.error("Subtask execution failed: {}", subTaskId, e);
            orchestrator.updateSubtaskStatus(packageId, subTaskId, "FAILED", null, e.getMessage());
            return null;
        });

        return ResponseEntity.accepted().body(Map.of(
                "status", "STARTED",
                "subTaskId", subTaskId,
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
            orchestrator.updateSubtaskStatus(packageId, subTaskId, "COMPLETED", result, null);
            checkAndCompletePackage(packageId);
        }).exceptionally(e -> {
            log.error("Subtask retry failed: {}", subTaskId, e);
            orchestrator.updateSubtaskStatus(packageId, subTaskId, "FAILED", null, e.getMessage());
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

    /**
     * 检查并完成协作包
     */
    private void checkAndCompletePackage(String packageId) {
        if (orchestrator.isAllSubtasksCompleted(packageId)) {
            orchestrator.complete(packageId, "All subtasks completed");
        }
    }
}