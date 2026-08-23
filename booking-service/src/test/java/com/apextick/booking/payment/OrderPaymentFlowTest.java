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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class OrderPaymentFlowTest {

    private static final String SLUG = "india-australia-semi-final";

    @Autowired MockMvc mvc;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;

    private final ObjectMapper json = new ObjectMapper();

    private static final String VISA_OK = """
            {"card":{"number":"4242424242424242","expMonth":12,"expYear":2030,"cvc":"123","holder":"Pay Buyer"}}""";

    @Test
    void pay_with_a_good_card_books_seats_and_issues_tickets() throws Exception {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(2).toList();

        String sub = "pay-buyer";
        CurrentUser user = new CurrentUser(sub, "paybuyer", "pay@apextick.local", "Pay Buyer", Set.of("user"));
        holdService.hold(SLUG, seatIds, user);
        OrderResponse order = orderService.create(new CreateOrderRequest(eventId, seatIds),
                "pay-order-" + System.nanoTime(), user);

        String token = TestTokens.user(sub, "paybuyer", "pay@apextick.local");
        mvc.perform(post("/api/orders/" + order.id() + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(VISA_OK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.cardBrand").value("VISA"))
                .andExpect(jsonPath("$.cardLast4").value("4242"))
                .andExpect(jsonPath("$.order.status").value("PAID"))
                .andExpect(jsonPath("$.order.ticketIds.length()").value(2));

        mvc.perform(get("/api/orders/" + order.id() + "/tickets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].qrToken").exists());

        List<Seat> after = seatRepository.findByIdsWithLayout(seatIds);
        assertThat(after).allMatch(s -> s.getStatus() == SeatStatus.BOOKED);
    }

    @Test
    void a_declined_card_leaves_the_order_pending_with_402() throws Exception {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(1).toList();

        String sub = "decline-buyer";
        CurrentUser user = new CurrentUser(sub, "declbuyer", "decl@apextick.local", "Decl Buyer", Set.of("user"));
        holdService.hold(SLUG, seatIds, user);
        OrderResponse order = orderService.create(new CreateOrderRequest(eventId, seatIds),
                "decl-order-" + System.nanoTime(), user);

        String declined = json.writeValueAsString(Map.of("card",
                Map.of("number", "4000000000000002", "expMonth", 12, "expYear", 2030, "cvc", "123", "holder", "D")));
        mvc.perform(post("/api/orders/" + order.id() + "/pay")
                        .header("Authorization", "Bearer " + TestTokens.user(sub, "declbuyer", "decl@apextick.local"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(declined))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("card_declined"))
                .andExpect(jsonPath("$.order.status").value("PENDING_PAYMENT"));
    }
}
