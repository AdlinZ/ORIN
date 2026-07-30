package com.adlin.orin.modules.task.consumer;

import com.adlin.orin.modules.task.dto.TaskMessage;
import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskStatus;
import com.adlin.orin.modules.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 死信队列处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterHandler {

    private final TaskRepository taskRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${orin.task.dlq.name:workflow-task-dlq}")
    private String dlqName;

    @Value("${orin.task.dlx.name:workflow-task-dlx}")
    private String dlxName;

    @Value("${orin.task.routing-key:workflow.task}")
    private String routingKey;

    /**
     * 将任务移动到死信队列
     */
    @Transactional
    public void moveToDeadLetter(TaskEntity task, TaskMessage taskMessage, String reason) {
        log.warn("Moving task to dead letter queue: taskId={}, reason={}", taskMessage.getTaskId(), reason);

        // 更新任务状态为死信
        task.setStatus(TaskStatus.DEAD);
        task.setDeadLetterReason(reason);
        taskRepository.save(task);

        // 发送告警通知
        sendDeadLetterAlert(task, reason);
    }

    /**
     * 发送死信告警通知
     */
    private void sendDeadLetterAlert(TaskEntity task, String reason) {
        log.error("DEAD LETTER ALERT: taskId={}, workflowId={}, reason={}",
                task.getTaskId(), task.getWorkflowId(), reason);

        // TODO: 集成告警服务（如邮件、短信、Webhook等）
        // 这里可以调用 AlertNotificationService 发送告警
        // alertNotificationService.sendDeadLetterAlert(task, reason);
    }

    /**
     * 从死信队列重放任务
     */
    @Transactional
    public void replayFromDeadLetter(String taskId) {
        TaskEntity task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (task.getStatus() != TaskStatus.DEAD) {
            throw new IllegalStateException("Task is not in dead letter status: " + taskId);
        }

        log.info("Replaying task from dead letter: taskId={}", taskId);

        // 创建新的任务消息
        TaskMessage taskMessage = TaskMessage.builder()
                .taskId(task.getTaskId() + "-replay-" + System.currentTimeMillis())
                .workflowId(task.getWorkflowId())
                .priority(task.getPriority())
                .inputData(task.getInputData())
                .triggeredBy(task.getTriggeredBy())
                .triggerSource(task.getTriggerSource())
                .retryCount(0)
                .maxRetries(task.getMaxRetries())
                .replay(true)
                .originalTaskId(task.getTaskId())
                .build();

        // 重置任务状态为排队中
        task.setStatus(TaskStatus.QUEUED);
        task.setWorkflowInstanceId(null);
        task.setRetryCount(0);
        task.setErrorMessage(null);
        task.setErrorStack(null);
        task.setDeadLetterReason(null);
        task.setNextRetryAt(null);
        taskRepository.save(task);

        // 发送回队列
        rabbitTemplate.convertAndSend(dlxName, routingKey + ".dlq", taskMessage);
    }

    /**
     * 查询死信队列中的消息
     */
    public void processDeadLetterQueue() {
        log.info("Processing dead letter queue: {}", dlqName);
        // RabbitMQ 的死信队列可以通过管理接口查看
        // 这里可以添加定时任务来处理积压的死信消息
    }
}
