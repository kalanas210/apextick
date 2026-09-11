package com.apextick.booking.admin;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.order.dto.OrderResponse;
import com.apextick.booking.payment.PaymentService;
import com.apextick.booking.payment.dto.PayRequest;
import com.apextick.booking.payment.model.PaymentCard;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.IntegrationTest;
import com.apextick.booking.support.TestTokens;
import com.apextick.booking.ticket.Ticket;
import com.apextick.booking.ticket.TicketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AdminApiTest {

    @Autowired MockMvc mvc;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired TicketRepository ticketRepository;

    private final ObjectMapper json = new ObjectMapper();

    private String adminToken() {
        return "Bearer " + TestTokens.admin("admin-1", "admin1", "admin@apextick.local");
    }

    @Test
    void admin_creates_an_event_applies_a_layout_and_reads_stats() throws Exception {
        String slug = "admin-created-" + System.nanoTime();
        Map<String, Object> event = Map.of(
                "name", "Admin Created", "slug", slug, "sport", "football",
                "startsAt", "2027-01-01T18:00:00Z", "venue", "Test Arena", "currency", "USD");

        String created = mvc.perform(post("/api/admin/events").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(event)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(slug))
                .andReturn().getResponse().getContentAsString();
        long eventId = json.readTree(created).get("id").asLong();

        Map<String, Object> layout = Map.of(
                "tiers", List.of(Map.of("code", "std", "name", "Standard", "price", 100, "perks", List.of(), "sortOrder", 0)),
                "sections", List.of(Map.of("code", "main", "name", "Main", "tierCode", "std",
                        "side", "n", "rows", 2, "seatsPerRow", 2, "sortOrder", 0)));
        mvc.perform(post("/api/admin/events/" + eventId + "/layout").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(layout)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatsCreated").value(4));

        mvc.perform(get("/api/admin/events/" + eventId + "/stats").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(4))
                .andExpect(jsonPath("$.total").value(4));

        mvc.perform(get("/api/admin/seats?eventId=" + eventId).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    /** Per-tier stats used to report booked as total - available, so a hold counted as a sale. */
    @Test
    void tier_stats_count_held_and_booked_seats_separately() throws Exception {
        String slug = "admin-tier-stats-" + System.nanoTime();
        Map<String, Object> event = Map.of(
                "name", "Tier Stats", "slug", slug, "sport", "football", "status", "onsale",
                "startsAt", "2027-01-01T18:00:00Z", "venue", "Test Arena", "currency", "USD");
        String created = mvc.perform(post("/api/admin/events").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(event)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long eventId = json.readTree(created).get("id").asLong();
        Map<String, Object> layout = Map.of(
                "tiers", List.of(Map.of("code", "std", "name", "Standard", "price", 100)),
                "sections", List.of(Map.of("code", "main", "name", "Main", "tierCode", "std",
                        "side", "n", "rows", 2, "seatsPerRow", 2)));
        mvc.perform(post("/api/admin/events/" + eventId + "/layout").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(layout)))
                .andExpect(status().isCreated());

        List<Long> seatIds = seatRepository.findByEventIdOrderByIdAsc(eventId).stream().map(Seat::getId).toList();
        CurrentUser holder = new CurrentUser("tier-holder", "tierholder", "th@apextick.local", "Tier Holder", Set.of("user"));
        holdService.hold(slug, List.of(seatIds.get(0)), holder);

        CurrentUser buyer = new CurrentUser("tier-buyer", "tierbuyer", "tb@apextick.local", "Tier Buyer", Set.of("user"));
        List<Long> bought = List.of(seatIds.get(1));
        holdService.hold(slug, bought, buyer);
        OrderResponse order = orderService.create(new CreateOrderRequest(eventId, bought),
                "tier-order-" + System.nanoTime(), buyer);
        paymentService.pay(UUID.fromString(order.id()),
                new PayRequest(new PaymentCard("4242424242424242", 12, 2030, "123", "T"), null, null, null),
                "tier-pay-" + System.nanoTime(), buyer);

        mvc.perform(get("/api/admin/events/" + eventId + "/stats").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(2))
                .andExpect(jsonPath("$.held").value(1))
                .andExpect(jsonPath("$.booked").value(1))
                .andExpect(jsonPath("$.byTier[0].tierCode").value("std"))
                .andExpect(jsonPath("$.byTier[0].total").value(4))
                .andExpect(jsonPath("$.byTier[0].available").value(2))
                .andExpect(jsonPath("$.byTier[0].held").value(1))
                .andExpect(jsonPath("$.byTier[0].booked").value(1));
    }

    @Test
    void admin_verifies_a_ticket_once_then_rejects_reuse() throws Exception {
        String slug = "australia-england-super-8";
        Long eventId = eventRepository.findBySlug(slug).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(1).toList();

        String sub = "verify-buyer";
        CurrentUser buyer = new CurrentUser(sub, "verifybuyer", "vb@apextick.local", "Verify Buyer", Set.of("user"));
        holdService.hold(slug, seatIds, buyer);
        OrderResponse order = orderService.create(new CreateOrderRequest(eventId, seatIds),
                "verify-order-" + System.nanoTime(), buyer);
        paymentService.pay(UUID.fromString(order.id()),
                new PayRequest(new PaymentCard("4242424242424242", 12, 2030, "123", "H"), null, null, null),
                "verify-pay-" + System.nanoTime(), buyer);

        Ticket ticket = ticketRepository.findByOrderId(UUID.fromString(order.id())).get(0);
        String verifyBody = json.writeValueAsString(Map.of("qrToken", ticket.getQrToken()));

        mvc.perform(post("/api/admin/tickets/verify").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.ticket.status").value("USED"));

        mvc.perform(post("/api/admin/tickets/verify").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_ALREADY_USED"));
    }

    @Test
    void admin_orders_list_is_reachable() throws Exception {
        mvc.perform(get("/api/admin/orders").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
