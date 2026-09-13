package com.apextick.booking.payment;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.OrderStatus;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.payment.dto.PayRequest;
import com.apextick.booking.payment.model.PaymentCard;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Attempts racing on one order. A second tab, a double click, a client retrying on its own: each
 * arrives with a key of its own, so matching keys can never catch them. Only making the attempts
 * take turns on the order does.
 */
@IntegrationTest
class PaymentConcurrencyTest {

    private static final String SLUG = "manchester-city-tottenham";
    private static final PayRequest VISA_OK = new PayRequest(
            new PaymentCard("4242424242424242", 12, 2030, "123", "Race Buyer"), null, null, null);

    @Autowired PaymentService paymentService;
    @Autowired PaymentRepository paymentRepository;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired HoldService holdService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;

    @Test
    void attempts_racing_on_one_order_charge_it_once() throws InterruptedException {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(2).toList();

        CurrentUser user = new CurrentUser("race-buyer", "racebuyer", "race@apextick.local", "Race Buyer",
                Set.of("user"));
        holdService.hold(SLUG, seatIds, user);
        UUID orderId = UUID.fromString(orderService.create(new CreateOrderRequest(eventId, seatIds),
                "race-order-" + System.nanoTime(), user).id());

        int contenders = 12;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(contenders);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < contenders; i++) {
                String idempotencyKey = "race-attempt-" + i;
                pool.submit(() -> {
                    try {
                        start.await();
                        paymentService.pay(orderId, VISA_OK, idempotencyKey, user);
                    } catch (Exception ignored) {
                        // a loser is told another payment is in progress; what got charged is the assertion
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        List<Payment> charged = paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        assertThat(charged).hasSize(1);
        assertThat(charged.getFirst().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }
}
