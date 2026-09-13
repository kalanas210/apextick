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
    public static final String Q_REFUND = "notifications.payment-refunded";
    public static final String Q_PAYMENT_FAILED = "notifications.payment-failed";
    public static final String Q_EVENT_CANCELLED = "notifications.event-cancelled";
    public static final String Q_SEAT = "notifications.seat-events";

    @Bean
    Declarables notificationTopology() {
        TopicExchange events = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
        DirectExchange dlx = ExchangeBuilder.directExchange(DLX).durable(true).build();

        Queue booking = workQueue(Q_BOOKING);
        Queue cancel = workQueue(Q_CANCEL);
        Queue refund = workQueue(Q_REFUND);
        Queue paymentFailed = workQueue(Q_PAYMENT_FAILED);
        Queue eventCancelled = workQueue(Q_EVENT_CANCELLED);
        Queue seat = workQueue(Q_SEAT);
        Queue bookingDlq = new Queue(Q_BOOKING + ".dlq", true);
        Queue cancelDlq = new Queue(Q_CANCEL + ".dlq", true);
        Queue refundDlq = new Queue(Q_REFUND + ".dlq", true);
        Queue paymentFailedDlq = new Queue(Q_PAYMENT_FAILED + ".dlq", true);
        Queue eventCancelledDlq = new Queue(Q_EVENT_CANCELLED + ".dlq", true);
        Queue seatDlq = new Queue(Q_SEAT + ".dlq", true);

        return new Declarables(
                events, dlx, booking, cancel, refund, paymentFailed, eventCancelled, seat,
                bookingDlq, cancelDlq, refundDlq, paymentFailedDlq, eventCancelledDlq, seatDlq,
                BindingBuilder.bind(booking).to(events).with("booking.confirmed"),
                BindingBuilder.bind(cancel).to(events).with("order.cancelled"),
                BindingBuilder.bind(refund).to(events).with("payment.refunded"),
                BindingBuilder.bind(paymentFailed).to(events).with("payment.failed"),
                BindingBuilder.bind(eventCancelled).to(events).with("event.cancelled"),
                BindingBuilder.bind(seat).to(events).with("seat.held"),
                BindingBuilder.bind(seat).to(events).with("seat.released"),
                dlqBinding(bookingDlq, dlx, Q_BOOKING),
                dlqBinding(cancelDlq, dlx, Q_CANCEL),
                dlqBinding(refundDlq, dlx, Q_REFUND),
                dlqBinding(paymentFailedDlq, dlx, Q_PAYMENT_FAILED),
                dlqBinding(eventCancelledDlq, dlx, Q_EVENT_CANCELLED),
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
