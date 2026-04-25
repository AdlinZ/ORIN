package com.adlin.orin.modules.collaboration.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置 - 协作模块专用
 * 独立于 task 模块的 RabbitMQ 拓扑，支持独立回滚
 */
@Configuration
public class CollaborationRabbitMQConfig {

    @Value("${orin.collaboration.exchange.name:collaboration-task-exchange}")
    private String exchangeName;

    @Value("${orin.collaboration.queue.name:collaboration-task-queue}")
    private String queueName;

    @Value("${orin.collaboration.routing-key:collaboration.task}")
    private String routingKey;

    @Value("${orin.collaboration.dlq.name:collaboration-task-dlq}")
    private String dlqName;

    @Value("${orin.collaboration.dlx.name:collaboration-task-dlx}")
    private String dlxName;

    @Value("${orin.collaboration.reply.exchange:collaboration-reply-exchange}")
    private String replyExchangeName;

    @Value("${orin.collaboration.result.queue:collaboration-task-result-queue}")
    private String resultQueueName;

    @Value("${orin.collaboration.reply.routing-key:collaboration.task.result}")
    private String replyRoutingKey;

    @Value("${orin.collaboration.queue.ttl:300000}")
    private int queueTtl;

    @Value("${orin.collaboration.listeners.auto-startup:true}")
    private boolean listenerAutoStartup;

    /**
     * 任务交换机
     */
    @Bean
    public DirectExchange collabExchange() {
        return new DirectExchange(exchangeName);
    }

    /**
     * 回复交换机（用于 result 回调）
     */
    @Bean
    public DirectExchange collabReplyExchange() {
        return new DirectExchange(replyExchangeName);
    }

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange collabDeadLetterExchange() {
        return new DirectExchange(dlxName);
    }

    /**
     * 主任务队列 - 带 TTL 和死信配置
     */
    @Bean
    public Queue collabTaskQueue() {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", routingKey + ".dlq")
                .withArgument("x-message-ttl", queueTtl)
                .build();
    }

    /**
     * 死信队列
     */
    @Bean
    public Queue collabDeadLetterQueue() {
        return QueueBuilder.durable(dlqName).build();
    }

    /**
     * 结果队列（接收 AI engine 执行结果）
     */
    @Bean
    public Queue collabResultQueue() {
        return QueueBuilder.durable(resultQueueName).build();
    }

    /**
     * 绑定任务队列到交换机
     */
    @Bean
    public Binding collabTaskBinding() {
        return BindingBuilder.bind(collabTaskQueue())
                .to(collabExchange())
                .with(routingKey);
    }

    /**
     * 绑定死信队列到死信交换机
     */
    @Bean
    public Binding collabDlqBinding() {
        return BindingBuilder.bind(collabDeadLetterQueue())
                .to(collabDeadLetterExchange())
                .with(routingKey + ".dlq");
    }

    /**
     * 绑定结果队列到回复交换机
     */
    @Bean
    public Binding collabResultBinding() {
        return BindingBuilder.bind(collabResultQueue())
                .to(collabReplyExchange())
                .with(replyRoutingKey);
    }

    /**
     * 消息转换器 - JSON格式
     */
    @Bean
    public MessageConverter collabJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate配置
     */
    @Bean
    public RabbitTemplate collabRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(collabJsonMessageConverter());
        rabbitTemplate.setExchange(exchangeName);
        rabbitTemplate.setRoutingKey(routingKey);
        return rabbitTemplate;
    }

    /**
     * RabbitListenerContainerFactory配置 - 支持并发消费
     */
    @Bean
    public SimpleRabbitListenerContainerFactory collabRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(collabJsonMessageConverter());
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(10);
        factory.setDefaultRequeueRejected(false);
        factory.setMissingQueuesFatal(false);
        factory.setAutoStartup(listenerAutoStartup);
        return factory;
    }
}
