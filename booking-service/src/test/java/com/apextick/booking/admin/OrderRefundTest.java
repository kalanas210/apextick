package com.apextick.booking.admin;

import com.apextick.booking.catalog.EventRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The box office's refund, over HTTP. PAID used to have no way out: a customer who could not
 * come, or whose order went wrong after it was paid, stayed charged, and their seats stayed booked
 * for nobody.
 */
class OrderRefundTest extends PaymentGatewaySpies {

    // OrderPaymentFlowTest buys here too, under buyers of its own
    private static final String SLUG = "india-australia-semi-final";
    private static final String ADMIN_SUB = "refund-admin";

    @Autowired MockMvc mvc;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentService paymentService;
    @Autowired PaymentRepository paymentRepository;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired JdbcClient jdbc;

    private record Pending(UUID orderId, List<Long> seatIds) {
    }

    private record Sale(UUID orderId, List<Long> seatIds, List<Ticket> tickets, Payment payment) {
    }

    private static String admin() {
        return "Bearer " + TestTokens.admin(ADMIN_SUB, "refundadmin", "refundadmin@apextick.local");
    }

    private static String steward() {
        return "Bearer " + TestTokens.withRoles("refund-steward", "steward", "steward@apextick.local",
                "user", "scanner");
    }

    private static CurrentUser customer(String sub) {
        return new CurrentUser(sub, sub, sub + "@apextick.local", "Refund Buyer", Set.of("user"));
    }

    /** Seats held and an order created for them, not yet paid. */
    private Pending pendingOrder(String buyer, int seats) {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(seats).toList();
        holdService.hold(SLUG, seatIds, customer(buyer));
        UUID orderId = UUID.fromString(orderService.create(new CreateOrderRequest(eventId, seatIds),
                buyer + "-order-" + UUID.randomUUID(), customer(buyer)).id());
        return new Pending(orderId, seatIds);
    }

    /** A paid order, its tickets issued, paid through the mock gateway. */
    private Sale buy(String buyer, int seats) {
        Pending pending = pendingOrder(buyer, seats);
        paymentService.pay(pending.orderId(),
                new PayRequest(new PaymentCard("4242424242424242", 12, 2030, "123", "R"), null, null, null),
                buyer + "-pay-" + UUID.randomUUID(), customer(buyer));
        Payment payment = paymentRepository.findByOrderIdAndStatus(pending.orderId(), PaymentStatus.SUCCEEDED)
                .getFirst();
        return new Sale(pending.orderId(), pending.seatIds(), ticketRepository.findByOrderId(pending.orderId()),
                payment);
    }

    private ResultActions refund(UUID orderId) throws Exception {
        return refund(orderId, "{\"reason\":\"Cannot attend\"}");
    }

    private ResultActions refund(UUID orderId, String body) throws Exception {
        return mvc.perform(post("/api/admin/orders/" + orderId + "/refund")
                .header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions retry(UUID orderId) throws Exception {
        return mvc.perform(post("/api/admin/orders/" + orderId + "/refund/retry").header("Authorization", admin()));
    }

    private ResultActions scan(Ticket ticket) throws Exception {
        return mvc.perform(post("/api/gate/scans")
                .header("Authorization", steward())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrToken\":\"" + ticket.getQrToken() + "\",\"eventId\":" + ticket.getEventId() + "}"));
    }

    private List<SeatStatus> seatStatuses(List<Long> seatIds) {
        return seatRepository.findAllById(seatIds).stream().map(Seat::getStatus).toList();
    }

    private long refundsAnnounced(Payment payment) {
        return jdbc.sql("SELECT count(*) FROM outbox_events WHERE type = 'payment.refunded' AND aggregate_id = :id")
                .param("id", payment.getId().toString()).query(Long.class).single();
    }

    private long seatReleases(List<Long> seatIds) {
        return jdbc.sql("SELECT count(*) FROM outbox_events WHERE type = 'seat.released' AND aggregate_id IN (:ids)")
                .param("ids", seatIds.stream().map(String::valueOf).toList()).query(Long.class).single();
    }

    @Test
    void the_box_office_refunds_a_paid_order_and_its_seats_go_back_on_sale() throws Exception {
        Sale sale = buy("refund-full", 2);
        long releasesBefore = seatReleases(sale.seatIds());

        refund(sale.orderId(), "{\"reason\":\"Customer cannot attend\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("REFUNDED"))
                .andExpect(jsonPath("$.order.refundedAt").exists())
                .andExpect(jsonPath("$.refundedBy").value(ADMIN_SUB))
                .andExpect(jsonPath("$.refundReason").value("Customer cannot attend"))
                .andExpect(jsonPath("$.refundBlockedBy").value("NOT_PAID"))
                .andExpect(jsonPath("$.tickets.length()").value(2))
                .andExpect(jsonPath("$.tickets[*].status", everyItem(is("CANCELLED"))))
                .andExpect(jsonPath("$.payments[0].status").value("REFUNDED"))
                .andExpect(jsonPath("$.payments[0].refundRef").exists())
                .andExpect(jsonPath("$.payments[0].refundAttempts").value(1));

        Payment paid = sale.payment();
        verify(mockGateway).refund(eq(paid.getProviderRef()), any(), eq(paid.getCurrency()),
                eq("refund:" + paid.getId()));
        assertThat(seatStatuses(sale.seatIds())).containsOnly(SeatStatus.AVAILABLE);
        assertThat(seatReleases(sale.seatIds())).isEqualTo(releasesBefore + 2);
        assertThat(refundsAnnounced(paid)).isEqualTo(1);
        // a voided ticket opens no gate
        scan(sale.tickets().getFirst())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_CANCELLED"));
    }

    /**
     * The decision to refund does not wait on the provider: the tickets are void and the seats back
     * on sale either way. What the provider refuses stays owed, visibly, until it is asked again and
     * accepts -- and every ask names the same refund, so it can only ever be paid once.
     */
    @Test
    void a_refund_the_provider_refuses_is_still_owed_and_goes_through_when_asked_again() throws Exception {
        Sale sale = buy("refund-refused", 1);
        doReturn(new RefundResult(false, null, "Refunds are paused on this account"))
                .when(mockGateway).refund(any(), any(), any(), any());

        refund(sale.orderId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("REFUNDED"))
                .andExpect(jsonPath("$.tickets[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.payments[0].status").value("REFUND_REQUIRED"))
                .andExpect(jsonPath("$.payments[0].refundError").value("Refunds are paused on this account"))
                .andExpect(jsonPath("$.payments[0].refundAttempts").value(1));
        assertThat(seatStatuses(sale.seatIds())).containsOnly(SeatStatus.AVAILABLE);
        assertThat(refundsAnnounced(sale.payment())).isZero();

        doReturn(new RefundResult(true, "mock_refund_second_ask", null))
                .when(mockGateway).refund(any(), any(), any(), any());
        retry(sale.orderId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments[0].status").value("REFUNDED"))
                .andExpect(jsonPath("$.payments[0].refundRef").value("mock_refund_second_ask"))
                .andExpect(jsonPath("$.payments[0].refundError").doesNotExist())
                .andExpect(jsonPath("$.payments[0].refundAttempts").value(2));

        assertThat(refundsAnnounced(sale.payment())).isEqualTo(1);
        verify(mockGateway, times(2)).refund(any(), any(), any(), eq("refund:" + sale.payment().getId()));
        retry(sale.orderId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_NOT_OWED"));
    }

    /**
     * Someone has walked in on the ticket, so the money is not simply owed back. When letting them
     * in was the mistake, the admission is undone at the gate first, and then the refund goes.
     */
    @Test
    void an_order_with_a_ticket_used_at_the_gate_is_not_refunded_until_the_admission_is_undone() throws Exception {
        Sale sale = buy("refund-used", 1);
        Ticket ticket = sale.tickets().getFirst();
        scan(ticket).andExpect(status().isOk());

        mvc.perform(get("/api/admin/orders/" + sale.orderId()).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundBlockedBy").value("TICKETS_USED"))
                .andExpect(jsonPath("$.tickets[0].status").value("USED"));
        refund(sale.orderId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_TICKETS_USED"))
                .andExpect(jsonPath("$.ticketIds[0]").value(ticket.getId().toString()));

        verify(mockGateway, never()).refund(any(), any(), any(), any());
        assertThat(orderRepository.findById(sale.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findById(sale.payment().getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(seatStatuses(sale.seatIds())).containsOnly(SeatStatus.BOOKED);

        mvc.perform(post("/api/gate/tickets/" + ticket.getId() + "/unadmit")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Scanned by mistake\"}"))
                .andExpect(status().isOk());
        refund(sale.orderId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("REFUNDED"));
    }

    @Test
    void only_a_paid_order_is_refunded_and_only_once() throws Exception {
        Pending unpaid = pendingOrder("refund-unpaid", 1);
        refund(unpaid.orderId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_REFUNDABLE"))
                .andExpect(jsonPath("$.orderStatus").value("PENDING_PAYMENT"));

        Sale sale = buy("refund-twice", 1);
        refund(sale.orderId()).andExpect(status().isOk());
        refund(sale.orderId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_REFUNDABLE"))
                .andExpect(jsonPath("$.orderStatus").value("REFUNDED"));

        verify(mockGateway, times(1)).refund(any(), any(), any(), any());
    }

    @Test
    void a_refund_has_to_say_why() throws Exception {
        Sale sale = buy("refund-reasonless", 1);

        refund(sale.orderId(), "{}").andExpect(status().isBadRequest());
        refund(sale.orderId(), "{\"reason\":\"   \"}").andExpect(status().isBadRequest());
        refund(UUID.randomUUID()).andExpect(status().isNotFound());

        assertThat(orderRepository.findById(sale.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        verify(mockGateway, never()).refund(any(), any(), any(), any());
    }
}
