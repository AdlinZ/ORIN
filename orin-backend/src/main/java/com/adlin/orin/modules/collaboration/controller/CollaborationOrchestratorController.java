package com.adlin.orin.modules.collaboration.controller;

import com.adlin.orin.modules.collaboration.dto.CollaborationPackage;
import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.service.CollaborationOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
}