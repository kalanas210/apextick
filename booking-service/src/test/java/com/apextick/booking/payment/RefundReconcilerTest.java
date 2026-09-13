package com.apextick.booking.payment;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.payment.dto.PayRequest;
import com.apextick.booking.payment.model.PaymentCard;
import com.apextick.booking.payment.model.RefundResult;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The refunds a provider refused, asked for again. REFUND_REQUIRED used to be written and never
 * read: a charge the provider would not give back stayed owed for as long as nobody looked.
 */
class RefundReconcilerTest extends PaymentGatewaySpies {

    // PaymentConcurrencyTest buys here too, under buyers of its own
    private static final String SLUG = "manchester-city-tottenham";
    private static final CurrentUser ADMIN = new CurrentUser("reconciler-admin", "reconciler-admin",
            "reconciler-admin@apextick.local", "Reconciler Admin", Set.of("admin"));
    private static final RefundResult REFUSED = new RefundResult(false, null, "Refunds are paused on this account");

    @Autowired RefundReconciler reconciler;
    @Autowired RefundService refundService;
    @Autowired PaymentService paymentService;
    @Autowired PaymentRepository paymentRepository;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired MeterRegistry meters;
    @Autowired JdbcClient jdbc;

    private record Paid(UUID orderId, UUID paymentId) {
        String refundKey() {
            return "refund:" + paymentId;
        }
    }

    /** A one-seat order paid through the mock gateway. */
    private Paid paidOrder(String buyer) {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(1).toList();
        CurrentUser user = new CurrentUser(buyer, buyer, buyer + "@apextick.local", "Reconciled Buyer", Set.of("user"));
        holdService.hold(SLUG, seatIds, user);
        UUID orderId = UUID.fromString(orderService.create(new CreateOrderRequest(eventId, seatIds),
                buyer + "-order-" + UUID.randomUUID(), user).id());
        paymentService.pay(orderId,
                new PayRequest(new PaymentCard("4242424242424242", 12, 2030, "123", "R"), null, null, null),
                buyer + "-pay-" + UUID.randomUUID(), user);
        UUID paymentId = paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCEEDED).getFirst().getId();
        return new Paid(orderId, paymentId);
    }

    private Payment payment(Paid paid) {
        return paymentRepository.findById(paid.paymentId()).orElseThrow();
    }

    /** As if the provider was last asked a day ago, and had been asked this many times. */
    private void lastAskedADayAgo(Paid paid, int attempts) {
        jdbc.sql("UPDATE payments SET refund_last_attempt_at = now() - INTERVAL '1 day', refund_attempts = :attempts "
                        + "WHERE id = :id")
                .param("attempts", attempts).param("id", paid.paymentId()).update();
    }

    private double refundsOwed() {
        return meters.get("apextick.refunds.owed").gauge().value();
    }

    @Test
    void a_refused_refund_is_asked_for_again_once_it_is_due_until_the_provider_takes_it() {
        Paid paid = paidOrder("reconcile-later");
        doReturn(REFUSED).when(mockGateway).refund(any(), any(), any(), any());
        double owedBefore = refundsOwed();

        refundService.refundOrder(paid.orderId(), "Cannot attend", ADMIN);
        assertThat(payment(paid).getStatus()).isEqualTo(PaymentStatus.REFUND_REQUIRED);
        assertThat(refundsOwed()).isEqualTo(owedBefore + 1);

        // asked a moment ago, so not due yet
        reconciler.retryDue();
        verify(mockGateway, times(1)).refund(any(), any(), any(), eq(paid.refundKey()));

        lastAskedADayAgo(paid, 1);
        doReturn(new RefundResult(true, "mock_refund_reconciled", null))
                .when(mockGateway).refund(any(), any(), any(), any());
        reconciler.retryDue();

        Payment refunded = payment(paid);
        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refunded.getRefundRef()).isEqualTo("mock_refund_reconciled");
        assertThat(refunded.getRefundAttempts()).isEqualTo(2);
        // the same refund both times, so the retry could never have paid it out twice
        verify(mockGateway, times(2)).refund(any(), any(), any(), eq(paid.refundKey()));
    }

    @Test
    void a_refund_refused_time_after_time_is_left_for_a_person_and_still_counted_as_owed() {
        Paid paid = paidOrder("reconcile-never");
        doReturn(REFUSED).when(mockGateway).refund(any(), any(), any(), any());
        refundService.refundOrder(paid.orderId(), "Cannot attend", ADMIN);
        lastAskedADayAgo(paid, 12);

        reconciler.retryDue();

        verify(mockGateway, times(1)).refund(any(), any(), any(), eq(paid.refundKey()));
        assertThat(payment(paid).getStatus()).isEqualTo(PaymentStatus.REFUND_REQUIRED);
        assertThat(refundsOwed()).isGreaterThanOrEqualTo(1);
    }
}
