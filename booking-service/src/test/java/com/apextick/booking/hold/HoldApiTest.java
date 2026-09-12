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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    // the per-user cap test needs nine free seats of its own
    private static final String CAP_SLUG = "gujarat-titans-rajasthan-royals";

    private List<Long> twoAvailableSeatIds() {
        return availableSeatIds(SLUG, 2);
    }

    private List<Long> availableSeatIds(String slug, int count) {
        Long eventId = eventRepository.findBySlug(slug).orElseThrow().getId();
        return seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .map(Seat::getId).limit(count).toList();
    }

    private String body(List<Long> seatIds) throws Exception {
        return json.writeValueAsString(java.util.Map.of("seatIds", seatIds));
    }

    @Test
    void hold_then_release_round_trip() throws Exception {
        List<Long> ids = twoAvailableSeatIds();
        String token = TestTokens.user("holder-1", "holder1", "h1@apextick.local");

        mvc.perform(post("/api/events/" + SLUG + "/holds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(ids)))
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

        mvc.perform(post("/api/events/" + SLUG + "/holds")
                        .header("Authorization", "Bearer " + TestTokens.user("owner", "owner", "o@apextick.local"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(ids)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/events/" + SLUG + "/holds")
                        .header("Authorization", "Bearer " + TestTokens.user("rival", "rival", "r@apextick.local"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(ids)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"))
                .andExpect(jsonPath("$.seatIds").isArray());
    }

    /**
     * The seat map pre-selects the caller's own live hold, so Back from checkout, a reload or
     * a retry re-posts exactly the seats they already hold. That used to be reported as
     * "someone just took one of those seats" against themselves, with no way forward but
     * waiting out the TTL. Re-holding is now idempotent and extends the hold.
     */
    @Test
    void re_holding_your_own_seats_refreshes_the_hold_instead_of_conflicting() throws Exception {
        List<Long> ids = twoAvailableSeatIds();
        String token = TestTokens.user("returning", "returning", "ret@apextick.local");

        mvc.perform(post("/api/events/" + SLUG + "/holds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(ids)))
                .andExpect(status().isCreated());
        Instant firstHeldUntil = seatRepository.findById(ids.get(0)).orElseThrow().getHeldUntil();

        mvc.perform(post("/api/events/" + SLUG + "/holds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(ids)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatIds.length()").value(2))
                .andExpect(jsonPath("$.seats[0].mine").value(true));

        mvc.perform(get("/api/events/" + SLUG + "/holds/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatIds.length()").value(2));

        // the hold was refreshed, not merely tolerated
        Seat after = seatRepository.findById(ids.get(0)).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(after.getHeldBy()).isEqualTo("returning");
        assertThat(after.getHeldUntil()).isAfterOrEqualTo(firstHeldUntil);
    }

    /** Adding one more seat to a live hold goes through the same call, and must not fail. */
    @Test
    void a_mix_of_own_and_available_seats_is_held() throws Exception {
        List<Long> ids = availableSeatIds(SLUG, 2);
        String token = TestTokens.user("adder", "adder", "add@apextick.local");

        mvc.perform(post("/api/events/" + SLUG + "/holds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(List.of(ids.get(0)))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/events/" + SLUG + "/holds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(ids)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatIds.length()").value(2));
    }

    /**
     * The cap is per person, not per request: eight seats a call, thirty calls a minute is
     * how one account walks off with a whole stand during a flash sale.
     */
    @Test
    void the_seat_cap_counts_seats_the_caller_already_holds() throws Exception {
        List<Long> ids = availableSeatIds(CAP_SLUG, 9);
        String token = TestTokens.user("hoarder", "hoarder", "hoard@apextick.local");

        mvc.perform(post("/api/events/" + CAP_SLUG + "/holds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(ids.subList(0, 8))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatIds.length()").value(8));

        mvc.perform(post("/api/events/" + CAP_SLUG + "/holds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(List.of(ids.get(8)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TOO_MANY_SEATS"))
                .andExpect(jsonPath("$.maxSeats").value(8))
                .andExpect(jsonPath("$.heldSeats").value(8));
    }

    @Test
    void empty_selection_is_rejected() throws Exception {
        mvc.perform(post("/api/events/" + SLUG + "/holds")
                        .header("Authorization", "Bearer " + TestTokens.user("u", "u", "u@apextick.local"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"seatIds\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
