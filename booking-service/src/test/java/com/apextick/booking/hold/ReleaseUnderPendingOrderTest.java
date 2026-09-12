package com.apextick.booking.hold;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.OrderStatus;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.order.dto.OrderResponse;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.IntegrationTest;
import com.apextick.booking.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Release my seats" is offered on the seat map whenever a hold is running, including after
 * Back from checkout. Letting it free seats an unpaid order still covers put the two
 * aggregates out of step: the map showed the seats available while existsPendingForSeats kept
 * every buyer out, and paying the order charged the card only to refund it.
 */
@IntegrationTest
class ReleaseUnderPendingOrderTest {

    private static final String SLUG = "aston-villa-arsenal";

    @Autowired MockMvc mvc;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired JdbcClient jdbc;

    @Test
    void releasing_seats_an_unpaid_order_covers_is_refused_until_the_order_is_cancelled() throws Exception {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(2).toList();

        String sub = "release-guard-buyer";
        CurrentUser buyer = new CurrentUser(sub, "rgbuyer", "rg@apextick.local", "Release Guard", Set.of("user"));
        String token = "Bearer " + TestTokens.user(sub, "rgbuyer", "rg@apextick.local");

        holdService.hold(SLUG, seatIds, buyer);
        OrderResponse order = orderService.create(new CreateOrderRequest(eventId, seatIds),
                "release-guard-" + System.nanoTime(), buyer);

        mvc.perform(delete("/api/events/" + SLUG + "/holds").header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_PENDING"))
                .andExpect(jsonPath("$.seatIds").isArray());

        assertThat(seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> seatIds.contains(s.getId())))
                .allMatch(s -> s.getStatus() == SeatStatus.HELD);

        // cancelling the order is the release path, and it frees the seats for everyone
        orderService.cancel(UUID.fromString(order.id()), buyer);
        assertThat(seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> seatIds.contains(s.getId())))
                .allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE);

        mvc.perform(delete("/api/events/" + SLUG + "/holds").header("Authorization", token))
                .andExpect(status().isNoContent());
    }

    /**
     * The guard has to ask about the caller's own orders, not everyone's. A seat can be held by
     * one buyer while a stranger's unpaid order still names it, and not rarely: an order's
     * deadline is capped at the hold's, so on every abandoned checkout the expiry listener frees
     * the seat within milliseconds while the order sweeper waits up to its next tick to cancel
     * the order. An unscoped guard turned the next buyer's "release my seats" into a 409 telling
     * them to cancel an order they do not own and cannot see -- and left every other seat they
     * held stuck too, because the guard aborts the whole call.
     */
    @Test
    void a_stranger_s_abandoned_order_does_not_block_the_next_buyer_s_release() throws Exception {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .map(Seat::getId).skip(4).limit(2).toList();

        CurrentUser abandoner = new CurrentUser("checkout-abandoner", "abandoner",
                "abandon@apextick.local", "Checkout Abandoner", Set.of("user"));
        holdService.hold(SLUG, seatIds, abandoner);
        OrderResponse abandoned = orderService.create(new CreateOrderRequest(eventId, seatIds),
                "abandoned-" + System.nanoTime(), abandoner);

        // their hold lapses and the expiry listener frees the seats; the order sweeper has not
        // ticked yet, so the abandoned order still names them
        seatIds.forEach(id -> jdbc.sql("UPDATE seats SET held_until = now() - INTERVAL '1 minute' WHERE id = :id")
                .param("id", id).update());
        seatIds.forEach(holdService::releaseExpired);

        // the next buyer picks the same seats up, then changes their mind
        String nextSub = "next-buyer";
        CurrentUser nextBuyer = new CurrentUser(nextSub, "nextbuyer", "next@apextick.local",
                "Next Buyer", Set.of("user"));
        String nextToken = "Bearer " + TestTokens.user(nextSub, "nextbuyer", "next@apextick.local");
        holdService.hold(SLUG, seatIds, nextBuyer);

        mvc.perform(delete("/api/events/" + SLUG + "/holds").header("Authorization", nextToken))
                .andExpect(status().isNoContent());

        assertThat(seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> seatIds.contains(s.getId())))
                .allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE);
        assertThat(orderRepository.findById(UUID.fromString(abandoned.id())).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }
}
