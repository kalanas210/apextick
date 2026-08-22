package com.apextick.booking.hold;

import com.apextick.booking.catalog.EventRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class HoldApiTest {

    @Autowired MockMvc mvc;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;

    private final ObjectMapper json = new ObjectMapper();

    // use a dedicated seeded event so parallel tests don't fight over the same seats
    private static final String SLUG = "brazil-morocco-group-stage";

    private List<Long> twoAvailableSeatIds() {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        return seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .map(Seat::getId).limit(2).toList();
    }

    @Test
    void hold_then_release_round_trip() throws Exception {
        List<Long> ids = twoAvailableSeatIds();
        String body = json.writeValueAsString(java.util.Map.of("seatIds", ids));
        String token = TestTokens.user("holder-1", "holder1", "h1@apextick.local");

        mvc.perform(post("/api/events/" + SLUG + "/holds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatIds.length()").value(2))
                .andExpect(jsonPath("$.heldUntil").exists())
                .andExpect(jsonPath("$.seats[0].mine").value(true));

        mvc.perform(get("/api/events/" + SLUG + "/holds/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatIds.length()").value(2));

        mvc.perform(delete("/api/events/" + SLUG + "/holds")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/events/" + SLUG + "/holds/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatIds.length()").value(0));
    }

    @Test
    void holding_an_already_held_seat_conflicts_with_seat_ids() throws Exception {
        List<Long> ids = twoAvailableSeatIds();
        String body = json.writeValueAsString(java.util.Map.of("seatIds", ids));

        mvc.perform(post("/api/events/" + SLUG + "/holds")
                        .header("Authorization", "Bearer " + TestTokens.user("owner", "owner", "o@apextick.local"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/events/" + SLUG + "/holds")
                        .header("Authorization", "Bearer " + TestTokens.user("rival", "rival", "r@apextick.local"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"))
                .andExpect(jsonPath("$.seatIds").isArray());
    }

    @Test
    void empty_selection_is_rejected() throws Exception {
        mvc.perform(post("/api/events/" + SLUG + "/holds")
                        .header("Authorization", "Bearer " + TestTokens.user("u", "u", "u@apextick.local"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"seatIds\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
