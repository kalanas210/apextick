package com.apextick.notification.service;

import com.apextick.notification.idempotency.IdempotentConsumer;
import com.apextick.notification.log.NotificationLogService;
import com.apextick.notification.mail.EmailService;
import com.apextick.notification.mail.OutboundEmail;
import com.apextick.notification.mail.TemplateRenderer;
import com.apextick.notification.messaging.EventEnvelope;
import com.apextick.notification.messaging.payload.BookingConfirmedPayload;
import com.apextick.notification.messaging.payload.OrderCancelledPayload;
import com.apextick.notification.messaging.payload.SeatEventPayload;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final IdempotentConsumer idempotent;
    private final EmailService email;
    private final TemplateRenderer templates;
    private final NotificationLogService logs;
    private final MeterRegistry meters;
    private final String publicBaseUrl;
    private final boolean seatHeldEmail;

    public NotificationService(IdempotentConsumer idempotent, EmailService email, TemplateRenderer templates,
                               NotificationLogService logs, MeterRegistry meters,
                               @Value("${app.public-base-url}") String publicBaseUrl,
                               @Value("${app.notifications.seat-held-email}") boolean seatHeldEmail) {
        this.idempotent = idempotent;
        this.email = email;
        this.templates = templates;
        this.logs = logs;
        this.meters = meters;
        this.publicBaseUrl = publicBaseUrl;
        this.seatHeldEmail = seatHeldEmail;
    }

    public void handleBookingConfirmed(EventEnvelope<BookingConfirmedPayload> env) {
        BookingConfirmedPayload p = env.payload();
        boolean fresh = idempotent.runOnce(env.eventId(), env.type(), () -> {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("booking", p);
            model.put("ticketsUrl", orderUrl(p.orderId()));
            String subject = "You're in — " + p.event().name();
            email.send(new OutboundEmail(p.userEmail(), subject, templates.render("email/booking-confirmed", model)));
            logs.recordSent(env.eventId(), env.type(), p.userEmail(), subject);
            meters.counter("notifications_sent_total", "type", env.type()).increment();
        });
        countEvent(env.type(), fresh);
    }

    public void handleOrderCancelled(EventEnvelope<OrderCancelledPayload> env) {
        OrderCancelledPayload p = env.payload();
        boolean fresh = idempotent.runOnce(env.eventId(), env.type(), () -> {
            if (!StringUtils.hasText(p.userEmail())) {
                logs.recordSkipped(env.eventId(), env.type(), "no recipient");
                return;
            }
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("cancel", p);
            String subject = "Your ApexTick order " + p.orderNumber() + " was cancelled";
            email.send(new OutboundEmail(p.userEmail(), subject, templates.render("email/order-cancelled", model)));
            logs.recordSent(env.eventId(), env.type(), p.userEmail(), subject);
            meters.counter("notifications_sent_total", "type", env.type()).increment();
        });
        countEvent(env.type(), fresh);
    }

    public void handleSeatEvent(EventEnvelope<SeatEventPayload> env) {
        // seat.held / seat.released: log + metric only (the UI shows these live over WebSocket)
        idempotent.runOnce(env.eventId(), env.type(), () -> {
            if (seatHeldEmail && "seat.held".equals(env.type())) {
                log.info("(seat-held email disabled by default) seat {} event {}",
                        env.payload().seatId(), env.payload().eventId());
            }
            log.debug("seat event {} for seat {}", env.type(), env.payload().seatId());
        });
        meters.counter("notifications_events_total", "type", env.type()).increment();
    }

    /** The frontend's order page (app/orders/[id]), keyed by the order's UUID; it lists the tickets. */
    private String orderUrl(String orderId) {
        return UriComponentsBuilder.fromUriString(publicBaseUrl)
                .pathSegment("orders", orderId)
                .toUriString();
    }

    private void countEvent(String type, boolean fresh) {
        meters.counter("notifications_events_total", "type", type).increment();
        if (!fresh) {
            meters.counter("notifications_duplicates_total", "type", type).increment();
        }
    }
}
