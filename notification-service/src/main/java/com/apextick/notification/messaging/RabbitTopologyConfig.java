package com.apextick.notification.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares (idempotently) the topic exchange, per-type work queues bound to it, and a
 * dead-letter exchange + per-queue DLQ so poison messages are parked, not lost.
 */
@Configuration
public class RabbitTopologyConfig {

    public static final String EXCHANGE = "apextick.events";
    public static final String DLX = "apextick.events.dlx";
    public static final String Q_BOOKING = "notifications.booking-confirmed";
    public static final String Q_CANCEL = "notifications.order-cancelled";
    public static final String Q_SEAT = "notifications.seat-events";

    @Bean
    Declarables notificationTopology() {
        TopicExchange events = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
        DirectExchange dlx = ExchangeBuilder.directExchange(DLX).durable(true).build();

        Queue booking = workQueue(Q_BOOKING);
        Queue cancel = workQueue(Q_CANCEL);
        Queue seat = workQueue(Q_SEAT);
        Queue bookingDlq = new Queue(Q_BOOKING + ".dlq", true);
        Queue cancelDlq = new Queue(Q_CANCEL + ".dlq", true);
        Queue seatDlq = new Queue(Q_SEAT + ".dlq", true);

        return new Declarables(
                events, dlx, booking, cancel, seat, bookingDlq, cancelDlq, seatDlq,
                BindingBuilder.bind(booking).to(events).with("booking.confirmed"),
                BindingBuilder.bind(cancel).to(events).with("order.cancelled"),
                BindingBuilder.bind(seat).to(events).with("seat.held"),
                BindingBuilder.bind(seat).to(events).with("seat.released"),
                dlqBinding(bookingDlq, dlx, Q_BOOKING),
                dlqBinding(cancelDlq, dlx, Q_CANCEL),
                dlqBinding(seatDlq, dlx, Q_SEAT));
    }

    private Queue workQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(name)
                .build();
    }

    private Binding dlqBinding(Queue dlq, DirectExchange dlx, String routingKey) {
        return BindingBuilder.bind(dlq).to(dlx).with(routingKey);
    }
}
