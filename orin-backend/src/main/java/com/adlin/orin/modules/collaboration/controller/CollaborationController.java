package com.adlin.orin.modules.collaboration.controller;

import com.adlin.orin.modules.collaboration.entity.CollaborationTask;
import com.adlin.orin.modules.collaboration.service.CollaborationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 多智能体协作控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/collaboration")
@RequiredArgsConstructor
@Tag(name = "Multi-Agent Collaboration", description = "多智能体协作管理")
public class CollaborationController {

    private final CollaborationService collaborationService;

    @Operation(summary = "创建协作任务")
    @PostMapping("/tasks")
    public ResponseEntity<CollaborationTask> createTask(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        
        String name = (String) request.get("name");
        String description = (String) request.get("description");
        String taskType = (String) request.getOrDefault("taskType", "SEQUENTIAL");
        @SuppressWarnings("unchecked")
        List<String> agentIds = (List<String>) request.get("agentIds");
        
        CollaborationTask task = collaborationService.createTask(
                name, description, taskType, agentIds, userId);
        
        return ResponseEntity.ok(task);
    }

    @Operation(summary = "获取当前用户的协作任务列表")
    @GetMapping("/tasks")
    public ResponseEntity<List<CollaborationTask>> getMyTasks(
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        
        List<CollaborationTask> tasks = collaborationService.getTasksByUser(userId);
        return ResponseEntity.ok(tasks);
    }

    @Operation(summary = "获取所有协作任务列表")
    @GetMapping("/tasks/all")
    public ResponseEntity<List<CollaborationTask>> getAllTasks() {
        List<CollaborationTask> tasks = collaborationService.getAllTasks();
        return ResponseEntity.ok(tasks);
    }

    @Operation(summary = "获取协作任务详情")
    @GetMapping("/tasks/{id}")
    public ResponseEntity<CollaborationTask> getTask(@PathVariable Long id) {
        return collaborationService.getTask(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "开始执行协作任务")
    @PostMapping("/tasks/{id}/start")
    public ResponseEntity<CollaborationTask> startTask(@PathVariable Long id) {
        try {
            CollaborationTask task = collaborationService.updateTaskStatus(id, "RUNNING", null, null);
            return ResponseEntity.ok(task);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "执行下一个 Agent")
    @PostMapping("/tasks/{id}/next")
    public ResponseEntity<CollaborationTask> executeNextAgent(@PathVariable Long id) {
        try {
            CollaborationTask task = collaborationService.executeNextAgent(id);
            return ResponseEntity.ok(task);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "完成任务")
    @PostMapping("/tasks/{id}/complete")
    public ResponseEntity<CollaborationTask> completeTask(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        
        try {
            String result = request.get("result");
            CollaborationTask task = collaborationService.updateTaskStatus(id, "COMPLETED", result, null);
            return ResponseEntity.ok(task);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "标记任务失败")
    @PostMapping("/tasks/{id}/fail")
    public ResponseEntity<CollaborationTask> failTask(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        
        try {
            String errorMessage = request.get("errorMessage");
            CollaborationTask task = collaborationService.updateTaskStatus(id, "FAILED", null, errorMessage);
            return ResponseEntity.ok(task);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "删除协作任务")
    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        try {
            collaborationService.deleteTask(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "获取正在运行的任务")
    @GetMapping("/tasks/running")
    public ResponseEntity<List<CollaborationTask>> getRunningTasks() {
        List<CollaborationTask> tasks = collaborationService.getRunningTasks();
        return ResponseEntity.ok(tasks);
    }
}
