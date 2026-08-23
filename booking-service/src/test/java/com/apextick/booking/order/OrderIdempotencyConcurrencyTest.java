package com.apextick.booking.order;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.order.dto.OrderResponse;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class OrderIdempotencyConcurrencyTest {

    private static final String SLUG = "usa-paraguay-group-stage";

    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired HoldService holdService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;

    @Test
    void same_idempotency_key_creates_exactly_one_order_under_concurrency() throws InterruptedException {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .map(Seat::getId).limit(2).toList();

        String sub = "idem-buyer";
        CurrentUser user = new CurrentUser(sub, "idembuyer", "idem@apextick.local", "Idem Buyer", Set.of("user"));
        holdService.hold(SLUG, seatIds, user);

        String key = "fixed-key-" + System.nanoTime();
        CreateOrderRequest request = new CreateOrderRequest(eventId, seatIds);

        int contenders = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(contenders);
        Set<String> orderIds = ConcurrentHashMap.newKeySet();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < contenders; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        OrderResponse r = orderService.create(request, key, user);
                        orderIds.add(r.id());
                    } catch (Exception ignored) {
                        // a loser may throw; the idempotent winner is what we assert on
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(orderIds).hasSize(1);
        assertThat(orderRepository.findByUserSubAndIdempotencyKey(sub, key)).isPresent();
    }
}
