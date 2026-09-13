package com.apextick.booking.payment.stripe;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.order.dto.OrderResponse;
import com.apextick.booking.payment.PaymentGatewaySpies;
import com.apextick.booking.payment.RefundReconciler;
import com.apextick.booking.payment.RefundService;
import com.apextick.booking.payment.model.RefundResult;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.StripeWebhooks;
import com.apextick.booking.ticket.Ticket;
import com.apextick.booking.ticket.TicketRepository;
import com.apextick.booking.ticket.TicketStatus;
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
    private static final CurrentUser ADMIN = new CurrentUser("webhook-admin", "webhook-admin",
            "webhook-admin@apextick.local", "Webhook Admin", Set.of("admin"));

    @Autowired MockMvc mvc;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired JdbcClient jdbc;
    @Autowired RefundService refundService;
    @Autowired RefundReconciler reconciler;

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

    /** An order Stripe has already charged for: its seats booked and its tickets issued. */
    private Charge paidStripeOrder(String buyer) throws Exception {
        Charge c = pendingStripeOrder(buyer);
        deliver(event(c, "evt_paid_" + buyer, "payment_intent.succeeded")).andExpect(status().isOk());
        assertThat(orderStatus(c)).isEqualTo("PAID");
        return c;
    }

    private List<TicketStatus> ticketStatuses(Charge c) {
        return ticketRepository.findByOrderId(c.orderId()).stream().map(Ticket::getStatus).toList();
    }

    private List<SeatStatus> seatStatuses(Charge c) {
        return seatRepository.findAllById(c.seatIds()).stream().map(Seat::getStatus).toList();
    }

    private long published(String type, Object aggregateId) {
        return jdbc.sql("SELECT count(*) FROM outbox_events WHERE type = :type AND aggregate_id = :id")
                .param("type", type).param("id", aggregateId.toString()).query(Long.class).single();
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
        // the buyer may have left mid 3-D Secure: they are told, with the way back to the order
        assertThat(published("payment.failed", c.paymentId())).isEqualTo(1);
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

    /**
     * Stripe can send the succeeded event before the pay request that created the intent has stored
     * the intent's id: a server-confirmed charge settles within the same second. The payment id
     * stamped on the intent still finds the payment, which records the reference as it settles.
     */
    @Test
    void a_charge_whose_reference_was_not_recorded_yet_is_matched_by_its_payment_id() throws Exception {
        Charge c = pendingStripeOrder("webhook-early");
        jdbc.sql("UPDATE payments SET provider_ref = NULL, status = 'INITIATED' WHERE id = :id")
                .param("id", c.paymentId()).update();

        deliver(event(c, "evt_early", "payment_intent.succeeded")).andExpect(status().isOk());

        assertThat(orderStatus(c)).isEqualTo("PAID");
        assertThat(paymentStatus(c)).isEqualTo("SUCCEEDED");
        assertThat(jdbc.sql("SELECT provider_ref FROM payments WHERE id = :id")
                .param("id", c.paymentId()).query(String.class).single()).isEqualTo(c.intentId());
        assertThat(outcomeRecorded("evt_early")).isEqualTo("SUCCEEDED");
    }

    @Test
    void a_charge_nothing_here_created_is_recorded_as_unmatched_and_left_alone() throws Exception {
        String payload = StripeWebhooks.paymentIntentEvent("evt_stranger", "payment_intent.succeeded",
                "pi_created_elsewhere", 5000, "usd", null);

        deliver(payload).andExpect(status().isOk());

        assertThat(outcomeRecorded("evt_stranger")).isEqualTo("unmatched");
        verify(stripe, never()).refund(any(), any(), any(), any());
    }

    /**
     * The webhook is the word on what was actually charged. A charge for another sum than the
     * payment -- an intent reused from a different order, a partial capture -- must not buy the
     * order's tickets: the money goes back exactly as taken and the order stays payable.
     */
    @Test
    void a_charge_for_a_different_amount_than_its_payment_is_refunded_and_buys_nothing() throws Exception {
        Charge c = pendingStripeOrder("webhook-short");
        long charged = c.amountMinor() - 100;

        deliver(StripeWebhooks.paymentIntentEvent("evt_short", "payment_intent.succeeded", c.intentId(), charged,
                c.currency(), c.paymentId().toString())).andExpect(status().isOk());

        verify(stripe).refund(eq(c.intentId()), eq(StripeAmounts.fromMinorUnits(charged, c.currency())),
                eq(c.currency()), eq(refundKeyOf(c)));
        assertThat(paymentStatus(c)).isEqualTo("REFUNDED");
        assertThat(failureCode(c)).isEqualTo("amount_mismatch");
        assertThat(orderStatus(c)).isEqualTo("PENDING_PAYMENT");
        assertThat(ticketRepository.findByOrderId(c.orderId())).isEmpty();
    }

    @Test
    void a_charge_in_a_different_currency_than_its_payment_is_refunded_and_buys_nothing() throws Exception {
        Charge c = pendingStripeOrder("webhook-currency");
        String otherCurrency = c.currency().equalsIgnoreCase("USD") ? "GBP" : "USD";

        deliver(StripeWebhooks.paymentIntentEvent("evt_currency", "payment_intent.succeeded", c.intentId(),
                c.amountMinor(), otherCurrency, c.paymentId().toString())).andExpect(status().isOk());

        verify(stripe).refund(eq(c.intentId()), any(), eq(otherCurrency), eq(refundKeyOf(c)));
        assertThat(failureCode(c)).isEqualTo("amount_mismatch");
        assertThat(orderStatus(c)).isEqualTo("PENDING_PAYMENT");
    }

    /**
     * A refund made in Stripe's own dashboard used to leave the tickets it paid for scannable and their
     * seats booked. The charge has gone back, so the order is refunded as the box office would have.
     */
    @Test
    void a_charge_refunded_in_the_stripe_dashboard_refunds_its_order() throws Exception {
        Charge c = paidStripeOrder("webhook-dashboard-refund");

        deliver(StripeWebhooks.chargeRefundedEvent("evt_dashboard_refund", c.intentId(), c.amountMinor(),
                c.amountMinor(), c.currency())).andExpect(status().isOk());

        assertThat(orderStatus(c)).isEqualTo("REFUNDED");
        assertThat(paymentStatus(c)).isEqualTo("REFUNDED");
        assertThat(ticketStatuses(c)).containsOnly(TicketStatus.CANCELLED);
        assertThat(seatStatuses(c)).containsOnly(SeatStatus.AVAILABLE);
        assertThat(outcomeRecorded("evt_dashboard_refund")).isEqualTo("REFUNDED");
        assertThat(published("payment.refunded", c.paymentId())).isEqualTo(1);
        // Stripe has already given the money back, so nothing is asked of it
        verify(stripe, never()).refund(any(), any(), any(), any());
    }

    @Test
    void the_refund_asked_for_here_reported_back_by_stripe_changes_nothing() throws Exception {
        Charge c = paidStripeOrder("webhook-refund-echo");
        refundService.refundOrder(c.orderId(), "Cannot attend", ADMIN);

        deliver(StripeWebhooks.chargeRefundedEvent("evt_refund_echo", c.intentId(), c.amountMinor(),
                c.amountMinor(), c.currency())).andExpect(status().isOk());

        assertThat(orderStatus(c)).isEqualTo("REFUNDED");
        assertThat(paymentStatus(c)).isEqualTo("REFUNDED");
        assertThat(published("payment.refunded", c.paymentId())).isEqualTo(1);
        verify(stripe, times(1)).refund(any(), any(), any(), eq(refundKeyOf(c)));
    }

    @Test
    void a_partial_refund_in_the_dashboard_leaves_the_order_standing() throws Exception {
        Charge c = paidStripeOrder("webhook-partial-refund");

        deliver(StripeWebhooks.chargeRefundedEvent("evt_partial_refund", c.intentId(), c.amountMinor(),
                c.amountMinor() / 2, c.currency())).andExpect(status().isOk());

        assertThat(orderStatus(c)).isEqualTo("PAID");
        assertThat(paymentStatus(c)).isEqualTo("SUCCEEDED");
        assertThat(ticketStatuses(c)).containsOnly(TicketStatus.ISSUED);
        assertThat(deliveriesRecorded("evt_partial_refund")).isZero();
    }

    /**
     * A disputed charge is money the customer's bank is clawing back. The tickets it bought used to
     * go on admitting whoever held them; now they stop, and the order says why it was cancelled.
     */
    @Test
    void a_disputed_charge_voids_the_tickets_it_bought() throws Exception {
        Charge c = paidStripeOrder("webhook-disputed");

        deliver(StripeWebhooks.disputeCreatedEvent("evt_disputed", c.intentId(), c.amountMinor(), c.currency(),
                "fraudulent")).andExpect(status().isOk());

        assertThat(paymentStatus(c)).isEqualTo("DISPUTED");
        assertThat(failureCode(c)).isEqualTo("dispute:fraudulent");
        assertThat(orderStatus(c)).isEqualTo("CANCELLED");
        assertThat(orderRepository.findById(c.orderId()).orElseThrow().getCancelReason()).isEqualTo("CHARGEBACK");
        assertThat(ticketStatuses(c)).containsOnly(TicketStatus.CANCELLED);
        assertThat(seatStatuses(c)).containsOnly(SeatStatus.AVAILABLE);
        assertThat(published("order.cancelled", c.orderId())).isEqualTo(1);
    }

    /** The bank returns the money through the dispute; refunding it as well would return it twice. */
    @Test
    void a_refund_still_owed_is_not_asked_for_again_once_its_charge_is_disputed() throws Exception {
        Charge c = paidStripeOrder("webhook-disputed-owed");
        doReturn(new RefundResult(false, null, "Refunds are paused")).when(stripe).refund(any(), any(), any(), any());
        refundService.refundOrder(c.orderId(), "Cannot attend", ADMIN);
        assertThat(paymentStatus(c)).isEqualTo("REFUND_REQUIRED");

        deliver(StripeWebhooks.disputeCreatedEvent("evt_disputed_owed", c.intentId(), c.amountMinor(),
                c.currency(), "product_not_received")).andExpect(status().isOk());
        jdbc.sql("UPDATE payments SET refund_last_attempt_at = now() - INTERVAL '1 day' WHERE id = :id")
                .param("id", c.paymentId()).update();
        reconciler.retryDue();

        assertThat(paymentStatus(c)).isEqualTo("DISPUTED");
        verify(stripe, times(1)).refund(any(), any(), any(), eq(refundKeyOf(c)));
    }

    @Test
    void an_intent_cancelled_before_it_was_paid_fails_its_payment_and_leaves_the_order_payable() throws Exception {
        Charge c = pendingStripeOrder("webhook-intent-cancelled");

        deliver(event(c, "evt_intent_cancelled", "payment_intent.canceled")).andExpect(status().isOk());

        assertThat(paymentStatus(c)).isEqualTo("FAILED");
        assertThat(orderStatus(c)).isEqualTo("PENDING_PAYMENT");
        assertThat(outcomeRecorded("evt_intent_cancelled")).isEqualTo("CANCELLED");
    }

    @Test
    void a_charge_that_fails_once_its_order_has_closed_sends_nobody_back_to_pay() throws Exception {
        Charge c = pendingStripeOrder("webhook-declined-late");
        jdbc.sql("UPDATE orders SET status = 'EXPIRED' WHERE id = :id").param("id", c.orderId()).update();

        deliver(event(c, "evt_declined_late", "payment_intent.payment_failed")).andExpect(status().isOk());

        assertThat(paymentStatus(c)).isEqualTo("FAILED");
        // the order is gone, so an email saying "try again" would only lead the buyer to a dead end
        assertThat(published("payment.failed", c.paymentId())).isZero();
    }
}
