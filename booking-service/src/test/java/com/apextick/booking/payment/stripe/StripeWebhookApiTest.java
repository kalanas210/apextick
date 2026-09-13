package com.apextick.booking.payment.stripe;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.order.dto.OrderResponse;
import com.apextick.booking.payment.PaymentGatewaySpies;
import com.apextick.booking.payment.model.RefundResult;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    /** A Stripe charge for an order, as the pay endpoint leaves it while the buyer finishes paying. */
    private record Charge(UUID orderId, UUID paymentId, String intentId, long amountMinor, String currency,
                          List<Long> seatIds) {
    }

    /** Seats held and an order created, with the intent the pay endpoint would have made for it. */
    private Charge pendingStripeOrder(String buyer) {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(2).toList();
        CurrentUser user = new CurrentUser(buyer, buyer, buyer + "@apextick.local", "Webhook Buyer", Set.of("user"));
        holdService.hold(SLUG, seatIds, user);
        OrderResponse order = orderService.create(new CreateOrderRequest(eventId, seatIds),
                "webhook-order-" + UUID.randomUUID(), user);
        return stripeCharge(UUID.fromString(order.id()), order.total(), order.currency(), seatIds);
    }

    /** A second intent for the same order: another tab, or a 3-D Secure challenge finished late. */
    private Charge anotherChargeFor(Charge first) {
        return stripeCharge(first.orderId(), StripeAmounts.fromMinorUnits(first.amountMinor(), first.currency()),
                first.currency(), first.seatIds());
    }

    private Charge stripeCharge(UUID orderId, BigDecimal amount, String currency, List<Long> seatIds) {
        UUID paymentId = UUID.randomUUID();
        String intentId = "pi_" + paymentId.toString().replace("-", "");
        jdbc.sql("""
                        INSERT INTO payments (id, order_id, provider, status, amount, currency, idempotency_key,
                                              provider_ref, created_at)
                        VALUES (:id, :orderId, 'STRIPE', 'REQUIRES_ACTION', :amount, :currency, :key, :ref, now())
                        """)
                .param("id", paymentId).param("orderId", orderId)
                .param("amount", amount).param("currency", currency)
                .param("key", "pay-" + paymentId).param("ref", intentId)
                .update();
        return new Charge(orderId, paymentId, intentId, StripeAmounts.toMinorUnits(amount, currency), currency,
                seatIds);
    }

    private String event(Charge c, String eventId, String type) {
        return StripeWebhooks.paymentIntentEvent(eventId, type, c.intentId(), c.amountMinor(), c.currency(),
                c.paymentId().toString());
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
    private void loseTheHold(Charge c) {
        jdbc.sql("UPDATE seats SET held_until = now() - INTERVAL '1 minute' WHERE id IN (:ids)")
                .param("ids", c.seatIds()).update();
        c.seatIds().forEach(holdService::releaseExpired);
    }

    private String orderStatus(Charge c) {
        return orderRepository.findById(c.orderId()).orElseThrow().getStatus().name();
    }

    private String paymentStatus(Charge c) {
        return jdbc.sql("SELECT status FROM payments WHERE id = :id")
                .param("id", c.paymentId()).query(String.class).single();
    }

    private String failureCode(Charge c) {
        return jdbc.sql("SELECT failure_code FROM payments WHERE id = :id")
                .param("id", c.paymentId()).query(String.class).single();
    }

    private long deliveriesRecorded(String eventId) {
        return jdbc.sql("SELECT count(*) FROM payment_webhook_events WHERE external_event_id = :id")
                .param("id", eventId).query(Long.class).single();
    }

    private String outcomeRecorded(String eventId) {
        return jdbc.sql("SELECT outcome FROM payment_webhook_events WHERE external_event_id = :id")
                .param("id", eventId).query(String.class).single();
    }

    private static String refundKeyOf(Charge c) {
        return "refund:" + c.paymentId();
    }

    @Test
    void a_delivery_whose_signature_does_not_verify_is_refused_and_changes_nothing() throws Exception {
        Charge c = pendingStripeOrder("webhook-forged");

        deliver(event(c, "evt_forged", "payment_intent.succeeded"), "whsec_somebody_else")
                .andExpect(status().isBadRequest());

        assertThat(orderStatus(c)).isEqualTo("PENDING_PAYMENT");
        assertThat(paymentStatus(c)).isEqualTo("REQUIRES_ACTION");
        assertThat(deliveriesRecorded("evt_forged")).isZero();
    }

    @Test
    void an_event_type_nothing_acts_on_is_acknowledged_and_ignored() throws Exception {
        Charge c = pendingStripeOrder("webhook-ignored");

        deliver(event(c, "evt_ignored", "payment_intent.created")).andExpect(status().isOk());

        assertThat(paymentStatus(c)).isEqualTo("REQUIRES_ACTION");
        assertThat(deliveriesRecorded("evt_ignored")).isZero();
    }

    @Test
    void a_succeeded_charge_books_the_seats_and_issues_the_tickets() throws Exception {
        Charge c = pendingStripeOrder("webhook-paid");

        deliver(event(c, "evt_paid", "payment_intent.succeeded")).andExpect(status().isOk());

        assertThat(orderStatus(c)).isEqualTo("PAID");
        assertThat(paymentStatus(c)).isEqualTo("SUCCEEDED");
        assertThat(ticketRepository.findByOrderId(c.orderId())).hasSize(2);
        assertThat(seatRepository.findByIdsWithLayout(c.seatIds()))
                .allMatch(s -> s.getStatus() == SeatStatus.BOOKED);
        assertThat(outcomeRecorded("evt_paid")).isEqualTo("SUCCEEDED");
    }

    @Test
    void a_redelivered_event_is_applied_once() throws Exception {
        Charge c = pendingStripeOrder("webhook-redelivered");
        String payload = event(c, "evt_redelivered", "payment_intent.succeeded");

        deliver(payload).andExpect(status().isOk());
        deliver(payload).andExpect(status().isOk());

        assertThat(deliveriesRecorded("evt_redelivered")).isEqualTo(1);
        assertThat(ticketRepository.findByOrderId(c.orderId())).hasSize(2);
    }

    @Test
    void a_failed_charge_leaves_the_order_payable() throws Exception {
        Charge c = pendingStripeOrder("webhook-declined");

        deliver(event(c, "evt_declined", "payment_intent.payment_failed")).andExpect(status().isOk());

        assertThat(paymentStatus(c)).isEqualTo("FAILED");
        assertThat(orderStatus(c)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void a_charge_whose_seats_were_lost_is_refunded_once_and_its_order_cancelled() throws Exception {
        Charge c = pendingStripeOrder("webhook-lost");
        loseTheHold(c);
        String payload = event(c, "evt_lost", "payment_intent.succeeded");

        deliver(payload).andExpect(status().isOk());
        deliver(payload).andExpect(status().isOk());

        verify(stripe, times(1)).refund(eq(c.intentId()), any(), eq(c.currency()), eq(refundKeyOf(c)));
        assertThat(orderStatus(c)).isEqualTo("CANCELLED");
        assertThat(paymentStatus(c)).isEqualTo("REFUNDED");
        assertThat(failureCode(c)).isEqualTo("seats_lost");
        assertThat(outcomeRecorded("evt_lost")).isEqualTo("REFUNDED");
        assertThat(ticketRepository.findByOrderId(c.orderId())).isEmpty();
    }

    /**
     * The payment window closed while the buyer was still in a 3-D Secure challenge, and the charge
     * went through anyway. There is nothing left for it to buy, so it is refunded and the delivery
     * acknowledged. Refusing it with a 409 only had Stripe redeliver it for days, the customer
     * charged the whole time.
     */
    @Test
    void a_charge_that_lands_after_its_order_expired_is_refunded_instead_of_refused() throws Exception {
        Charge c = pendingStripeOrder("webhook-late");
        orderService.expire(c.orderId());
        String payload = event(c, "evt_late", "payment_intent.succeeded");

        deliver(payload).andExpect(status().isOk());
        deliver(payload).andExpect(status().isOk());

        verify(stripe, times(1)).refund(eq(c.intentId()), any(), eq(c.currency()), eq(refundKeyOf(c)));
        assertThat(orderStatus(c)).isEqualTo("EXPIRED");
        assertThat(paymentStatus(c)).isEqualTo("REFUNDED");
        assertThat(failureCode(c)).isEqualTo("order_closed");
        assertThat(deliveriesRecorded("evt_late")).isEqualTo(1);
    }

    @Test
    void a_second_charge_for_an_order_that_is_already_paid_is_refunded() throws Exception {
        Charge first = pendingStripeOrder("webhook-twice");
        Charge second = anotherChargeFor(first);

        deliver(event(first, "evt_first_charge", "payment_intent.succeeded")).andExpect(status().isOk());
        deliver(event(second, "evt_second_charge", "payment_intent.succeeded")).andExpect(status().isOk());

        verify(stripe).refund(eq(second.intentId()), any(), eq(second.currency()), eq(refundKeyOf(second)));
        verify(stripe, never()).refund(eq(first.intentId()), any(), any(), any());
        assertThat(paymentStatus(second)).isEqualTo("REFUNDED");
        assertThat(failureCode(second)).isEqualTo("duplicate_charge");
        assertThat(paymentStatus(first)).isEqualTo("SUCCEEDED");
        assertThat(orderStatus(first)).isEqualTo("PAID");
        assertThat(ticketRepository.findByOrderId(first.orderId())).hasSize(2);
    }

    @Test
    void a_refund_the_provider_refuses_is_left_marked_as_still_owed() throws Exception {
        doReturn(new RefundResult(false, null, "{\"error\":{\"code\":\"charge_disputed\"}}"))
                .when(stripe).refund(any(), any(), any(), any());
        Charge c = pendingStripeOrder("webhook-refused");
        loseTheHold(c);

        deliver(event(c, "evt_refused", "payment_intent.succeeded")).andExpect(status().isOk());

        assertThat(paymentStatus(c)).isEqualTo("REFUND_REQUIRED");
        assertThat(orderStatus(c)).isEqualTo("CANCELLED");
        assertThat(outcomeRecorded("evt_refused")).isEqualTo("REFUND_REQUIRED");
    }
}
