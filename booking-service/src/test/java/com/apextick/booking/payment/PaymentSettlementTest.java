package com.apextick.booking.payment;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.TestTokens;
import com.apextick.booking.ticket.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What a pay request does with a charge that goes through after its order stopped being payable.
 * The gateway call is the one step that runs outside a transaction, and against a real provider it
 * can take seconds: time enough for the payment window to close underneath it.
 */
class PaymentSettlementTest extends PaymentGatewaySpies {

    private static final String SLUG = "chennai-mumbai-return";
    private static final String VISA_OK = """
            {"card":{"number":"4242424242424242","expMonth":12,"expYear":2030,"cvc":"123","holder":"Late Buyer"}}""";

    @Autowired MockMvc mvc;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired PaymentRepository paymentRepository;

    @Test
    void a_charge_that_completes_after_its_order_expired_is_refunded() throws Exception {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(2).toList();
        String sub = "late-buyer";
        CurrentUser user = new CurrentUser(sub, "latebuyer", "late@apextick.local", "Late Buyer", Set.of("user"));
        holdService.hold(SLUG, seatIds, user);
        UUID orderId = UUID.fromString(orderService.create(new CreateOrderRequest(eventId, seatIds),
                "late-order-" + System.nanoTime(), user).id());

        // the sweeper expires the order while the gateway is still taking the money
        doAnswer(charge -> {
            orderService.expire(orderId);
            return charge.callRealMethod();
        }).when(mockGateway).initiate(any());

        mvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + TestTokens.user(sub, "latebuyer", "late@apextick.local"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(VISA_OK))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.failureCode").value("order_closed"))
                .andExpect(jsonPath("$.order.status").value("EXPIRED"));

        Payment refunded = paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId).getFirst();
        verify(mockGateway).refund(eq(refunded.getProviderRef()), any(), any(), eq("refund:" + refunded.getId()));
        assertThat(refunded.getRefundRef()).isNotBlank();
        assertThat(ticketRepository.findByOrderId(orderId)).isEmpty();
    }
}
