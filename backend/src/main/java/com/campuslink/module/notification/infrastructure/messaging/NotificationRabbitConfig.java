package com.campuslink.module.notification.infrastructure.messaging;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableRabbit
@ConditionalOnProperty(name = "campuslink.notifications.rabbitmq.enabled", havingValue = "true")
public class NotificationRabbitConfig {
    @Bean
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler notificationTaskScheduler() {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("cl-notify-publish-");
        return scheduler;
    }

    @Bean
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler notificationMonitorScheduler() {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("cl-notify-monitor-");
        return scheduler;
    }

    @Bean
    public CachingConnectionFactory notificationConnectionFactory(Environment env) {
        String user = env.getProperty("spring.rabbitmq.username", "");
        String password = env.getProperty("spring.rabbitmq.password", "");
        if (!StringUtils.hasText(user) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("RabbitMQ enabled: RABBITMQ_USER and RABBITMQ_PASSWORD required");
        }
        CachingConnectionFactory factory = new CachingConnectionFactory(
                env.getProperty("spring.rabbitmq.host", "127.0.0.1"),
                env.getProperty("spring.rabbitmq.port", Integer.class, 5672));
        factory.setUsername(user);
        factory.setPassword(password);
        factory.setVirtualHost(env.getProperty("spring.rabbitmq.virtual-host", "/"));
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        factory.getRabbitConnectionFactory().setConnectionTimeout(2000);
        factory.getRabbitConnectionFactory().setRequestedHeartbeat(10);
        return factory;
    }

    @Bean
    public RabbitTemplate notificationRabbitTemplate(CachingConnectionFactory factory) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMandatory(true);
        return template;
    }

    @Bean
    public RabbitAdmin notificationRabbitAdmin(CachingConnectionFactory factory) {
        return new RabbitAdmin(factory);
    }

    @Bean
    public Declarables notificationTopology() {
        DirectExchange exchange = new DirectExchange(NotificationRabbitTopology.EXCHANGE, true, false);
        DirectExchange error = new DirectExchange(NotificationRabbitTopology.ERROR_EXCHANGE, true, false);
        Queue queue = new Queue(NotificationRabbitTopology.QUEUE, true);
        Queue dead = new Queue(NotificationRabbitTopology.ERROR_QUEUE, true);
        return new Declarables(exchange, error, queue, dead,
                BindingBuilder.bind(queue).to(exchange).with(NotificationRabbitTopology.ROUTING_KEY),
                BindingBuilder.bind(dead).to(error).with(NotificationRabbitTopology.ROUTING_KEY));
    }

    @Bean
    public SimpleRabbitListenerContainerFactory notificationListenerFactory(CachingConnectionFactory factory) {
        SimpleRabbitListenerContainerFactory listener = new SimpleRabbitListenerContainerFactory();
        listener.setConnectionFactory(factory);
        listener.setConcurrentConsumers(1);
        listener.setMaxConcurrentConsumers(1);
        listener.setPrefetchCount(20);
        listener.setMissingQueuesFatal(false);
        listener.setDefaultRequeueRejected(true);
        return listener;
    }
}
