package com.apextick.booking.admin;

import com.apextick.booking.hold.HoldService;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.OrderStatus;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.payment.Payment;
import com.apextick.booking.payment.PaymentGatewaySpies;
import com.apextick.booking.payment.PaymentRepository;
import com.apextick.booking.payment.PaymentService;
import com.apextick.booking.payment.PaymentStatus;
import com.apextick.booking.payment.dto.PayRequest;
import com.apextick.booking.payment.model.PaymentCard;
import com.apextick.booking.payment.model.RefundResult;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.TestTokens;
import com.apextick.booking.ticket.Ticket;
import com.apextick.booking.ticket.TicketRepository;
import com.apextick.booking.ticket.TicketStatus;
import com.apextick.booking.web.ConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cancelling an event used to flip its badge and nothing else: its tickets went on opening gates,
 * its unpaid orders stayed payable, and nobody was told or refunded. Each test builds an event of
 * its own, so calling one off touches no other test's sales.
 */
class EventCancellationTest extends PaymentGatewaySpies {

    @Autowired MockMvc mvc;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentService paymentService;
    @Autowired PaymentRepository paymentRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired JdbcClient jdbc;

    private final ObjectMapper json = new ObjectMapper();

    private record Fixture(long eventId, String slug, String startsAt, List<Long> seatIds) {
    }

    private static String admin() {
        return "Bearer " + TestTokens.admin("cancel-admin", "canceladmin", "cancel-admin@apextick.local");
    }

    private static String steward() {
        return "Bearer " + TestTokens.withRoles("cancel-steward", "steward", "steward@apextick.local",
                "user", "scanner");
    }

    private static CurrentUser customer(String sub) {
        return new CurrentUser(sub, sub, sub + "@apextick.local", "Cancelled Fan", Set.of("user"));
    }

    /** An event on sale with eight seats of its own. */
    private Fixture eventOnSale(String name) throws Exception {
        String slug = "cancel-" + name + "-" + System.nanoTime();
        String startsAt = Instant.now().plus(Duration.ofDays(30)).toString();
        Map<String, Object> event = Map.of(
                "name", "Cancellation " + name, "slug", slug, "sport", "football", "status", "onsale",
                "startsAt", startsAt, "venue", "Test Arena", "currency", "USD");
        String created = mvc.perform(post("/api/admin/events").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(event)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long eventId = json.readTree(created).get("id").asLong();
        Map<String, Object> layout = Map.of(
                "tiers", List.of(Map.of("code", "std", "name", "Standard", "price", 100)),
                "sections", List.of(Map.of("code", "main", "name", "Main", "tierCode", "std",
                        "side", "n", "rows", 2, "seatsPerRow", 4)));
        mvc.perform(post("/api/admin/events/" + eventId + "/layout").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(layout)))
                .andExpect(status().isCreated());
        List<Long> seatIds = seatRepository.findByEventIdOrderByIdAsc(eventId).stream().map(Seat::getId).toList();
        return new Fixture(eventId, slug, startsAt, seatIds);
    }

    private UUID unpaidOrder(Fixture f, String buyer, List<Long> seatIds) {
        holdService.hold(f.slug(), seatIds, customer(buyer));
        return UUID.fromString(orderService.create(new CreateOrderRequest(f.eventId(), seatIds),
                buyer + "-order", customer(buyer)).id());
    }

    private UUID paidOrder(Fixture f, String buyer, List<Long> seatIds) {
        UUID orderId = unpaidOrder(f, buyer, seatIds);
        paymentService.pay(orderId,
                new PayRequest(new PaymentCard("4242424242424242", 12, 2030, "123", "X"), null, null, null),
                buyer + "-pay", customer(buyer));
        return orderId;
    }

    private ResultActions setStatus(long eventId, String body) throws Exception {
        return mvc.perform(patch("/api/admin/events/" + eventId + "/status").header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions save(Fixture f, String status) throws Exception {
        Map<String, Object> event = new HashMap<>(Map.of(
                "name", "Renamed", "slug", f.slug(), "sport", "football", "startsAt", f.startsAt(),
                "venue", "Test Arena", "currency", "USD"));
        if (status != null) {
            event.put("status", status);
        }
        return mvc.perform(put("/api/admin/events/" + f.eventId()).header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(event)));
    }

    private ResultActions scan(Ticket ticket) throws Exception {
        return mvc.perform(post("/api/gate/scans").header("Authorization", steward())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrToken\":\"" + ticket.getQrToken() + "\",\"eventId\":" + ticket.getEventId() + "}"));
    }

    private OrderStatus orderStatus(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    private List<TicketStatus> ticketStatuses(UUID orderId) {
        return ticketRepository.findByOrderId(orderId).stream().map(Ticket::getStatus).toList();
    }

    private List<SeatStatus> seatStatuses(Long... seatIds) {
        return seatRepository.findAllById(Arrays.asList(seatIds)).stream().map(Seat::getStatus).toList();
    }

    private long published(String type, UUID... aggregates) {
        return jdbc.sql("SELECT count(*) FROM outbox_events WHERE type = :type AND aggregate_id IN (:ids)")
                .param("type", type)
                .param("ids", Arrays.stream(aggregates).map(UUID::toString).toList())
                .query(Long.class).single();
    }

    @Test
    void cancelling_an_event_refunds_what_was_paid_cancels_what_was_not_and_tells_every_buyer() throws Exception {
        Fixture f = eventOnSale("cascade");
        List<Long> s = f.seatIds();
        UUID paid = paidOrder(f, "cancel-paid", List.of(s.get(0), s.get(1)));
        UUID admitted = paidOrder(f, "cancel-admitted", List.of(s.get(2)));
        UUID unpaid = unpaidOrder(f, "cancel-unpaid", List.of(s.get(3)));
        holdService.hold(f.slug(), List.of(s.get(4)), customer("cancel-browsing"));
        scan(ticketRepository.findByOrderId(admitted).getFirst()).andExpect(status().isOk());

        setStatus(f.eventId(), "{\"status\":\"cancelled\",\"reason\":\"Floodlight failure\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));

        // everyone who paid is refunded, including the buyer already let in
        assertThat(orderStatus(paid)).isEqualTo(OrderStatus.REFUNDED);
        assertThat(orderStatus(admitted)).isEqualTo(OrderStatus.REFUNDED);
        assertThat(paymentRepository.findByOrderIdOrderByCreatedAtAsc(paid).getFirst().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(paymentRepository.findByOrderIdOrderByCreatedAtAsc(admitted).getFirst().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(ticketStatuses(paid)).containsOnly(TicketStatus.CANCELLED);
        assertThat(ticketStatuses(admitted)).containsOnly(TicketStatus.USED);
        assertThat(orderRepository.findById(unpaid).orElseThrow()).satisfies(o -> {
            assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(o.getCancelReason()).isEqualTo("EVENT_CANCELLED");
        });
        assertThat(seatStatuses(s.get(0), s.get(1), s.get(3), s.get(4))).containsOnly(SeatStatus.AVAILABLE);
        // each buyer is told once, about their own order
        assertThat(published("event.cancelled", paid, admitted, unpaid)).isEqualTo(3);
        assertThat(jdbc.sql("SELECT cancel_reason FROM events WHERE id = :id").param("id", f.eventId())
                .query(String.class).single()).isEqualTo("Floodlight failure");

        // and nothing about the event sells or admits any more
        scan(ticketRepository.findByOrderId(paid).getFirst())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_ADMITTING"));
        assertThatThrownBy(() -> holdService.hold(f.slug(), List.of(s.get(5)), customer("cancel-late")))
                .isInstanceOf(ConflictException.class);
    }

    /** The cancellation does not wait on the provider: what it refuses stays owed, and the reconciler asks again. */
    @Test
    void a_refund_refused_while_cancelling_stays_owed_and_the_event_is_cancelled_all_the_same() throws Exception {
        Fixture f = eventOnSale("refused");
        UUID paid = paidOrder(f, "cancel-refused", List.of(f.seatIds().get(0)));
        doReturn(new RefundResult(false, null, "Refunds are paused")).when(mockGateway).refund(any(), any(), any(), any());

        setStatus(f.eventId(), "{\"status\":\"cancelled\"}").andExpect(status().isOk());

        assertThat(orderStatus(paid)).isEqualTo(OrderStatus.REFUNDED);
        Payment payment = paymentRepository.findByOrderIdOrderByCreatedAtAsc(paid).getFirst();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_REQUIRED);
        assertThat(payment.getFailureCode()).isEqualTo("event_cancelled");
        assertThat(payment.getRefundError()).isEqualTo("Refunds are paused");
    }

    @Test
    void a_cancelled_event_stays_cancelled_and_cancelling_it_again_refunds_nobody_twice() throws Exception {
        Fixture f = eventOnSale("terminal");
        UUID paid = paidOrder(f, "cancel-twice", List.of(f.seatIds().get(0)));
        setStatus(f.eventId(), "{\"status\":\"cancelled\"}").andExpect(status().isOk());

        setStatus(f.eventId(), "{\"status\":\"cancelled\"}").andExpect(status().isOk());
        setStatus(f.eventId(), "{\"status\":\"onsale\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.from").value("cancelled"))
                .andExpect(jsonPath("$.to").value("onsale"));

        verify(mockGateway, times(1)).refund(any(), any(), any(), any());
        assertThat(published("event.cancelled", paid)).isEqualTo(1);
    }

    /**
     * A PUT is a full replace. One that left the status out used to un-publish a live event; one that
     * named a different status would have cancelled it without refunding anybody.
     */
    @Test
    void an_event_that_has_sold_stays_published_and_saving_it_cannot_change_its_status() throws Exception {
        Fixture sold = eventOnSale("sold");
        paidOrder(sold, "cancel-sold", List.of(sold.seatIds().get(0)));

        setStatus(sold.eventId(), "{\"status\":\"draft\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_STATUS_TRANSITION"));
        save(sold, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.status").value("onsale"));
        save(sold, "cancelled")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_STATUS_TRANSITION"));
        verify(mockGateway, times(0)).refund(any(), any(), any(), any());

        Fixture unsold = eventOnSale("unsold");
        setStatus(unsold.eventId(), "{\"status\":\"draft\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("draft"));
    }
}
