package com.apextick.booking.payment.stripe;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.order.dto.OrderResponse;
import com.apextick.booking.payment.PaymentGatewaySpies;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.StripeWebhooks;
import com.apextick.booking.ticket.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Stripe webhook over HTTP, signed and verified for real, against a real database.
 *
 * <p>The webhook is what confirms a charge the browser finished -- a 3-D Secure challenge, a
 * client-confirmed intent -- and until this class no test had delivered one: the gateway's unit
 * test checks signatures in isolation, and every payment test pays through the mock gateway's
 * synchronous path. Each test here leaves an order the way the pay endpoint leaves one waiting on
 * Stripe (a REQUIRES_ACTION payment carrying the intent's id) and then posts what Stripe would.
 */
class StripeWebhookApiTest extends PaymentGatewaySpies {

    private static final String SLUG = "bengaluru-kolkata-night";

    @Autowired MockMvc mvc;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired JdbcClient jdbc;

    /** An order waiting on a Stripe charge. */
    private record Pending(UUID orderId, UUID paymentId, String intentId, long amountMinor, String currency,
                           List<Long> seatIds) {
    }

    /** Seats held, an order created, and the intent the pay endpoint would have created for it. */
    private Pending pendingStripeOrder(String buyer) {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(2).toList();
        CurrentUser user = new CurrentUser(buyer, buyer, buyer + "@apextick.local", "Webhook Buyer", Set.of("user"));
        holdService.hold(SLUG, seatIds, user);
        OrderResponse order = orderService.create(new CreateOrderRequest(eventId, seatIds),
                "webhook-order-" + UUID.randomUUID(), user);

        UUID paymentId = UUID.randomUUID();
        String intentId = "pi_" + paymentId.toString().replace("-", "");
        jdbc.sql("""
                        INSERT INTO payments (id, order_id, provider, status, amount, currency, idempotency_key,
                                              provider_ref, created_at)
                        VALUES (:id, :orderId, 'STRIPE', 'REQUIRES_ACTION', :amount, :currency, :key, :ref, now())
                        """)
                .param("id", paymentId).param("orderId", UUID.fromString(order.id()))
                .param("amount", order.total()).param("currency", order.currency())
                .param("key", "pay-" + paymentId).param("ref", intentId)
                .update();
        return new Pending(UUID.fromString(order.id()), paymentId, intentId,
                StripeAmounts.toMinorUnits(order.total(), order.currency()), order.currency(), seatIds);
    }

    private String event(Pending p, String eventId, String type) {
        return StripeWebhooks.paymentIntentEvent(eventId, type, p.intentId(), p.amountMinor(), p.currency(),
                p.paymentId().toString());
    }

    private ResultActions deliver(String payload) throws Exception {
        return deliver(payload, WEBHOOK_SECRET);
    }

    private ResultActions deliver(String payload, String signedWith) throws Exception {
        return mvc.perform(post("/api/payments/stripe/webhook")
                .header("Stripe-Signature", StripeWebhooks.signatureHeader(payload, signedWith))
                .contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    /** The hold lapses and the expiry listener frees the seats, the way it happens mid-3-D Secure. */
    private void loseTheHold(Pending p) {
        jdbc.sql("UPDATE seats SET held_until = now() - INTERVAL '1 minute' WHERE id IN (:ids)")
                .param("ids", p.seatIds()).update();
        p.seatIds().forEach(holdService::releaseExpired);
    }

    private String orderStatus(Pending p) {
        return orderRepository.findById(p.orderId()).orElseThrow().getStatus().name();
    }

    private String paymentStatus(Pending p) {
        return jdbc.sql("SELECT status FROM payments WHERE id = :id")
                .param("id", p.paymentId()).query(String.class).single();
    }

    private long deliveriesRecorded(String eventId) {
        return jdbc.sql("SELECT count(*) FROM payment_webhook_events WHERE external_event_id = :id")
                .param("id", eventId).query(Long.class).single();
    }

    private String outcomeRecorded(String eventId) {
        return jdbc.sql("SELECT outcome FROM payment_webhook_events WHERE external_event_id = :id")
                .param("id", eventId).query(String.class).single();
    }

    @Test
    void a_delivery_whose_signature_does_not_verify_is_refused_and_changes_nothing() throws Exception {
        Pending p = pendingStripeOrder("webhook-forged");

        deliver(event(p, "evt_forged", "payment_intent.succeeded"), "whsec_somebody_else")
                .andExpect(status().isBadRequest());

        assertThat(orderStatus(p)).isEqualTo("PENDING_PAYMENT");
        assertThat(paymentStatus(p)).isEqualTo("REQUIRES_ACTION");
        assertThat(deliveriesRecorded("evt_forged")).isZero();
    }

    @Test
    void an_event_type_nothing_acts_on_is_acknowledged_and_ignored() throws Exception {
        Pending p = pendingStripeOrder("webhook-ignored");

        deliver(event(p, "evt_ignored", "payment_intent.created")).andExpect(status().isOk());

        assertThat(paymentStatus(p)).isEqualTo("REQUIRES_ACTION");
        assertThat(deliveriesRecorded("evt_ignored")).isZero();
    }

    @Test
    void a_succeeded_charge_books_the_seats_and_issues_the_tickets() throws Exception {
        Pending p = pendingStripeOrder("webhook-paid");

        deliver(event(p, "evt_paid", "payment_intent.succeeded")).andExpect(status().isOk());

        assertThat(orderStatus(p)).isEqualTo("PAID");
        assertThat(paymentStatus(p)).isEqualTo("SUCCEEDED");
        assertThat(ticketRepository.findByOrderId(p.orderId())).hasSize(2);
        assertThat(seatRepository.findByIdsWithLayout(p.seatIds()))
                .allMatch(s -> s.getStatus() == SeatStatus.BOOKED);
        assertThat(outcomeRecorded("evt_paid")).isEqualTo("SUCCEEDED");
    }

    @Test
    void a_redelivered_event_is_applied_once() throws Exception {
        Pending p = pendingStripeOrder("webhook-redelivered");
        String payload = event(p, "evt_redelivered", "payment_intent.succeeded");

        deliver(payload).andExpect(status().isOk());
        deliver(payload).andExpect(status().isOk());

        assertThat(deliveriesRecorded("evt_redelivered")).isEqualTo(1);
        assertThat(ticketRepository.findByOrderId(p.orderId())).hasSize(2);
    }

    @Test
    void a_failed_charge_leaves_the_order_payable() throws Exception {
        Pending p = pendingStripeOrder("webhook-declined");

        deliver(event(p, "evt_declined", "payment_intent.payment_failed")).andExpect(status().isOk());

        assertThat(paymentStatus(p)).isEqualTo("FAILED");
        assertThat(orderStatus(p)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void a_charge_whose_seats_were_lost_is_refunded_and_its_order_cancelled() throws Exception {
        Pending p = pendingStripeOrder("webhook-lost");
        loseTheHold(p);

        deliver(event(p, "evt_lost", "payment_intent.succeeded")).andExpect(status().isOk());

        verify(stripe).refund(eq(p.intentId()), any(), eq(p.currency()), any());
        assertThat(orderStatus(p)).isEqualTo("CANCELLED");
        assertThat(paymentStatus(p)).isEqualTo("REFUND_REQUIRED");
        assertThat(ticketRepository.findByOrderId(p.orderId())).isEmpty();
    }
}
