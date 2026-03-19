package com.adlin.orin.modules.task.controller;

import com.adlin.orin.modules.task.entity.TaskQueue;
import com.adlin.orin.modules.task.service.TaskQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务队列控制器
 * 支持优先级管理
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Task Queue", description = "任务队列管理")
public class TaskQueueController {

    private final TaskQueueService taskService;

    @Operation(summary = "创建任务")
    @PostMapping
    public ResponseEntity<TaskQueue> createTask(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        
        String name = (String) request.get("name");
        String taskType = (String) request.getOrDefault("taskType", "GENERAL");
        String content = (String) request.get("content");
        Integer priority = (Integer) request.getOrDefault("priority", 5);
        
        TaskQueue task = taskService.createTask(name, taskType, content, priority, userId);
        return ResponseEntity.ok(task);
    }

    @Operation(summary = "获取任务列表")
    @GetMapping
    public ResponseEntity<Page<TaskQueue>> getTasks(
            @RequestParam(required = false) String createdBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Page<TaskQueue> tasks = taskService.getTasks(createdBy, page, size);
        return ResponseEntity.ok(tasks);
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{id}")
    public ResponseEntity<TaskQueue> getTask(@PathVariable Long id) {
        return taskService.getTask(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "获取任务统计")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats(
            @RequestParam(required = false) String createdBy) {
        return ResponseEntity.ok(taskService.getTaskStats(createdBy));
    }

    @Operation(summary = "获取下一个待执行任务")
    @GetMapping("/next")
    public ResponseEntity<TaskQueue> getNextTask() {
        return taskService.getNextTask()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @Operation(summary = "完成任务")
    @PostMapping("/{id}/complete")
    public ResponseEntity<TaskQueue> completeTask(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        
        try {
            String result = request.get("result");
            TaskQueue task = taskService.completeTask(id, result);
            return ResponseEntity.ok(task);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "标记任务失败")
    @PostMapping("/{id}/fail")
    public ResponseEntity<TaskQueue> failTask(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        
        try {
            String errorMessage = request.get("errorMessage");
            TaskQueue task = taskService.failTask(id, errorMessage);
            return ResponseEntity.ok(task);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "重试任务")
    @PostMapping("/{id}/retry")
    public ResponseEntity<TaskQueue> retryTask(@PathVariable Long id) {
        try {
            TaskQueue task = taskService.retryTask(id);
            return ResponseEntity.ok(task);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "取消任务")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<TaskQueue> cancelTask(@PathVariable Long id) {
        try {
            TaskQueue task = taskService.cancelTask(id);
            return ResponseEntity.ok(task);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.ok().build();
    }
}
