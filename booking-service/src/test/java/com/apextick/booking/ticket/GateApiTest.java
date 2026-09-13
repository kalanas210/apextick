package com.apextick.booking.ticket;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.payment.PaymentService;
import com.apextick.booking.payment.dto.PayRequest;
import com.apextick.booking.payment.model.PaymentCard;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The gate over HTTP. A steward scans a ticket against the event their gate is admitting, holding a
 * {@code scanner} role that opens the gate API and nothing else.
 */
@IntegrationTest
class GateApiTest {

    // fixtures no other test class buys seats at
    private static final String SLUG = "world-cup-final";
    private static final String OTHER_SLUG = "mumbai-indians-chennai-super-kings";
    private static final String CANCELLED_SLUG = "australia-england-super-8";

    @Autowired MockMvc mvc;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired JdbcClient jdbc;

    private final ObjectMapper json = new ObjectMapper();

    private static String steward() {
        return "Bearer " + TestTokens.withRoles("gate-steward", "steward", "steward@apextick.local", "user", "scanner");
    }

    private Long eventId(String slug) {
        return eventRepository.findBySlug(slug).orElseThrow().getId();
    }

    /** A paid ticket for one seat at {@code slug}. */
    private Ticket buyTicket(String slug, String buyer) {
        Long eventId = eventId(slug);
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(1).toList();
        CurrentUser user = new CurrentUser(buyer, buyer, buyer + "@apextick.local", "Gate Buyer", Set.of("user"));
        holdService.hold(slug, seatIds, user);
        UUID orderId = UUID.fromString(orderService.create(new CreateOrderRequest(eventId, seatIds),
                buyer + "-order-" + System.nanoTime(), user).id());
        paymentService.pay(orderId,
                new PayRequest(new PaymentCard("4242424242424242", 12, 2030, "123", "G"), null, null, null),
                buyer + "-pay-" + System.nanoTime(), user);
        return ticketRepository.findByOrderId(orderId).getFirst();
    }

    private ResultActions scan(String bearer, String qrToken, Long eventId) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/gate/scans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("qrToken", qrToken, "eventId", eventId)));
        return mvc.perform(bearer == null ? request : request.header("Authorization", bearer));
    }

    private TicketStatus statusOf(Ticket ticket) {
        return ticketRepository.findById(ticket.getId()).orElseThrow().getStatus();
    }

    @Test
    void a_steward_admits_a_ticket_once_and_hears_when_it_was_first_scanned() throws Exception {
        Ticket ticket = buyTicket(SLUG, "gate-once");

        scan(steward(), ticket.getQrToken(), ticket.getEventId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.status").value("USED"))
                .andExpect(jsonPath("$.ticket.eventId").value(ticket.getEventId().intValue()))
                .andExpect(jsonPath("$.event.slug").value(SLUG));

        scan(steward(), ticket.getQrToken(), ticket.getEventId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_ALREADY_USED"))
                .andExpect(jsonPath("$.usedAt").exists());
    }

    /**
     * A ticket used to admit its holder at any event, burning it for the match it was actually
     * bought for. Now the gate refuses it, says where the holder should be, and leaves the ticket
     * unspent for that match.
     */
    @Test
    void a_ticket_for_another_event_is_turned_away_unspent() throws Exception {
        Ticket ticket = buyTicket(OTHER_SLUG, "gate-wrong-event");

        scan(steward(), ticket.getQrToken(), eventId(SLUG))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_WRONG_EVENT"))
                .andExpect(jsonPath("$.ticketEventName").value("Mumbai Indians v Chennai Super Kings"))
                .andExpect(jsonPath("$.ticketEventStartsAt").exists());
        assertThat(statusOf(ticket)).isEqualTo(TicketStatus.ISSUED);

        scan(steward(), ticket.getQrToken(), ticket.getEventId()).andExpect(status().isOk());
    }

    @Test
    void nobody_is_admitted_to_a_cancelled_event() throws Exception {
        Ticket ticket = buyTicket(CANCELLED_SLUG, "gate-cancelled");
        String before = jdbc.sql("SELECT status FROM events WHERE id = :id")
                .param("id", ticket.getEventId()).query(String.class).single();
        jdbc.sql("UPDATE events SET status = 'CANCELLED' WHERE id = :id").param("id", ticket.getEventId()).update();
        try {
            scan(steward(), ticket.getQrToken(), ticket.getEventId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EVENT_NOT_ADMITTING"))
                    .andExpect(jsonPath("$.eventStatus").value("cancelled"));
            assertThat(statusOf(ticket)).isEqualTo(TicketStatus.ISSUED);
        } finally {
            jdbc.sql("UPDATE events SET status = :status WHERE id = :id")
                    .param("status", before).param("id", ticket.getEventId()).update();
        }
    }

    @Test
    void only_a_steward_or_an_admin_can_scan() throws Exception {
        Ticket ticket = buyTicket(SLUG, "gate-roles");

        scan(null, ticket.getQrToken(), ticket.getEventId()).andExpect(status().isUnauthorized());
        scan("Bearer " + TestTokens.user("gate-customer", "customer", "customer@apextick.local"),
                ticket.getQrToken(), ticket.getEventId())
                .andExpect(status().isForbidden());
        assertThat(statusOf(ticket)).isEqualTo(TicketStatus.ISSUED);

        scan("Bearer " + TestTokens.admin("gate-admin", "gateadmin", "gateadmin@apextick.local"),
                ticket.getQrToken(), ticket.getEventId())
                .andExpect(status().isOk());
    }

    /** The whole point of the role: a turnstile device that gets lost opens no admin API. */
    @Test
    void a_steward_cannot_reach_the_admin_api() throws Exception {
        mvc.perform(get("/api/admin/orders").header("Authorization", steward()))
                .andExpect(status().isForbidden());
    }
}
