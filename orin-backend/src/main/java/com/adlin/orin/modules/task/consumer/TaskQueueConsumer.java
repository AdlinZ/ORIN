package com.adlin.orin.modules.task.consumer;

import com.adlin.orin.modules.task.dto.TaskMessage;
import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskPriority;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskStatus;
import com.adlin.orin.modules.task.repository.TaskRepository;
import com.adlin.orin.modules.workflow.engine.WorkflowEngine;
import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 任务队列消费者 - 支持指数退避重试
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskQueueConsumer {

    private final TaskRepository taskRepository;
    private final WorkflowEngine workflowEngine;
    private final DeadLetterHandler deadLetterHandler;

    @Value("${orin.task.retry.max:3}")
    private int maxRetries;

    @Value("${orin.task.retry.initial-interval:1000}")
    private long initialInterval;

    @Value("${orin.task.retry.multiplier:2.0}")
    private double multiplier;

    @Value("${orin.task.retry.max-interval:30000}")
    private long maxInterval;

    /**
     * 监听任务队列
     */
    @RabbitListener(queues = "${orin.task.queue.name:workflow-task-queue}")
    public void consumeTask(TaskMessage taskMessage) {
        log.info("Received task: taskId={}, priority={}, retryCount={}",
                taskMessage.getTaskId(), taskMessage.getPriority(), taskMessage.getRetryCount());

        Optional<TaskEntity> taskOpt = taskRepository.findByTaskId(taskMessage.getTaskId());
        if (taskOpt.isEmpty()) {
            log.warn("Task not found in database: {}", taskMessage.getTaskId());
            return;
        }

        TaskEntity task = taskOpt.get();

        // 检查任务状态，避免重复执行
        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.DEAD) {
            log.info("Task already completed or dead: {}", taskMessage.getTaskId());
            return;
        }

        // 更新任务状态为运行中
        task.setStatus(TaskStatus.RUNNING);
        task.setStartedAt(LocalDateTime.now());
        taskRepository.save(task);

        try {
            // 执行工作流
            WorkflowInstanceEntity instance = executeWorkflow(task, taskMessage);
            if (instance.getStatus() != WorkflowInstanceEntity.InstanceStatus.SUCCESS) {
                String message = instance.getErrorMessage() != null
                        ? instance.getErrorMessage()
                        : "Workflow instance finished with status: " + instance.getStatus();
                throw new WorkflowTaskExecutionException(message, instance);
            }

            // 执行成功，更新任务状态
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            task.setOutputData(instance.getOutputData());
            task.setErrorMessage(null);
            task.setErrorStack(null);
            if (task.getStartedAt() != null) {
                task.setDurationMs(java.time.Duration.between(task.getStartedAt(), task.getCompletedAt()).toMillis());
            }
            taskRepository.save(task);

            log.info("Task completed successfully: {}", taskMessage.getTaskId());

        } catch (Exception e) {
            handleTaskFailure(task, taskMessage, e);
        }
    }

    /**
     * 执行工作流
     */
    private WorkflowInstanceEntity executeWorkflow(TaskEntity task, TaskMessage taskMessage) {
        Long instanceId = taskMessage.getWorkflowInstanceId();

        if (instanceId != null) {
            // 已有实例，直接执行
            return workflowEngine.executeInstance(instanceId);
        } else {
            // 创建新实例并执行
            WorkflowInstanceEntity instance = workflowEngine.createInstance(
                    task.getWorkflowId(),
                    task.getInputData(),
                    task.getTriggeredBy()
            );
            task.setWorkflowInstanceId(instance.getId());
            taskRepository.save(task);
            return workflowEngine.executeInstance(instance.getId());
        }
    }

    /**
     * 处理任务失败 - 指数退避重试
     */
    private void handleTaskFailure(TaskEntity task, TaskMessage taskMessage, Exception e) {
        log.error("Task execution failed: taskId={}, error={}", taskMessage.getTaskId(), e.getMessage(), e);

        int currentRetry = taskMessage.getRetryCount() != null ? taskMessage.getRetryCount() : 0;
        int maxRetry = taskMessage.getMaxRetries() != null ? taskMessage.getMaxRetries() : maxRetries;

        // 更新错误信息
        task.setErrorMessage(e.getMessage());
        task.setErrorStack(getStackTrace(e));
        task.setRetryCount(currentRetry);

        if (currentRetry < maxRetry) {
            // 计算下一次重试时间（指数退避）
            long delay = calculateExponentialBackoff(currentRetry);
            LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(delay * 1_000_000);

            task.setStatus(TaskStatus.RETRYING);
            task.setNextRetryAt(nextRetryAt);
            taskRepository.save(task);

            // 更新消息
            taskMessage.setRetryCount(currentRetry + 1);
            taskMessage.setErrorMessage(e.getMessage());

            // 标记为延迟重试，由 RetryScheduler 定时检查并发送
            taskMessage.setDelayedRetry(true);
            taskMessage.setDelayMillis(delay);

            log.info("Task scheduled for delayed retry: taskId={}, retryCount={}, delay={}ms, nextRetryAt={}",
                    taskMessage.getTaskId(), currentRetry + 1, delay, nextRetryAt);

        } else {
            // 超过最大重试次数，进入死信队列
            log.error("Task exceeded max retries, moving to dead letter queue: taskId={}", taskMessage.getTaskId());
            deadLetterHandler.moveToDeadLetter(task, taskMessage, "Exceeded max retries: " + e.getMessage());
        }
    }

    /**
     * 计算指数退避延迟
     */
    private long calculateExponentialBackoff(int retryCount) {
        long delay = (long) (initialInterval * Math.pow(multiplier, retryCount));
        return Math.min(delay, maxInterval);
    }

    private static final class WorkflowTaskExecutionException extends RuntimeException {
        private WorkflowTaskExecutionException(String message, WorkflowInstanceEntity instance) {
            super(message + " (instanceId=" + instance.getId() + ", status=" + instance.getStatus() + ")");
        }
    }

    /**
     * 获取堆栈跟踪
     */
    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
