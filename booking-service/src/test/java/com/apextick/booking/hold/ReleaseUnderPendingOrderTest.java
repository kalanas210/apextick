package com.apextick.booking.hold;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.order.OrderService;
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
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;

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
}
