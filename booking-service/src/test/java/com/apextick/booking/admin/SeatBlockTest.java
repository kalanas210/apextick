package com.apextick.booking.admin;

import com.apextick.booking.hold.HoldService;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.seat.SeatUnavailableException;
import com.apextick.booking.support.IntegrationTest;
import com.apextick.booking.support.TestTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Taking a seat off sale. Layouts are create-only, so a seat found broken, or kept for the press or a
 * wheelchair space, used to go on selling; and releasing a seat that was not held answered 200 with
 * nothing changed.
 */
@IntegrationTest
class SeatBlockTest {

    @Autowired MockMvc mvc;
    @Autowired SeatRepository seatRepository;
    @Autowired HoldService holdService;

    private final ObjectMapper json = new ObjectMapper();

    private record Fixture(String slug, List<Long> seatIds) {
    }

    private static String admin() {
        return "Bearer " + TestTokens.admin("block-admin", "blockadmin", "block-admin@apextick.local");
    }

    private static CurrentUser customer(String sub) {
        return new CurrentUser(sub, sub, sub + "@apextick.local", "Seat Buyer", Set.of("user"));
    }

    private Fixture eventOnSale(String name) throws Exception {
        String slug = "block-" + name + "-" + System.nanoTime();
        Map<String, Object> event = Map.of(
                "name", "Blocking " + name, "slug", slug, "sport", "football", "status", "onsale",
                "startsAt", Instant.now().plus(Duration.ofDays(30)).toString(), "venue", "Test Arena",
                "currency", "USD");
        String created = mvc.perform(post("/api/admin/events").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(event)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long eventId = json.readTree(created).get("id").asLong();
        Map<String, Object> layout = Map.of(
                "tiers", List.of(Map.of("code", "std", "name", "Standard", "price", 100)),
                "sections", List.of(Map.of("code", "main", "name", "Main", "tierCode", "std",
                        "side", "n", "rows", 1, "seatsPerRow", 4)));
        mvc.perform(post("/api/admin/events/" + eventId + "/layout").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(layout)))
                .andExpect(status().isCreated());
        return new Fixture(slug, seatRepository.findByEventIdOrderByIdAsc(eventId).stream().map(Seat::getId).toList());
    }

    private ResultActions seatAction(Long seatId, String action) throws Exception {
        return mvc.perform(post("/api/admin/seats/" + seatId + "/" + action).header("Authorization", admin()));
    }

    private SeatStatus statusOf(Long seatId) {
        return seatRepository.findById(seatId).orElseThrow().getStatus();
    }

    @Test
    void a_blocked_seat_cannot_be_held_until_it_is_put_back_on_sale() throws Exception {
        Fixture f = eventOnSale("hold");
        Long seat = f.seatIds().getFirst();

        seatAction(seat, "block").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOCKED"));
        assertThatThrownBy(() -> holdService.hold(f.slug(), List.of(seat), customer("block-buyer")))
                .isInstanceOf(SeatUnavailableException.class);
        assertThat(statusOf(seat)).isEqualTo(SeatStatus.BLOCKED);

        seatAction(seat, "unblock").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AVAILABLE"));
        holdService.hold(f.slug(), List.of(seat), customer("block-buyer"));
        assertThat(statusOf(seat)).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void only_an_available_seat_is_blocked_and_only_a_held_one_is_released() throws Exception {
        Fixture f = eventOnSale("rules");
        Long held = f.seatIds().get(0);
        Long available = f.seatIds().get(1);
        holdService.hold(f.slug(), List.of(held), customer("block-holder"));

        seatAction(held, "block")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"))
                .andExpect(jsonPath("$.seatStatus").value("HELD"));
        seatAction(available, "release")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_HELD"))
                .andExpect(jsonPath("$.seatStatus").value("AVAILABLE"));
        seatAction(available, "unblock")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_BLOCKED"));

        assertThat(statusOf(held)).isEqualTo(SeatStatus.HELD);
        assertThat(statusOf(available)).isEqualTo(SeatStatus.AVAILABLE);
    }
}
