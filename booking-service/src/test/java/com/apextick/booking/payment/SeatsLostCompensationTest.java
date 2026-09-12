package com.apextick.booking.payment;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class SeatsLostCompensationTest {

    private static final String SLUG = "chennai-mumbai-return";
    private static final String VISA_OK = """
            {"card":{"number":"4242424242424242","expMonth":12,"expYear":2030,"cvc":"123","holder":"H"}}""";

    @Autowired MockMvc mvc;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;

    @Test
    void paying_after_the_hold_was_lost_cancels_the_order_and_flags_a_refund() throws Exception {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(2).toList();

        String sub = "lost-buyer";
        CurrentUser user = new CurrentUser(sub, "lostbuyer", "lost@apextick.local", "Lost Buyer", Set.of("user"));
        holdService.hold(SLUG, seatIds, user);
        OrderResponse order = orderService.create(new CreateOrderRequest(eventId, seatIds),
                "lost-order-" + System.nanoTime(), user);

        // Lose the hold between order creation and payment the way it really happens: the
        // Redis TTL lapses and the expiry listener frees the seats. (Not releaseMine -- that
        // now refuses seats an unpaid order still covers, which is the point of ORDER_PENDING.)
        seatIds.forEach(holdService::releaseExpired);

        mvc.perform(post("/api/orders/" + order.id() + "/pay")
                        .header("Authorization", "Bearer " + TestTokens.user(sub, "lostbuyer", "lost@apextick.local"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(VISA_OK))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("REFUND_REQUIRED"))
                .andExpect(jsonPath("$.failureCode").value("seats_lost"))
                .andExpect(jsonPath("$.order.status").value("CANCELLED"));

        assertThat(orderRepository.findById(UUID.fromString(order.id())).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }
}
