package com.adlin.orin.modules.task.controller;

import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskPriority;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskStatus;
import com.adlin.orin.modules.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务管理控制器 - 提供任务查询、重放等功能
 */
@Slf4j
@RestController
@RequestMapping("/v1/tasks")
@Tag(name = "Task Management", description = "任务队列管理接口")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * 查询任务状态
     * GET /v1/tasks/{taskId}
     */
    @Operation(summary = "查询任务状态", description = "根据任务ID查询任务详情和状态")
    @GetMapping("/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        return taskService.getTaskById(taskId)
                .map(task -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("taskId", task.getTaskId());
                    result.put("workflowId", task.getWorkflowId());
                    result.put("workflowInstanceId", task.getWorkflowInstanceId());
                    result.put("priority", task.getPriority());
                    result.put("status", task.getStatus());
                    result.put("triggeredBy", task.getTriggeredBy());
                    result.put("triggerSource", task.getTriggerSource());
                    result.put("retryCount", task.getRetryCount());
                    result.put("maxRetries", task.getMaxRetries());
                    result.put("nextRetryAt", task.getNextRetryAt());
                    result.put("errorMessage", task.getErrorMessage());
                    result.put("errorStack", task.getErrorStack());
                    result.put("deadLetterReason", task.getDeadLetterReason());
                    result.put("inputData", task.getInputData());
                    result.put("outputData", task.getOutputData());
                    result.put("queuedAt", task.getQueuedAt());
                    result.put("startedAt", task.getStartedAt());
                    result.put("completedAt", task.getCompletedAt());
                    result.put("durationMs", task.getDurationMs());
                    result.put("createdAt", task.getCreatedAt());
                    result.put("updatedAt", task.getUpdatedAt());
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 快速查询任务状态
     * GET /v1/tasks/{taskId}/status
     */
    @Operation(summary = "快速查询任务状态", description = "快速获取任务当前状态")
    @GetMapping("/{taskId}/status")
    public ResponseEntity<Map<String, Object>> getTaskQuickStatus(@PathVariable String taskId) {
        TaskStatus status = taskService.getTaskStatus(taskId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("status", status);
        result.put("statusDescription", status.getDescription());
        return ResponseEntity.ok(result);
    }

    /**
     * 手动重放任务
     * POST /v1/tasks/{taskId}/replay
     */
    @Operation(summary = "手动重放任务", description = "重新执行失败或死信任务")
    @PostMapping("/{taskId}/replay")
    public ResponseEntity<Map<String, Object>> replayTask(@PathVariable String taskId) {
        try {
            TaskEntity newTask = taskService.replayTask(taskId);
            Map<String, Object> result = new HashMap<>();
            result.put("originalTaskId", taskId);
            result.put("newTaskId", newTask.getTaskId());
            result.put("status", "REPLAYED");
            result.put("message", "Task has been requeued for execution");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Task not found");
            error.put("message", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid task status");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 取消任务
     * POST /v1/tasks/{taskId}/cancel
     */
    @Operation(summary = "取消任务", description = "取消排队中的任务")
    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelTask(@PathVariable String taskId) {
        boolean success = taskService.cancelTask(taskId);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("taskId", taskId);
            result.put("status", "CANCELLED");
            result.put("message", "Task cancelled successfully");
            return ResponseEntity.ok(result);
        } else {
            result.put("taskId", taskId);
            result.put("status", "FAILED");
            result.put("message", "Task cannot be cancelled (not in QUEUED status or not found)");
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 查询工作流的所有任务
     * GET /v1/tasks/workflow/{workflowId}
     */
    @Operation(summary = "查询工作流任务列表", description = "查询指定工作流的所有任务")
    @GetMapping("/workflow/{workflowId}")
    public ResponseEntity<Map<String, Object>> getWorkflowTasks(
            @PathVariable Long workflowId,
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {

        List<TaskEntity> tasks = taskService.getTasksByWorkflowId(workflowId);

        // 手动分页
        int start = page * size;
        int end = Math.min(start + size, tasks.size());
        List<TaskEntity> pageContent = start < tasks.size() ? tasks.subList(start, end) : List.of();

        Map<String, Object> result = new HashMap<>();
        result.put("content", pageContent.stream().map(this::toTaskSummary).toList());
        result.put("page", page);
        result.put("size", size);
        result.put("totalElements", tasks.size());
        result.put("totalPages", (tasks.size() + size - 1) / size);

        return ResponseEntity.ok(result);
    }

    /**
     * 查询排队中的任务
     * GET /v1/tasks/queued
     */
    @Operation(summary = "查询排队任务", description = "查询当前排队的任务")
    @GetMapping("/queued")
    public ResponseEntity<Map<String, Object>> getQueuedTasks(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "priority").and(Sort.by(Sort.Direction.ASC, "createdAt")));
        Page<TaskEntity> tasks = taskService.getQueuedTasks(pageable);

        return ResponseEntity.ok(buildPageResult(tasks));
    }

    /**
     * 查询运行中的任务
     * GET /v1/tasks/running
     */
    @Operation(summary = "查询运行中任务", description = "查询当前执行中的任务")
    @GetMapping("/running")
    public ResponseEntity<Map<String, Object>> getRunningTasks(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "startedAt"));
        Page<TaskEntity> tasks = taskService.getRunningTasks(pageable);

        return ResponseEntity.ok(buildPageResult(tasks));
    }

    /**
     * 查询失败的任务
     * GET /v1/tasks/failed
     */
    @Operation(summary = "查询失败任务", description = "查询执行失败的任务")
    @GetMapping("/failed")
    public ResponseEntity<Map<String, Object>> getFailedTasks(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<TaskEntity> tasks = taskService.getFailedTasks(pageable);

        return ResponseEntity.ok(buildPageResult(tasks));
    }

    /**
     * 查询死信任务
     * GET /v1/tasks/dead
     */
    @Operation(summary = "查询死信任务", description = "查询进入死信队列的任务")
    @GetMapping("/dead")
    public ResponseEntity<Map<String, Object>> getDeadTasks(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<TaskEntity> tasks = taskService.getDeadTasks(pageable);

        return ResponseEntity.ok(buildPageResult(tasks));
    }

    /**
     * 获取任务统计信息
     * GET /v1/tasks/statistics
     */
    @Operation(summary = "任务统计", description = "获取任务队列统计信息")
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Long> statusStats = taskService.getTaskStatistics();
        Map<String, Long> priorityStats = taskService.getPendingPriorityStatistics();

        Map<String, Object> result = new HashMap<>();
        result.put("statusStatistics", statusStats);
        result.put("pendingPriorityStatistics", priorityStats);

        return ResponseEntity.ok(result);
    }

    /**
     * 创建新任务（带优先级）
     * POST /v1/tasks
     */
    @Operation(summary = "创建任务", description = "创建一个新的任务并加入队列")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTask(
            @RequestBody TaskCreateRequest request) {

        TaskPriority priority = TaskPriority.fromString(request.getPriority());

        TaskEntity task = taskService.createAndEnqueueTask(
                request.getWorkflowId(),
                request.getInputData(),
                priority,
                request.getTriggeredBy(),
                request.getTriggerSource()
        );

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getTaskId());
        result.put("workflowId", task.getWorkflowId());
        result.put("priority", task.getPriority());
        result.put("status", task.getStatus());
        result.put("message", "Task created and enqueued successfully");

        return ResponseEntity.ok(result);
    }

    private Map<String, Object> buildPageResult(Page<TaskEntity> page) {
        Map<String, Object> result = new HashMap<>();
        result.put("content", page.getContent().stream().map(this::toTaskSummary).toList());
        result.put("page", page.getNumber());
        result.put("size", page.getSize());
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return result;
    }

    private Map<String, Object> toTaskSummary(TaskEntity task) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("taskId", task.getTaskId());
        summary.put("workflowId", task.getWorkflowId());
        summary.put("workflowInstanceId", task.getWorkflowInstanceId());
        summary.put("priority", task.getPriority());
        summary.put("status", task.getStatus());
        summary.put("retryCount", task.getRetryCount());
        summary.put("errorMessage", task.getErrorMessage());
        summary.put("createdAt", task.getCreatedAt());
        summary.put("updatedAt", task.getUpdatedAt());
        return summary;
    }

    /**
     * 任务创建请求DTO
     */
    public static class TaskCreateRequest {
        private Long workflowId;
        private Map<String, Object> inputData;
        private String priority;
        private String triggeredBy;
        private String triggerSource;

        public Long getWorkflowId() { return workflowId; }
        public void setWorkflowId(Long workflowId) { this.workflowId = workflowId; }
        public Map<String, Object> getInputData() { return inputData; }
        public void setInputData(Map<String, Object> inputData) { this.inputData = inputData; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getTriggeredBy() { return triggeredBy; }
        public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
        public String getTriggerSource() { return triggerSource; }
        public void setTriggerSource(String triggerSource) { this.triggerSource = triggerSource; }
    }
}
