package com.campuslink.module.notification.infrastructure.messaging;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.AMQP;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local operator command; never registers a web endpoint or logs credentials. */
public final class NotificationReplayMain {
    private NotificationReplayMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: NotificationReplayMain <eventId>");
        }
        String eventId = UUID.fromString(args[0]).toString();
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(System.getenv().getOrDefault("RABBITMQ_HOST", "127.0.0.1"));
        factory.setPort(Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672")));
        factory.setVirtualHost(System.getenv().getOrDefault("RABBITMQ_VHOST", "/"));
        factory.setUsername(required("RABBITMQ_USER"));
        factory.setPassword(required("RABBITMQ_PASSWORD"));
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            channel.confirmSelect();
            AtomicBoolean returned = new AtomicBoolean();
            channel.addReturnListener(message -> returned.set(true));
            long count = channel.messageCount(NotificationRabbitTopology.ERROR_QUEUE);
            if (count > 10000) {
                throw new IllegalStateException("More than 10000 dead messages: inspect backlog before replay");
            }
            boolean replayed = false;
            try {
                for (long index = 0; index < count; index++) {
                    var original = channel.basicGet(NotificationRabbitTopology.ERROR_QUEUE, false);
                    if (original == null) {
                        break;
                    }
                    if (eventId.equals(original.getProps().getMessageId())) {
                        var properties = new AMQP.BasicProperties.Builder()
                                .contentType("application/json").deliveryMode(2).messageId(eventId).build();
                        channel.basicPublish(NotificationRabbitTopology.EXCHANGE,
                                NotificationRabbitTopology.ROUTING_KEY, true, properties, original.getBody());
                        channel.waitForConfirmsOrDie(3000);
                        if (returned.get()) {
                            throw new IllegalStateException("Replay unroutable; original retained");
                        }
                        channel.basicAck(original.getEnvelope().getDeliveryTag(), false);
                        replayed = true;
                        break;
                    }
                    // Keep unmatched deliveries unacked until the scan finishes, avoiding head-of-queue starvation.
                }
            } finally {
                channel.basicNack(0, true, true);
            }
            if (!replayed) {
                throw new IllegalArgumentException("Event not found in dead queue: " + eventId);
            }
            System.out.println("Replayed event " + eventId);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Environment variable required: " + name);
        }
        return value;
    }
}
