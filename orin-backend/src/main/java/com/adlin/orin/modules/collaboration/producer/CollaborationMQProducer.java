package com.adlin.orin.modules.collaboration.producer;

import com.adlin.orin.modules.collaboration.dto.CollabTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 协作任务 MQ 生产者 - 发布任务到 RabbitMQ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollaborationMQProducer {

    private final RabbitTemplate collabRabbitTemplate;

    @Value("${orin.collaboration.exchange.name:collaboration-task-exchange}")
    private String exchangeName;

    @Value("${orin.collaboration.routing-key:collaboration.task}")
    private String routingKey;

    /**
     * 发送任务到队列
     */
    public void sendTask(CollabTaskMessage message) {
        try {
            log.info("Publishing collab task to MQ: packageId={}, subTaskId={}, attempt={}",
                    message.getPackageId(), message.getSubTaskId(), message.getAttempt());

            collabRabbitTemplate.convertAndSend(exchangeName, routingKey, message);

            log.debug("Task published successfully: {}", message.getCorrelationId());
        } catch (AmqpException e) {
            log.error("Failed to publish collab task: packageId={}, subTaskId={}",
                    message.getPackageId(), message.getSubTaskId(), e);
            throw e;
        }
    }

    /**
     * 发送任务到重试队列（带指数退避延迟）
     */
    public void sendToRetry(CollabTaskMessage message) {
        int currentAttempt = message.getAttempt() != null ? message.getAttempt() : 0;
        message.setAttempt(currentAttempt + 1);

        // 计算指数退避延迟
        long delay = calculateRetryDelay(message, currentAttempt + 1);

        log.info("Publishing collab task retry: packageId={}, subTaskId={}, attempt={}, delay={}ms",
                message.getPackageId(), message.getSubTaskId(), message.getAttempt(), delay);

        // 消息后处理器：设置延迟和优先级
        MessagePostProcessor messagePostProcessor = msg -> {
            msg.getMessageProperties().setPriority(9); // 高优先级
            msg.getMessageProperties().setHeader("x-retry-count", message.getAttempt());
            msg.getMessageProperties().setHeader("x-retry-delay", delay);
            // 如果使用延迟，则设置 TTL 为延迟时间
            if (delay > 0) {
                msg.getMessageProperties().setExpiration(String.valueOf(delay));
            }
            return msg;
        };

        collabRabbitTemplate.convertAndSend(exchangeName, routingKey, message, messagePostProcessor);
    }

    /**
     * 计算指数退避延迟
     * delay = min(initialInterval * (multiplier ^ attempt), maxInterval)
     */
    private long calculateRetryDelay(CollabTaskMessage message, int attempt) {
        long initialInterval = message.getRetryInitialInterval() != null
                ? message.getRetryInitialInterval() : 1000L;
        double multiplier = message.getRetryMultiplier() != null
                ? message.getRetryMultiplier() : 2.0;
        long maxInterval = message.getRetryMaxInterval() != null
                ? message.getRetryMaxInterval() : 30000L;

        long delay = (long) (initialInterval * Math.pow(multiplier, attempt - 1));
        return Math.min(delay, maxInterval);
    }

    /**
     * 发送任务到指定 replyTo 队列
     */
    public void sendTaskWithReplyTo(CollabTaskMessage message, String replyTo) {
        message.setReplyTo(replyTo);

        log.info("Publishing collab task with replyTo: packageId={}, subTaskId={}, replyTo={}",
                message.getPackageId(), message.getSubTaskId(), replyTo);

        MessagePostProcessor messagePostProcessor = msg -> {
            msg.getMessageProperties().setReplyTo(replyTo);
            msg.getMessageProperties().setCorrelationId(message.getCorrelationId());
            return msg;
        };

        collabRabbitTemplate.convertAndSend(exchangeName, routingKey, message, messagePostProcessor);
    }
}
