package com.apextick.notification.messaging;

import com.apextick.notification.log.NotificationLogService;
import com.apextick.notification.messaging.payload.BookingConfirmedPayload;
import com.apextick.notification.messaging.payload.OrderCancelledPayload;
import com.apextick.notification.messaging.payload.SeatEventPayload;
import com.apextick.notification.service.NotificationService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Consumes envelopes; on failure records a FAILED log (own tx) and rethrows to trigger retry/DLQ. */
@Component
public class NotificationListener {

    private final EnvelopeParser parser;
    private final NotificationService service;
    private final NotificationLogService logs;

    public NotificationListener(EnvelopeParser parser, NotificationService service, NotificationLogService logs) {
        this.parser = parser;
        this.service = service;
        this.logs = logs;
    }

    @RabbitListener(queues = RabbitTopologyConfig.Q_BOOKING)
    public void onBookingConfirmed(Message message) {
        EventEnvelope<BookingConfirmedPayload> env = parser.parse(message.getBody(), BookingConfirmedPayload.class);
        try {
            service.handleBookingConfirmed(env);
        } catch (RuntimeException e) {
            logs.recordFailed(env.eventId(), env.type(),
                    env.payload() == null ? null : env.payload().userEmail(), e.getMessage());
            throw e;
        }
    }

    @RabbitListener(queues = RabbitTopologyConfig.Q_CANCEL)
    public void onOrderCancelled(Message message) {
        EventEnvelope<OrderCancelledPayload> env = parser.parse(message.getBody(), OrderCancelledPayload.class);
        try {
            service.handleOrderCancelled(env);
        } catch (RuntimeException e) {
            logs.recordFailed(env.eventId(), env.type(),
                    env.payload() == null ? null : env.payload().userEmail(), e.getMessage());
            throw e;
        }
    }

    @RabbitListener(queues = RabbitTopologyConfig.Q_SEAT)
    public void onSeatEvent(Message message) {
        EventEnvelope<SeatEventPayload> env = parser.parse(message.getBody(), SeatEventPayload.class);
        service.handleSeatEvent(env);
    }
}
