package com.apextick.booking.platform;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.IntegrationTest;
import com.apextick.booking.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The rate limiter over HTTP: which URIs land in which bucket, and what a throttled
 * caller actually receives (429 + {@code Retry-After} + {@code RATE_LIMITED}).
 *
 * <p>{@code RateLimitTest} calls {@code RedisRateLimiter} directly, so the interceptor's
 * URI matching, subject resolution and the problem+json mapping never ran anywhere. The
 * limiter is off in the test profile (it would throttle every other test), so this class
 * turns it on with a tiny budget -- which puts it in its own application context, hence
 * its own containers.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.hold.limit=2",
        "app.rate-limit.hold.window=PT1M",
        "app.rate-limit.order.limit=2",
        "app.rate-limit.order.window=PT1M",
})
class RateLimitHttpTest {

    // a slug no other test class touches, so the budget is the only thing under test
    private static final String SLUG = "newcastle-chelsea";

    @Autowired MockMvc mvc;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;

    private List<Long> availableSeatIds(int limit) {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        return seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .map(Seat::getId).limit(limit).toList();
    }

    private MockHttpServletRequestBuilder hold(Long seatId, String token) {
        return post("/api/events/" + SLUG + "/holds")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seatIds\":[" + seatId + "]}");
    }

    @Test
    void the_third_hold_in_the_window_is_429_with_retry_after() throws Exception {
        String token = TestTokens.user("rl-burst", "rlburst", "rlburst@apextick.local");
        List<Long> ids = availableSeatIds(3);

        mvc.perform(hold(ids.get(0), token)).andExpect(status().isCreated());
        mvc.perform(hold(ids.get(1), token)).andExpect(status().isCreated());

        mvc.perform(hold(ids.get(2), token))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("[1-9][0-9]*")))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());

        // the throttled request never reached the service: the seat is untouched
        assertThat(seatRepository.findById(ids.get(2)).orElseThrow().getStatus())
                .isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void reads_are_not_counted_against_the_hold_budget() throws Exception {
        String token = TestTokens.user("rl-reader", "rlreader", "rlreader@apextick.local");

        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/events/" + SLUG + "/holds/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        mvc.perform(hold(availableSeatIds(1).get(0), token)).andExpect(status().isCreated());
    }

    @Test
    void each_subject_gets_its_own_window() throws Exception {
        String heavy = TestTokens.user("rl-heavy", "rlheavy", "rlheavy@apextick.local");
        String light = TestTokens.user("rl-light", "rllight", "rllight@apextick.local");
        List<Long> ids = availableSeatIds(4);

        mvc.perform(hold(ids.get(0), heavy)).andExpect(status().isCreated());
        mvc.perform(hold(ids.get(1), heavy)).andExpect(status().isCreated());
        mvc.perform(hold(ids.get(2), heavy)).andExpect(status().isTooManyRequests());

        // a different sub starts with a full budget
        mvc.perform(hold(ids.get(3), light)).andExpect(status().isCreated());
    }

    @Test
    void the_pay_path_is_throttled_by_the_order_bucket() throws Exception {
        String token = TestTokens.user("rl-payer", "rlpayer", "rlpayer@apextick.local");
        String path = "/api/orders/" + UUID.randomUUID() + "/pay";

        // the order does not exist, so these 404 -- but the limiter counts them, which is
        // exactly what makes the bucket a defence rather than a formality
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(path)
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound());
        }

        mvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("[1-9][0-9]*")))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void order_creation_shares_the_order_bucket_with_pay() throws Exception {
        String token = TestTokens.user("rl-buyer", "rlbuyer", "rlbuyer@apextick.local");

        // an unknown event, so the handler 404s without creating anything -- the point is
        // that /api/orders itself is counted, the other half of the order bucket's matching
        for (int i = 0; i < 2; i++) {
            mvc.perform(createOrder(token)).andExpect(status().isNotFound());
        }

        mvc.perform(createOrder(token))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    private MockHttpServletRequestBuilder createOrder(String token) {
        return post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":999999999,\"seatIds\":[999999999]}");
    }

    @Test
    void posts_outside_the_two_buckets_are_never_throttled() throws Exception {
        String admin = TestTokens.admin("rl-admin", "rladmin", "rladmin@apextick.local");

        // /api/admin/seats/{id}/release matches neither regex, so it passes through the
        // interceptor every time (well past the budget of 2)
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/admin/seats/999999999/release")
                            .header("Authorization", "Bearer " + admin))
                    .andExpect(status().isNotFound());
        }
    }
}
