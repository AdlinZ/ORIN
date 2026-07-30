package com.adlin.orin.modules.task.service;

import com.adlin.orin.modules.task.entity.TaskQueue;
import com.adlin.orin.modules.task.repository.TaskQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskQueueService {

    private final TaskQueueRepository taskRepository;

    /**
     * 创建任务
     */
    @Transactional
    public TaskQueue createTask(String name, String taskType, String content, Integer priority, String createdBy) {
        TaskQueue task = TaskQueue.builder()
                .name(name)
                .taskType(taskType)
                .content(content)
                .priority(priority != null ? priority : 5)
                .status("PENDING")
                .createdBy(createdBy)
                .retryCount(0)
                .maxRetry(3)
                .build();

        return taskRepository.save(task);
    }

    /**
     * 获取下一个待执行任务（按优先级）
     */
    public Optional<TaskQueue> getNextTask() {
        List<TaskQueue> pendingTasks = taskRepository.findByStatusOrderByPriority("PENDING");
        
        if (!pendingTasks.isEmpty()) {
            TaskQueue task = pendingTasks.get(0);
            // 标记为 RUNNING
            task.setStatus("RUNNING");
            task.setStartTime(LocalDateTime.now());
            return Optional.of(taskRepository.save(task));
        }
        
        return Optional.empty();
    }

    /**
     * 完成任务
     */
    @Transactional
    public TaskQueue completeTask(Long id, String result) {
        return taskRepository.findById(id)
                .map(task -> {
                    task.setStatus("COMPLETED");
                    task.setResult(result);
                    task.setEndTime(LocalDateTime.now());
                    return taskRepository.save(task);
                })
                .orElseThrow(() -> new RuntimeException("Task not found: " + id));
    }

    /**
     * 标记任务失败
     */
    @Transactional
    public TaskQueue failTask(Long id, String errorMessage) {
        return taskRepository.findById(id)
                .map(task -> {
                    task.setStatus("FAILED");
                    task.setErrorMessage(errorMessage);
                    task.setEndTime(LocalDateTime.now());
                    return taskRepository.save(task);
                })
                .orElseThrow(() -> new RuntimeException("Task not found: " + id));
    }

    /**
     * 重试任务
     */
    @Transactional
    public TaskQueue retryTask(Long id) {
        return taskRepository.findById(id)
                .map(task -> {
                    if (task.getRetryCount() < task.getMaxRetry()) {
                        task.setStatus("PENDING");
                        task.setRetryCount(task.getRetryCount() + 1);
                        task.setErrorMessage(null);
                        task.setResult(null);
                        return taskRepository.save(task);
                    } else {
                        throw new RuntimeException("Max retries exceeded for task: " + id);
                    }
                })
                .orElseThrow(() -> new RuntimeException("Task not found: " + id));
    }

    /**
     * 取消任务
     */
    @Transactional
    public TaskQueue cancelTask(Long id) {
        return taskRepository.findById(id)
                .map(task -> {
                    if ("PENDING".equals(task.getStatus())) {
                        task.setStatus("CANCELLED");
                        task.setEndTime(LocalDateTime.now());
                        return taskRepository.save(task);
                    } else {
                        throw new RuntimeException("Cannot cancel task in status: " + task.getStatus());
                    }
                })
                .orElseThrow(() -> new RuntimeException("Task not found: " + id));
    }

    /**
     * 获取任务列表（分页）
     */
    public Page<TaskQueue> getTasks(String createdBy, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "priority").and(Sort.by(Sort.Direction.DESC, "createdAt")));
        if (createdBy != null && !createdBy.isEmpty()) {
            return taskRepository.findByCreatedBy(createdBy, pageable);
        }
        return taskRepository.findAll(pageable);
    }

    /**
     * 获取任务详情
     */
    public Optional<TaskQueue> getTask(Long id) {
        return taskRepository.findById(id);
    }

    /**
     * 获取任务统计
     */
    public Map<String, Long> getTaskStats(String createdBy) {
        if (createdBy != null && !createdBy.isEmpty()) {
            return Map.of(
                    "pending", taskRepository.countByCreatedByAndStatus(createdBy, "PENDING"),
                    "running", taskRepository.countByCreatedByAndStatus(createdBy, "RUNNING"),
                    "completed", taskRepository.countByCreatedByAndStatus(createdBy, "COMPLETED"),
                    "failed", taskRepository.countByCreatedByAndStatus(createdBy, "FAILED")
            );
        }
        
        return Map.of(
                "pending", taskRepository.countByStatus("PENDING"),
                "running", taskRepository.countByStatus("RUNNING"),
                "completed", taskRepository.countByStatus("COMPLETED"),
                "failed", taskRepository.countByStatus("FAILED")
        );
    }

    /**
     * 删除任务
     */
    @Transactional
    public void deleteTask(Long id) {
        taskRepository.deleteById(id);
    }
}
