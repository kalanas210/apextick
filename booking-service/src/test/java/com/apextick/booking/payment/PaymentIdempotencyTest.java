package com.apextick.booking.payment;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class PaymentIdempotencyTest {

    private static final String SLUG = "qualifier-one";
    private static final String VISA_OK = """
            {"card":{"number":"4242424242424242","expMonth":12,"expYear":2030,"cvc":"123","holder":"H"}}""";

    @Autowired MockMvc mvc;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;

    @Test
    void paying_a_second_time_does_not_charge_again() throws Exception {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(2).toList();

        String sub = "idem-pay";
        CurrentUser user = new CurrentUser(sub, "idempay", "idempay@apextick.local", "Idem Pay", Set.of("user"));
        holdService.hold(SLUG, seatIds, user);
        OrderResponse order = orderService.create(new CreateOrderRequest(eventId, seatIds),
                "idem-pay-order-" + System.nanoTime(), user);
        String token = "Bearer " + TestTokens.user(sub, "idempay", "idempay@apextick.local");

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/orders/" + order.id() + "/pay")
                            .header("Authorization", token)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON).content(VISA_OK))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                    .andExpect(jsonPath("$.order.status").value("PAID"));
        }

        // exactly one payment attempt exists for the order
        mvc.perform(get("/api/orders/" + order.id() + "/payments").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"));
    }
}
