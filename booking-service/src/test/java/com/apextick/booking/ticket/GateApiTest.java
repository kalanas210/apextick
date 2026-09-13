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
import com.apextick.booking.ticket.gate.ScanOutcome;
import com.apextick.booking.ticket.gate.TicketScan;
import com.apextick.booking.ticket.gate.TicketScanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
    private static final String STEWARD_SUB = "gate-steward";

    @Autowired MockMvc mvc;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired TicketScanRepository scanRepository;
    @Autowired JdbcClient jdbc;

    private final ObjectMapper json = new ObjectMapper();

    private static String steward() {
        return "Bearer " + TestTokens.withRoles(STEWARD_SUB, "steward", "steward@apextick.local", "user", "scanner");
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
        return scan(bearer, qrToken, eventId, null);
    }

    private ResultActions scan(String bearer, String qrToken, Long eventId, String gate) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("qrToken", qrToken, "eventId", eventId));
        if (gate != null) {
            body.put("gate", gate);
        }
        MockHttpServletRequestBuilder request = post("/api/gate/scans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
        return mvc.perform(bearer == null ? request : request.header("Authorization", bearer));
    }

    private long admitted(Long eventId) throws Exception {
        String body = mvc.perform(get("/api/gate/events/" + eventId + "/admissions").header("Authorization", steward()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("admitted").asLong();
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
     * The head count used to live in one browser tab until it reloaded, and a refused scan left no
     * trace at all. Every scan is now on the record with its gate and its steward, an admission
     * is announced as ticket.used, and the count is the same on every turnstile.
     */
    @Test
    void every_scan_is_recorded_and_admissions_are_counted_across_gates() throws Exception {
        Ticket ticket = buyTicket(SLUG, "gate-record");
        long before = admitted(ticket.getEventId());

        scan(steward(), ticket.getQrToken(), ticket.getEventId(), "North 3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admissions.admitted").value((int) before + 1));
        scan(steward(), ticket.getQrToken(), ticket.getEventId(), "South 1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.usedGate").value("North 3"))
                .andExpect(jsonPath("$.ticketId").value(ticket.getId().toString()));

        assertThat(admitted(ticket.getEventId())).isEqualTo(before + 1);
        assertThat(scanRepository.findByTicketIdOrderByScannedAtAscIdAsc(ticket.getId()))
                .extracting(TicketScan::getOutcome, TicketScan::getGate, TicketScan::getActorSub)
                .containsExactly(
                        tuple(ScanOutcome.ADMITTED, "North 3", STEWARD_SUB),
                        tuple(ScanOutcome.ALREADY_USED, "South 1", STEWARD_SUB));
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_events WHERE type = 'ticket.used' AND aggregate_id = :id")
                .param("id", ticket.getId().toString()).query(Long.class).single())
                .isEqualTo(1);
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
        assertThat(scanRepository.findByTicketIdOrderByScannedAtAscIdAsc(ticket.getId()))
                .extracting(TicketScan::getOutcome, TicketScan::getEventId)
                .containsExactly(tuple(ScanOutcome.WRONG_EVENT, eventId(SLUG)));

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
        String customer = "Bearer " + TestTokens.user("gate-customer", "customer", "customer@apextick.local");

        scan(null, ticket.getQrToken(), ticket.getEventId()).andExpect(status().isUnauthorized());
        scan(customer, ticket.getQrToken(), ticket.getEventId()).andExpect(status().isForbidden());
        mvc.perform(get("/api/gate/events/" + ticket.getEventId() + "/admissions").header("Authorization", customer))
                .andExpect(status().isForbidden());
        assertThat(statusOf(ticket)).isEqualTo(TicketStatus.ISSUED);

        scan("Bearer " + TestTokens.admin("gate-admin", "gateadmin", "gateadmin@apextick.local"),
                ticket.getQrToken(), ticket.getEventId())
                .andExpect(status().isOk());
    }

    /** The whole point of the role: a turnstile device that goes missing opens no admin API. */
    @Test
    void a_steward_cannot_reach_the_admin_api() throws Exception {
        mvc.perform(get("/api/admin/orders").header("Authorization", steward()))
                .andExpect(status().isForbidden());
    }
}
