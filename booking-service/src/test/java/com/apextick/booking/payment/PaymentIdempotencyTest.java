package com.apextick.booking.payment;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.order.dto.OrderResponse;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.IntegrationTest;
import com.apextick.booking.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class PaymentIdempotencyTest {

    private static final String SLUG = "qualifier-one";
    private static final String VISA_OK = """
            {"card":{"number":"4242424242424242","expMonth":12,"expYear":2030,"cvc":"123","holder":"H"}}""";
    private static final String VISA_DECLINED = """
            {"card":{"number":"4000000000000002","expMonth":12,"expYear":2030,"cvc":"123","holder":"H"}}""";

    @Autowired MockMvc mvc;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;

    /** An unpaid order, and the bearer token of the buyer who owns it. */
    private record Unpaid(String id, String token) {
    }

    private Unpaid unpaidOrder(String sub) {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(2).toList();

        CurrentUser user = new CurrentUser(sub, sub, sub + "@apextick.local", "Idem Pay", Set.of("user"));
        holdService.hold(SLUG, seatIds, user);
        OrderResponse order = orderService.create(new CreateOrderRequest(eventId, seatIds),
                sub + "-order-" + System.nanoTime(), user);
        return new Unpaid(order.id(), "Bearer " + TestTokens.user(sub, sub, sub + "@apextick.local"));
    }

    private ResultActions pay(Unpaid order, String idempotencyKey, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/orders/" + order.id() + "/pay")
                .header("Authorization", order.token())
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(idempotencyKey == null ? request : request.header("Idempotency-Key", idempotencyKey));
    }

    private ResultActions attempts(Unpaid order) throws Exception {
        return mvc.perform(get("/api/orders/" + order.id() + "/payments").header("Authorization", order.token()))
                .andExpect(status().isOk());
    }

    @Test
    void paying_a_second_time_does_not_charge_again() throws Exception {
        Unpaid order = unpaidOrder("idem-pay");

        for (int i = 0; i < 2; i++) {
            pay(order, UUID.randomUUID().toString(), VISA_OK)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                    .andExpect(jsonPath("$.order.status").value("PAID"));
        }

        // exactly one payment attempt exists for the order
        attempts(order)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"));
    }

    /**
     * A key names one attempt. Sending it again -- a retry after a dropped connection, a second
     * click on the same form -- gets that attempt's answer back and charges nothing, whatever card
     * the resend carries. Trying again means a new key.
     */
    @Test
    void resending_an_attempt_gets_its_answer_back_instead_of_a_new_charge() throws Exception {
        Unpaid order = unpaidOrder("idem-resend");
        String key = UUID.randomUUID().toString();

        pay(order, key, VISA_DECLINED)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value("FAILED"));
        pay(order, key, VISA_OK)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.order.status").value("PENDING_PAYMENT"));
        attempts(order).andExpect(jsonPath("$.length()").value(1));

        pay(order, UUID.randomUUID().toString(), VISA_OK)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        attempts(order).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void an_attempt_without_an_idempotency_key_is_refused_before_anything_is_charged() throws Exception {
        Unpaid order = unpaidOrder("idem-keyless");

        pay(order, null, VISA_OK)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_MISSING"));
        attempts(order).andExpect(jsonPath("$.length()").value(0));
    }
}
