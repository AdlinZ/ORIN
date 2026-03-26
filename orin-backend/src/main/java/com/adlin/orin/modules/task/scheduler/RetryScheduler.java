package com.adlin.orin.modules.task.scheduler;

import com.adlin.orin.modules.task.config.TaskRetryConfig;
import com.adlin.orin.modules.task.dto.TaskMessage;
import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskStatus;
import com.adlin.orin.modules.task.producer.TaskQueueProducer;
import com.adlin.orin.modules.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务重试调度器
 * 定时检查待重试的任务并在延迟时间到达后重新入队
 * 支持按任务类型配置不同重试策略
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryScheduler {

    private final TaskRepository taskRepository;
    private final TaskQueueProducer taskQueueProducer;
    private final TaskRetryConfig retryConfig;

    @Value("${orin.task.retry.enabled:true}")
    private boolean retryEnabled;

    /**
     * 每 10 秒检查一次是否有需要重试的任务
     */
    @Scheduled(fixedDelay = 10000)
    public void processRetryTasks() {
        if (!retryEnabled) {
            return;
        }

        try {
            // 查询需要重试的任务（状态为 RETRYING 且下次重试时间已到达）
            LocalDateTime now = LocalDateTime.now();
            List<TaskEntity> tasksToRetry = taskRepository.findTasksToRetry(now);

            if (tasksToRetry.isEmpty()) {
                return;
            }

            log.info("Found {} tasks ready for retry", tasksToRetry.size());

            for (TaskEntity task : tasksToRetry) {
                try {
                    // 构建任务消息
                    TaskMessage message = TaskMessage.builder()
                            .taskId(task.getTaskId())
                            .workflowId(task.getWorkflowId())
                            .workflowInstanceId(task.getWorkflowInstanceId())
                            .priority(task.getPriority())
                            .inputData(task.getInputData())
                            .triggeredBy(task.getTriggeredBy())
                            .triggerSource(task.getTriggerSource())
                            .retryCount(task.getRetryCount())
                            .maxRetries(task.getMaxRetries())
                            .errorMessage(task.getErrorMessage())
                            .build();

                    // 更新任务状态为 QUEUED
                    task.setStatus(TaskStatus.QUEUED);
                    task.setNextRetryAt(null);
                    taskRepository.save(task);

                    // 发送任务到队列
                    taskQueueProducer.sendTask(message);

                    log.info("Task retried successfully: taskId={}, retryCount={}",
                            task.getTaskId(), task.getRetryCount());

                } catch (Exception e) {
                    log.error("Failed to retry task: taskId={}, error={}",
                            task.getTaskId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error in retry scheduler", e);
        }
    }

    /**
     * 根据任务类型计算下次重试时间
     * 使用该类型的重试策略配置
     */
    public LocalDateTime calculateNextRetryTime(String taskType, int retryCount) {
        TaskRetryConfig.TaskTypeRetryConfig config = retryConfig.getConfigForType(taskType);
        long delayMillis = (long) (config.getInitialDelay() * Math.pow(config.getBackoffMultiplier(), retryCount));
        return LocalDateTime.now().plusNanos(delayMillis * 1_000_000);
    }
}