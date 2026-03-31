package com.adlin.orin.modules.knowledge.controller;

import com.adlin.orin.common.enums.TaskStatus;
import com.adlin.orin.modules.knowledge.entity.KnowledgeTask;
import com.adlin.orin.modules.knowledge.repository.KnowledgeTaskRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库任务控制器
 * 提供任务日志、队列统计、重试等 API
 */
@RestController
@RequestMapping("/api/v1/knowledge/tasks")
@RequiredArgsConstructor
@Tag(name = "Knowledge Task", description = "知识库任务管理")
public class KnowledgeTaskController {

    private final KnowledgeTaskRepository taskRepository;

    @Operation(summary = "获取任务列表")
    @GetMapping
    public ResponseEntity<Page<KnowledgeTask>> getTasks(
            @RequestParam(required = false) String kbId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<KnowledgeTask> tasks;

        if (kbId != null && status != null) {
            tasks = taskRepository.findAll(pageRequest);
            // Filter by kbId and status in memory (or add custom query)
        } else if (kbId != null) {
            tasks = taskRepository.findAll(pageRequest);
        } else if (status != null) {
            tasks = taskRepository.findAll(pageRequest);
        } else {
            tasks = taskRepository.findAll(pageRequest);
        }

        return ResponseEntity.ok(tasks);
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{taskId}")
    public ResponseEntity<KnowledgeTask> getTask(@PathVariable String taskId) {
        return taskRepository.findById(taskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "重试任务")
    @PostMapping("/{taskId}/retry")
    public ResponseEntity<Map<String, Object>> retryTask(@PathVariable String taskId) {
        return taskRepository.findById(taskId)
                .map(task -> {
                    if (task.getStatus() != TaskStatus.FAILED) {
                        Map<String, Object> error = new HashMap<>();
                        error.put("success", false);
                        error.put("message", "只能重试失败的任务");
                        return ResponseEntity.badRequest().body(error);
                    }

                    task.setStatus(TaskStatus.RETRYING);
                    task.setRetryCount(task.getRetryCount() + 1);
                    taskRepository.save(task);

                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("task", task);
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "获取队列统计")
    @GetMapping("/queue/stats")
    public ResponseEntity<Map<String, Object>> getQueueStats() {
        List<KnowledgeTask> allTasks = taskRepository.findAll();

        long pending = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING).count();
        long running = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.RUNNING).count();
        long success = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.SUCCESS).count();
        long failed = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.FAILED).count();
        long retrying = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.RETRYING).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", allTasks.size());
        stats.put("pending", pending);
        stats.put("running", running);
        stats.put("success", success);
        stats.put("failed", failed);
        stats.put("retrying", retrying);
        stats.put("queueLength", pending + running + retrying);

        return ResponseEntity.ok(stats);
    }

    @Operation(summary = "获取失败任务列表")
    @GetMapping("/failed")
    public ResponseEntity<List<KnowledgeTask>> getFailedTasks() {
        return ResponseEntity.ok(taskRepository.findByStatus(TaskStatus.FAILED));
    }
}
