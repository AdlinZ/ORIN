package com.adlin.orin.modules.task.service;

import com.adlin.orin.modules.task.dto.TaskMessage;
import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskPriority;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskStatus;
import com.adlin.orin.modules.task.producer.TaskQueueProducer;
import com.adlin.orin.modules.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 任务服务 - 管理异步任务队列
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskQueueProducer taskQueueProducer;

    @Value("${orin.task.retry.max:3}")
    private int defaultMaxRetries;

    /**
     * 创建任务并入队
     */
    @Transactional
    public TaskEntity createAndEnqueueTask(Long workflowId, Map<String, Object> inputData,
                                           TaskPriority priority, String triggeredBy, String triggerSource) {
        return createAndEnqueueTask(workflowId, null, inputData, priority, triggeredBy, triggerSource);
    }

    /**
     * 创建任务并绑定已创建的工作流实例后入队。
     */
    @Transactional
    public TaskEntity createAndEnqueueTask(Long workflowId, Long workflowInstanceId, Map<String, Object> inputData,
                                           TaskPriority priority, String triggeredBy, String triggerSource) {
        // 生成唯一任务ID
        String taskId = "task-" + UUID.randomUUID().toString();

        // 创建任务实体
        TaskEntity task = TaskEntity.builder()
                .taskId(taskId)
                .workflowId(workflowId)
                .workflowInstanceId(workflowInstanceId)
                .priority(priority != null ? priority : TaskPriority.NORMAL)
                .status(TaskStatus.QUEUED)
                .inputData(inputData)
                .triggeredBy(triggeredBy)
                .triggerSource(triggerSource)
                .retryCount(0)
                .maxRetries(defaultMaxRetries)
                .build();

        task = taskRepository.save(task);

        // 创建任务消息并发送到队列
        TaskMessage message = TaskMessage.builder()
                .taskId(task.getTaskId())
                .workflowId(task.getWorkflowId())
                .workflowInstanceId(task.getWorkflowInstanceId())
                .priority(task.getPriority())
                .inputData(task.getInputData())
                .triggeredBy(task.getTriggeredBy())
                .triggerSource(task.getTriggerSource())
                .retryCount(0)
                .maxRetries(task.getMaxRetries())
                .build();

        taskQueueProducer.sendTask(message);

        log.info("Task created and enqueued: taskId={}, workflowId={}, priority={}",
                taskId, workflowId, priority);

        return task;
    }

    /**
     * 快速入队 - 使用默认优先级
     */
    public TaskEntity enqueueTask(Long workflowId, Map<String, Object> inputData, String triggeredBy) {
        return createAndEnqueueTask(workflowId, inputData, TaskPriority.NORMAL, triggeredBy, "API");
    }

    /**
     * 带优先级的入队
     */
    public TaskEntity enqueueTaskWithPriority(Long workflowId, Map<String, Object> inputData,
                                               TaskPriority priority, String triggeredBy) {
        return createAndEnqueueTask(workflowId, inputData, priority, triggeredBy, "API");
    }

    /**
     * 根据任务ID查询任务
     */
    public Optional<TaskEntity> getTaskById(String taskId) {
        return taskRepository.findByTaskId(taskId);
    }

    /**
     * 根据工作流ID查询任务列表
     */
    public List<TaskEntity> getTasksByWorkflowId(Long workflowId) {
        return taskRepository.findByWorkflowId(workflowId);
    }

    /**
     * 根据工作流实例ID查询任务
     */
    public Optional<TaskEntity> getTaskByWorkflowInstanceId(Long workflowInstanceId) {
        return taskRepository.findByWorkflowInstanceId(workflowInstanceId).stream().findFirst();
    }

    /**
     * 查询任务状态
     */
    public TaskStatus getTaskStatus(String taskId) {
        return taskRepository.findByTaskId(taskId)
                .map(TaskEntity::getStatus)
                .orElse(null);
    }

    /**
     * 查询排队中的任务（分页）
     */
    public Page<TaskEntity> getQueuedTasks(Pageable pageable) {
        return taskRepository.findByStatus(TaskStatus.QUEUED, pageable);
    }

    /**
     * 查询运行中的任务（分页）
     */
    public Page<TaskEntity> getRunningTasks(Pageable pageable) {
        return taskRepository.findByStatus(TaskStatus.RUNNING, pageable);
    }

    /**
     * 查询失败的任务（分页）
     */
    public Page<TaskEntity> getFailedTasks(Pageable pageable) {
        return taskRepository.findByStatus(TaskStatus.FAILED, pageable);
    }

    /**
     * 查询死信任务（分页）
     */
    public Page<TaskEntity> getDeadTasks(Pageable pageable) {
        return taskRepository.findByStatus(TaskStatus.DEAD, pageable);
    }

    /**
     * 手动重放失败或死信任务
     */
    @Transactional
    public TaskEntity replayTask(String taskId) {
        TaskEntity task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (task.getStatus() != TaskStatus.FAILED && task.getStatus() != TaskStatus.DEAD) {
            throw new IllegalStateException("Only FAILED or DEAD tasks can be replayed. Current status: " + task.getStatus());
        }

        log.info("Replaying task: taskId={}, originalStatus={}", taskId, task.getStatus());

        // 生成新的任务ID
        String newTaskId = "task-" + UUID.randomUUID().toString();

        // 创建新任务
        TaskEntity newTask = TaskEntity.builder()
                .taskId(newTaskId)
                .workflowId(task.getWorkflowId())
                .priority(task.getPriority())
                .status(TaskStatus.QUEUED)
                .inputData(task.getInputData())
                .triggeredBy(task.getTriggeredBy())
                .triggerSource(task.getTriggerSource())
                .retryCount(0)
                .maxRetries(task.getMaxRetries())
                .build();

        newTask = taskRepository.save(newTask);

        // 发送消息到队列
        TaskMessage message = TaskMessage.builder()
                .taskId(newTask.getTaskId())
                .workflowId(newTask.getWorkflowId())
                .priority(newTask.getPriority())
                .inputData(newTask.getInputData())
                .triggeredBy(newTask.getTriggeredBy())
                .triggerSource(newTask.getTriggerSource())
                .retryCount(0)
                .maxRetries(newTask.getMaxRetries())
                .replay(true)
                .originalTaskId(taskId)
                .build();

        taskQueueProducer.sendTask(message);

        // 更新原任务状态
        task.setStatus(TaskStatus.RETRYING);
        task.setUpdatedAt(java.time.LocalDateTime.now());
        taskRepository.save(task);

        return newTask;
    }

    /**
     * 取消任务
     */
    @Transactional
    public boolean cancelTask(String taskId) {
        Optional<TaskEntity> taskOpt = taskRepository.findByTaskId(taskId);
        if (taskOpt.isEmpty()) {
            return false;
        }

        TaskEntity task = taskOpt.get();
        if (task.getStatus() == TaskStatus.QUEUED) {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage("Task cancelled by user");
            taskRepository.save(task);
            log.info("Task cancelled: {}", taskId);
            return true;
        } else if (task.getStatus() == TaskStatus.RUNNING || task.getStatus() == TaskStatus.RETRYING) {
            // 正在运行的任务无法取消
            log.warn("Cannot cancel task in status: {}", task.getStatus());
            return false;
        }

        return false;
    }

    /**
     * 获取任务统计信息
     */
    public Map<String, Long> getTaskStatistics() {
        List<Object[]> results = taskRepository.countByStatus();
        Map<String, Long> stats = new java.util.HashMap<>();
        for (Object[] result : results) {
            TaskStatus status = (TaskStatus) result[0];
            Long count = (Long) result[1];
            stats.put(status.name(), count);
        }
        return stats;
    }

    /**
     * 获取待处理任务的优先级统计
     */
    public Map<String, Long> getPendingPriorityStatistics() {
        List<Object[]> results = taskRepository.countPendingByPriority();
        Map<String, Long> stats = new java.util.HashMap<>();
        for (Object[] result : results) {
            TaskPriority priority = (TaskPriority) result[0];
            Long count = (Long) result[1];
            stats.put(priority.name(), count);
        }
        return stats;
    }
}
