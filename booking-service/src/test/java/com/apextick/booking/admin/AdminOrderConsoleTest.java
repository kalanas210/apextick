package com.apextick.booking.admin;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.payment.PaymentGatewaySpies;
import com.apextick.booking.payment.PaymentRepository;
import com.apextick.booking.payment.PaymentService;
import com.apextick.booking.payment.PaymentStatus;
import com.apextick.booking.payment.RefundService;
import com.apextick.booking.payment.dto.PayRequest;
import com.apextick.booking.payment.model.PaymentCard;
import com.apextick.booking.payment.model.RefundResult;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The orders console's list. A customer phoning the box office has an order number or only an
 * email address, and the list could filter by status and nothing else; it also left every order's
 * ticket ids empty.
 */
class AdminOrderConsoleTest extends PaymentGatewaySpies {

    // OrderRefundTest and OrderPaymentFlowTest buy here too, under buyers of their own
    private static final String SLUG = "india-australia-semi-final";
    private static final CurrentUser ADMIN = new CurrentUser("console-admin", "console-admin",
            "console-admin@apextick.local", "Console Admin", Set.of("admin"));

    @Autowired MockMvc mvc;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentService paymentService;
    @Autowired PaymentRepository paymentRepository;
    @Autowired RefundService refundService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;

    /** A one-seat order paid through the mock gateway, bought by {@code sub} under {@code name}. */
    private UUID paidOrder(String sub, String name) {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(1).toList();
        CurrentUser buyer = new CurrentUser(sub, sub, sub + "@apextick.local", name, Set.of("user"));
        holdService.hold(SLUG, seatIds, buyer);
        UUID orderId = UUID.fromString(orderService.create(new CreateOrderRequest(eventId, seatIds),
                sub + "-order", buyer).id());
        paymentService.pay(orderId,
                new PayRequest(new PaymentCard("4242424242424242", 12, 2030, "123", "C"), null, null, null),
                sub + "-pay", buyer);
        return orderId;
    }

    private ResultActions search(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/admin/orders")
                .header("Authorization", "Bearer " + TestTokens.admin("console-admin", "consoleadmin",
                        "console-admin@apextick.local"));
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request).andExpect(status().isOk());
    }

    private static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void the_box_office_finds_an_order_by_its_number_or_the_customers_email_or_name() throws Exception {
        String tag = tag();
        UUID orderId = paidOrder("console-" + tag, "Priya Raman " + tag);
        String number = orderRepository.findById(orderId).orElseThrow().getOrderNumber();

        search("q", number.toLowerCase())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderId.toString()))
                .andExpect(jsonPath("$.content[0].userEmail").value("console-" + tag + "@apextick.local"))
                .andExpect(jsonPath("$.content[0].userName").value("Priya Raman " + tag))
                // the list used to send every order's ticket ids empty
                .andExpect(jsonPath("$.content[0].ticketIds.length()").value(1))
                .andExpect(jsonPath("$.content[0].items[0].ticketId").exists());
        search("q", ("Console-" + tag + "@ApexTick").toUpperCase())
                .andExpect(jsonPath("$.totalElements").value(1));
        search("q", "raman " + tag)
                .andExpect(jsonPath("$.totalElements").value(1));
        // typed, a % is a character to look for, not a wildcard that lists every order
        search("q", "%").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void the_box_office_narrows_the_list_to_the_refunds_still_owed() throws Exception {
        String tag = tag();
        UUID owed = paidOrder("console-owed-" + tag, "Owed " + tag);
        paidOrder("console-settled-" + tag, "Settled " + tag);
        doReturn(new RefundResult(false, null, "Refunds are paused")).when(mockGateway).refund(any(), any(), any(), any());
        refundService.refundOrder(owed, "Cannot attend", ADMIN);

        search("q", tag, "refundRequired", "true")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(owed.toString()));
        search("q", tag, "status", "REFUNDED").andExpect(jsonPath("$.totalElements").value(1));
        search("q", tag, "status", "PAID").andExpect(jsonPath("$.totalElements").value(1));
        search("q", tag).andExpect(jsonPath("$.totalElements").value(2));

        doReturn(new RefundResult(true, "mock_refund_finally", null)).when(mockGateway).refund(any(), any(), any(), any());
        refundService.retry(paymentRepository.findByOrderIdAndStatus(owed, PaymentStatus.REFUND_REQUIRED).getFirst().getId());

        search("q", tag, "refundRequired", "true").andExpect(jsonPath("$.totalElements").value(0));
    }
}
