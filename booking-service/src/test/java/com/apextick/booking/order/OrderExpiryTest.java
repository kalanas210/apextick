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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class OrderExpiryTest {

    private static final String SLUG = "world-cup-final-2026";

    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderExpirySweeper sweeper;
    @Autowired HoldService holdService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;

    @Test
    void sweeper_expires_an_unpaid_order_and_releases_its_seats() {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .map(Seat::getId).limit(2).toList();

        String sub = "expiry-buyer";
        CurrentUser user = new CurrentUser(sub, "expirybuyer", "exp@apextick.local", "Exp Buyer", Set.of("user"));
        holdService.hold(SLUG, seatIds, user);

        OrderResponse created = orderService.create(new CreateOrderRequest(eventId, seatIds),
                "expiry-key-" + System.nanoTime(), user);
        UUID orderId = UUID.fromString(created.id());

        // force the payment window to have elapsed
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        orderRepository.saveAndFlush(order);

        int expired = sweeper.sweep();
        assertThat(expired).isGreaterThanOrEqualTo(1);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.EXPIRED);
        List<Seat> after = seatRepository.findByIdsWithLayout(seatIds);
        assertThat(after).allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE);
    }
}
