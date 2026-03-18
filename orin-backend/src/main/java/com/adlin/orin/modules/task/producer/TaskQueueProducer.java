package com.adlin.orin.modules.task.producer;

import com.adlin.orin.modules.task.dto.TaskMessage;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskPriority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 任务队列生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskQueueProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${orin.task.exchange.name:workflow-task-exchange}")
    private String exchangeName;

    @Value("${orin.task.routing-key:workflow.task}")
    private String routingKey;

    /**
     * 发送任务到队列（支持优先级）
     */
    public void sendTask(TaskMessage taskMessage) {
        log.info("Sending task to queue: taskId={}, priority={}, workflowId={}",
                taskMessage.getTaskId(), taskMessage.getPriority(), taskMessage.getWorkflowId());

        // 设置消息优先级
        MessagePostProcessor messagePostProcessor = message -> {
            int priority = getPriorityValue(taskMessage.getPriority());
            message.getMessageProperties().setPriority(priority);
            message.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
            return message;
        };

        rabbitTemplate.convertAndSend(exchangeName, routingKey, taskMessage, messagePostProcessor);
        log.debug("Task sent successfully: {}", taskMessage.getTaskId());
    }

    /**
     * 发送任务到重试队列
     */
    public void sendToRetry(TaskMessage taskMessage) {
        log.info("Sending task to retry: taskId={}, retryCount={}",
                taskMessage.getTaskId(), taskMessage.getRetryCount());
        taskMessage.setPriority(TaskPriority.HIGH); // 重试任务提高优先级
        sendTask(taskMessage);
    }

    /**
     * 将任务从死信队列重放
     */
    public void replayTask(TaskMessage taskMessage) {
        log.info("Replaying task: taskId={}", taskMessage.getTaskId());
        taskMessage.setReplay(true);
        taskMessage.setRetryCount(0);
        taskMessage.setErrorMessage(null);
        sendTask(taskMessage);
    }

    /**
     * 优先级值转换
     */
    private int getPriorityValue(TaskPriority priority) {
        if (priority == null) {
            return TaskPriority.NORMAL.getValue();
        }
        return priority.getValue();
    }
}
