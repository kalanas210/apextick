package com.apextick.notification.service;

import com.apextick.notification.idempotency.IdempotentConsumer;
import com.apextick.notification.log.NotificationLogService;
import com.apextick.notification.mail.EmailService;
import com.apextick.notification.mail.OutboundEmail;
import com.apextick.notification.mail.TemplateRenderer;
import com.apextick.notification.messaging.EventEnvelope;
import com.apextick.notification.messaging.payload.BookingConfirmedPayload;
import com.apextick.notification.messaging.payload.EventCancelledPayload;
import com.apextick.notification.messaging.payload.OrderCancelledPayload;
import com.apextick.notification.messaging.payload.PaymentFailedPayload;
import com.apextick.notification.messaging.payload.PaymentRefundedPayload;
import com.apextick.notification.messaging.payload.SeatEventPayload;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final DateTimeFormatter KICKOFF = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH);

    /** A payment window is minutes long, so the time it closes is given plainly, in UTC, and said to be UTC. */
    private static final DateTimeFormatter HELD_UNTIL =
            DateTimeFormatter.ofPattern("HH:mm 'UTC on' d MMM", Locale.ENGLISH).withZone(ZoneOffset.UTC);

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
            if ("EVENT_CANCELLED".equals(p.reason())) {
                // its buyer hears from event.cancelled, which says the event is off and not only the order
                logs.recordSkipped(env.eventId(), env.type(), "told by event.cancelled");
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

    /**
     * Sent only once the provider has accepted the refund. Telling a customer their money is on its
     * way while the provider is still refusing it is the promise the old cancellation email made.
     */
    public void handlePaymentRefunded(EventEnvelope<PaymentRefundedPayload> env) {
        PaymentRefundedPayload p = env.payload();
        boolean fresh = idempotent.runOnce(env.eventId(), env.type(), () -> {
            if (!StringUtils.hasText(p.userEmail())) {
                logs.recordSkipped(env.eventId(), env.type(), "no recipient");
                return;
            }
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("refund", p);
            model.put("amount", money(p.amount(), p.currency()));
            model.put("why", refundExplanation(p.reason()));
            model.put("orderUrl", orderUrl(p.orderId()));
            String subject = "Your refund for ApexTick order " + p.orderNumber();
            email.send(new OutboundEmail(p.userEmail(), subject, templates.render("email/payment-refunded", model)));
            logs.recordSent(env.eventId(), env.type(), p.userEmail(), subject);
            meters.counter("notifications_sent_total", "type", env.type()).increment();
        });
        countEvent(env.type(), fresh);
    }

    /**
     * A payment that failed after the buyer had left the checkout: a 3-D Secure challenge never answered,
     * a card refused a minute later. The link goes back to the checkout, which says plainly whether the
     * order can still be paid.
     */
    public void handlePaymentFailed(EventEnvelope<PaymentFailedPayload> env) {
        PaymentFailedPayload p = env.payload();
        boolean fresh = idempotent.runOnce(env.eventId(), env.type(), () -> {
            if (!StringUtils.hasText(p.userEmail())) {
                logs.recordSkipped(env.eventId(), env.type(), "no recipient");
                return;
            }
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("failed", p);
            model.put("amount", money(p.total(), p.currency()));
            model.put("why", failureExplanation(p.failureCode()));
            model.put("heldUntil", p.expiresAt() == null ? null : HELD_UNTIL.format(p.expiresAt()));
            model.put("checkoutUrl", checkoutUrl(p.orderId()));
            String subject = "Your payment for ApexTick order " + p.orderNumber() + " did not go through";
            email.send(new OutboundEmail(p.userEmail(), subject, templates.render("email/payment-failed", model)));
            logs.recordSent(env.eventId(), env.type(), p.userEmail(), subject);
            meters.counter("notifications_sent_total", "type", env.type()).increment();
        });
        countEvent(env.type(), fresh);
    }

    /** One email per order an event cancellation touched, saying what happens to that order. */
    public void handleEventCancelled(EventEnvelope<EventCancelledPayload> env) {
        EventCancelledPayload p = env.payload();
        boolean fresh = idempotent.runOnce(env.eventId(), env.type(), () -> {
            if (!StringUtils.hasText(p.userEmail())) {
                logs.recordSkipped(env.eventId(), env.type(), "no recipient");
                return;
            }
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("cancelled", p);
            model.put("when", kickoff(p.startsAt(), p.timeZone()));
            model.put("amount", money(p.total(), p.currency()));
            model.put("orderUrl", orderUrl(p.orderId()));
            String subject = p.eventName() + " has been cancelled";
            email.send(new OutboundEmail(p.userEmail(), subject, templates.render("email/event-cancelled", model)));
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

    /**
     * "USD 105.00": the amount to its currency's own minor units. The number can arrive as 105.0
     * once it has been through a JSON tree, and a refund email is the last place to show that.
     */
    static String money(BigDecimal amount, String currency) {
        if (amount == null) {
            return currency == null ? "" : currency;
        }
        int places = 2;
        try {
            places = Math.max(0, Currency.getInstance(currency).getDefaultFractionDigits());
        } catch (RuntimeException e) {
            // an unknown or missing code keeps two places
        }
        String value = amount.setScale(places, RoundingMode.HALF_UP).toPlainString();
        return currency == null ? value : currency + " " + value;
    }

    /** "Sun 19 Jul 2026, 15:00": when the event was due, on its own clock rather than the mail server's. */
    static String kickoff(Instant startsAt, String timeZone) {
        if (startsAt == null) {
            return "the date announced";
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(timeZone == null || timeZone.isBlank() ? "UTC" : timeZone);
        } catch (RuntimeException e) {
            zone = ZoneOffset.UTC;
        }
        return KICKOFF.withZone(zone).format(startsAt);
    }

    /** Why the money went back, in words for the customer rather than booking-service's codes. */
    static String refundExplanation(String reason) {
        return switch (reason == null ? "" : reason) {
            case "box_office_refund" -> "The box office refunded this order, so its tickets no longer admit anyone.";
            case "event_cancelled" -> "The event was cancelled, so your order has been refunded in full.";
            case "seats_lost" -> "Your seats were taken before your payment could be confirmed, so the order was cancelled.";
            case "order_closed" -> "Your payment went through after the order had expired or been cancelled, "
                    + "so it could not buy the seats.";
            case "duplicate_charge" -> "The order had already been paid by another payment, so this second charge "
                    + "has been returned. Your tickets are not affected.";
            case "amount_mismatch" -> "The amount charged did not match the order, so the charge has been returned "
                    + "in full. The order has not been paid.";
            default -> "The payment has been returned to you.";
        };
    }

    /** Why a card was refused, from the provider's decline code, in words a buyer can act on. */
    static String failureExplanation(String code) {
        return switch (code == null ? "" : code) {
            case "insufficient_funds" -> "The card had insufficient funds.";
            case "expired_card" -> "The card has expired.";
            case "incorrect_cvc" -> "The card's security code was wrong.";
            case "authentication_required", "payment_intent_authentication_failure" ->
                    "Your bank's security check was not completed.";
            case "card_declined", "generic_decline", "do_not_honor" -> "Your bank declined the card.";
            default -> "The payment was not completed.";
        };
    }

    /** The frontend's order page (app/orders/[id]), keyed by the order's UUID; it lists the tickets. */
    private String orderUrl(String orderId) {
        return UriComponentsBuilder.fromUriString(publicBaseUrl)
                .pathSegment("orders", orderId)
                .toUriString();
    }

    /** The frontend's checkout for an order (app/checkout/[orderId]). */
    private String checkoutUrl(String orderId) {
        return UriComponentsBuilder.fromUriString(publicBaseUrl)
                .pathSegment("checkout", orderId)
                .toUriString();
    }

    private void countEvent(String type, boolean fresh) {
        meters.counter("notifications_events_total", "type", type).increment();
        if (!fresh) {
            meters.counter("notifications_duplicates_total", "type", type).increment();
        }
    }
}
