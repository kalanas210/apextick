package com.apextick.booking.order;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class OrderFlowTest {

    private static final String SLUG = "england-croatia-group-stage";

    @Autowired MockMvc mvc;
    @Autowired HoldService holdService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void create_pay_window_and_cancel_release_seats() throws Exception {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .map(Seat::getId).limit(2).toList();

        String sub = "flow-buyer";
        CurrentUser user = new CurrentUser(sub, "flowbuyer", "flow@apextick.local", "Flow Buyer", Set.of("user"));
        holdService.hold(SLUG, seatIds, user);

        String token = TestTokens.user(sub, "flowbuyer", "flow@apextick.local");
        String body = json.writeValueAsString(Map.of("eventId", eventId, "seatIds", seatIds));

        String response = mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.subtotal").exists())
                .andExpect(jsonPath("$.fee").exists())
                .andExpect(jsonPath("$.total").exists())
                .andReturn().getResponse().getContentAsString();

        String orderId = json.readTree(response).get("id").asText();

        mvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // seats released back to AVAILABLE
        List<Seat> after = seatRepository.findByIdsWithLayout(seatIds);
        assertThat(after).allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE);
    }

    @Test
    void order_requires_an_idempotency_key() throws Exception {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        String body = json.writeValueAsString(Map.of("eventId", eventId, "seatIds", List.of(999_999_999L)));
        mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + TestTokens.user("x", "x", "x@apextick.local"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_MISSING"));
    }
}
