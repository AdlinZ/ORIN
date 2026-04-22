package com.adlin.orin.modules.task.config;

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
 * RabbitMQ配置 - 支持优先级队列和死信队列
 */
@Configuration
public class RabbitMQConfig {

    @Value("${orin.task.queue.name:workflow-task-queue}")
    private String taskQueueName;

    @Value("${orin.task.exchange.name:workflow-task-exchange}")
    private String taskExchangeName;

    @Value("${orin.task.routing-key:workflow.task}")
    private String taskRoutingKey;

    @Value("${orin.task.dlq.name:workflow-task-dlq}")
    private String dlqName;

    @Value("${orin.task.dlx.name:workflow-task-dlx}")
    private String dlxName;

    @Value("${orin.task.priority.max:10}")
    private int maxPriority;

    @Value("${orin.rabbitmq.listeners.auto-startup:true}")
    private boolean listenerAutoStartup;

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(dlxName);
    }

    /**
     * 死信队列
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(dlqName).build();
    }

    /**
     * 绑定死信队列到死信交换机
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(taskRoutingKey + ".dlq");
    }

    /**
     * 任务交换机
     */
    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(taskExchangeName);
    }

    /**
     * 优先级队列 - 支持任务优先级
     */
    @Bean
    public Queue taskQueue() {
        return QueueBuilder.durable(taskQueueName)
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", taskRoutingKey + ".dlq")
                .withArgument("x-max-priority", maxPriority)
                .build();
    }

    /**
     * 绑定任务队列到交换机
     */
    @Bean
    public Binding taskBinding() {
        return BindingBuilder.bind(taskQueue())
                .to(taskExchange())
                .with(taskRoutingKey);
    }

    /**
     * 消息转换器 - JSON格式
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate配置
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        rabbitTemplate.setExchange(taskExchangeName);
        rabbitTemplate.setRoutingKey(taskRoutingKey);
        return rabbitTemplate;
    }

    /**
     * RabbitListenerContainerFactory配置 - 支持并发消费和优先级
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(10);
        factory.setDefaultRequeueRejected(false); // 失败后不重新入队，由代码控制重试
        factory.setMissingQueuesFatal(false);
        factory.setAutoStartup(listenerAutoStartup);
        return factory;
    }
}
