package com.apextick.booking.hold;

import com.apextick.booking.catalog.Event;
import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.catalog.EventStatus;
import com.apextick.booking.catalog.PriceTier;
import com.apextick.booking.catalog.PriceTierRepository;
import com.apextick.booking.catalog.Section;
import com.apextick.booking.catalog.SectionRepository;
import com.apextick.booking.catalog.Sport;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.support.IntegrationTest;
import com.apextick.booking.support.TestTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin sales controls -- the status pill, "Sales open", "Sales close" -- and the event's
 * own kickoff time used to be decorative: holds checked only that the event was not a draft.
 * These pin that every step which moves inventory or money reads them, and that the answer is
 * the same at each step, so a hold taken while the sale was open cannot be cashed in after it
 * has closed.
 */
@IntegrationTest
class SalesWindowApiTest {

    @Autowired MockMvc mvc;
    @Autowired EventRepository eventRepository;
    @Autowired PriceTierRepository priceTierRepository;
    @Autowired SectionRepository sectionRepository;
    @Autowired SeatRepository seatRepository;

    private final ObjectMapper json = new ObjectMapper();

    private static final String VISA_OK = """
            {"card":{"number":"4242424242424242","expMonth":12,"expYear":2030,"cvc":"123","holder":"H"}}""";

    @Test
    void holds_are_refused_before_the_sale_opens() throws Exception {
        Event event = seedEvent(EventStatus.ONSALE, future(7), future(1), null);

        holdRequest(event, "early-bird")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SALES_NOT_OPEN"))
                .andExpect(jsonPath("$.salesStartAt").exists());
    }

    @Test
    void holds_are_refused_after_the_sale_closes() throws Exception {
        Event event = seedEvent(EventStatus.ONSALE, future(7), past(30), past(1));

        holdRequest(event, "late-comer")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SALES_CLOSED"));
    }

    /** No explicit sales end means sales run up to kickoff -- never past a match already played. */
    @Test
    void holds_are_refused_once_the_event_has_started() throws Exception {
        Event event = seedEvent(EventStatus.ONSALE, past(1), past(30), null);

        holdRequest(event, "time-traveller")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SALES_CLOSED"));
    }

    /** Marking an event sold out used to change only the badge. */
    @Test
    void a_sold_out_event_stops_selling() throws Exception {
        Event event = seedEvent(EventStatus.SOLD_OUT, future(7), past(30), null);

        holdRequest(event, "sold-out-chancer")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SALES_CLOSED"));
    }

    @Test
    void a_cancelled_event_stops_selling() throws Exception {
        Event event = seedEvent(EventStatus.CANCELLED, future(7), past(30), null);

        holdRequest(event, "cancelled-chancer")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SALES_CLOSED"));
    }

    /**
     * The hold is the only guard a buyer passes before checkout, and it is taken minutes
     * earlier. So the order and the payment re-ask, or a sale that closed mid-checkout would
     * still complete.
     */
    @Test
    void an_order_and_a_payment_are_both_refused_once_the_sale_has_closed() throws Exception {
        Event event = seedEvent(EventStatus.ONSALE, future(7), past(30), null);
        String sub = "checkout-straggler";
        String token = "Bearer " + TestTokens.user(sub, sub, sub + "@apextick.local");
        List<Seat> seats = seatsOf(event);
        List<Long> both = List.of(seats.get(0).getId(), seats.get(1).getId());

        mvc.perform(post("/api/events/" + event.getSlug() + "/holds").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("seatIds", both))))
                .andExpect(status().isCreated());

        String order = mvc.perform(post("/api/orders").header("Authorization", token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("eventId", event.getId(), "seatIds", List.of(both.get(0))))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = json.readTree(order).get("id").asText();

        // the gates close while the buyer is on the checkout page
        closeSales(event);

        mvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(VISA_OK))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SALES_CLOSED"));

        // and the still-live hold on the second seat cannot become an order either
        mvc.perform(post("/api/orders").header("Authorization", token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("eventId", event.getId(), "seatIds", List.of(both.get(1))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SALES_CLOSED"));
    }

    // ---- fixtures ----

    private org.springframework.test.web.servlet.ResultActions holdRequest(Event event, String sub)
            throws Exception {
        List<Long> seatIds = List.of(seatsOf(event).get(0).getId());
        return mvc.perform(post("/api/events/" + event.getSlug() + "/holds")
                .header("Authorization", "Bearer " + TestTokens.user(sub, sub, sub + "@apextick.local"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("seatIds", seatIds))));
    }

    private List<Seat> seatsOf(Event event) {
        return seatRepository.findByEventIdOrderByIdAsc(event.getId());
    }

    private void closeSales(Event event) {
        Event stored = eventRepository.findById(event.getId()).orElseThrow();
        stored.setSalesEndAt(past(1));
        eventRepository.saveAndFlush(stored);
    }

    private static Instant future(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    private static Instant past(int minutes) {
        return Instant.now().minus(minutes, ChronoUnit.MINUTES);
    }

    private Event seedEvent(EventStatus status, Instant startsAt, Instant salesStartAt, Instant salesEndAt) {
        Event event = new Event();
        event.setName("Sales Window");
        event.setVenue("Test Arena");
        event.setStartsAt(startsAt);
        event.setSlug("sales-window-" + System.nanoTime());
        event.setSport(Sport.CRICKET);
        event.setStatus(status);
        event.setSalesStartAt(salesStartAt);
        event.setSalesEndAt(salesEndAt);
        event = eventRepository.saveAndFlush(event);

        PriceTier tier = new PriceTier();
        tier.setEvent(event);
        tier.setCode("standard");
        tier.setName("Standard");
        tier.setPrice(new BigDecimal("100.00"));
        tier.setPerks(List.of());
        tier.setSortOrder(0);
        tier = priceTierRepository.saveAndFlush(tier);

        Section section = new Section();
        section.setEvent(event);
        section.setCode("main");
        section.setName("Main");
        section.setTier(tier);
        section.setSide("n");
        section.setRows(1);
        section.setSeatsPerRow(2);
        section.setSortOrder(0);
        section = sectionRepository.saveAndFlush(section);

        List<Seat> seats = new ArrayList<>();
        for (int c = 0; c < 2; c++) {
            Seat seat = new Seat();
            seat.setEventId(event.getId());
            seat.setSection(section);
            seat.setSeatNumber("A" + (c + 1));
            seat.setRowIdx(0);
            seat.setColIdx(c);
            seats.add(seat);
        }
        seatRepository.saveAllAndFlush(seats);
        return event;
    }
}
