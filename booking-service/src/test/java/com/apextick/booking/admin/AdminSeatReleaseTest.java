package com.apextick.booking.admin;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.order.OrderStatus;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.order.dto.OrderResponse;
import com.apextick.booking.realtime.RealtimePublisher;
import com.apextick.booking.realtime.dto.SeatStatusChange;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.IntegrationTest;
import com.apextick.booking.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin force-release used to be a bare UPDATE: no seat.released event, no live seat-map
 * update, and the unpaid order covering the seat left payable -- so the buyer's card would be
 * charged and refunded, and every other buyer stayed locked out by that order until it expired.
 * It now runs through the same release path as every other one.
 */
@IntegrationTest
class AdminSeatReleaseTest {

    private static final String SLUG = "newcastle-chelsea";

    @Value("${local.server.port}")
    int port;

    @Autowired MockMvc mvc;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired RealtimePublisher realtime;
    @Autowired JdbcClient jdbc;

    @Test
    void force_release_frees_the_seat_cancels_its_order_and_announces_both() throws Exception {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(1).toList();
        Long seatId = seatIds.get(0);

        CurrentUser buyer = new CurrentUser("stuck-buyer", "stuckbuyer", "stuck@apextick.local",
                "Stuck Buyer", Set.of("user"));
        holdService.hold(SLUG, seatIds, buyer);
        OrderResponse order = orderService.create(new CreateOrderRequest(eventId, seatIds),
                "admin-release-" + System.nanoTime(), buyer);

        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        StompSession session = client.connectAsync("ws://localhost:" + port + "/api/ws",
                new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);
        try {
            session.subscribe("/topic/events/" + eventId + "/seats", new StompFrameHandler() {
                @Override
                @NonNull
                public Type getPayloadType(@NonNull StompHeaders headers) {
                    return byte[].class;
                }

                @Override
                public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                    frames.add(new String((byte[]) payload, StandardCharsets.UTF_8));
                }
            });
            awaitSubscription(eventId, frames);

            mvc.perform(post("/api/admin/seats/" + seatId + "/release")
                            .header("Authorization", "Bearer "
                                    + TestTokens.admin("admin-1", "admin1", "admin@apextick.local")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.heldBy").doesNotExist());

            String frame = await().atMost(Duration.ofSeconds(10))
                    .until(() -> frames.poll(500, TimeUnit.MILLISECONDS),
                            f -> f != null && f.contains("\"seatId\":" + seatId));
            assertThat(frame).contains("SEATS_CHANGED").contains("AVAILABLE");
        } finally {
            session.disconnect();
            client.stop();
        }

        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);

        // the outbox carries the release, tagged as the admin action it was
        Long releasedRows = jdbc.sql("""
                        SELECT count(*) FROM outbox_events
                         WHERE type = 'seat.released' AND aggregate_id = :seatId
                           AND payload ->> 'reason' = 'ADMIN'
                        """)
                .param("seatId", String.valueOf(seatId))
                .query(Long.class).single();
        assertThat(releasedRows).isEqualTo(1L);

        // and the order that covered the seat is closed rather than left payable
        assertThat(orderRepository.findById(UUID.fromString(order.id())).orElseThrow())
                .satisfies(o -> {
                    assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                    assertThat(o.getCancelReason()).isEqualTo("ADMIN_RELEASED");
                });
    }

    /**
     * A STOMP SUBSCRIBE registers asynchronously, so probe the topic until a frame comes back
     * before doing the thing under test -- otherwise a passing release could still look silent.
     */
    private void awaitSubscription(Long eventId, BlockingQueue<String> frames) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(500)).until(() -> {
            realtime.seatStatusChanged(eventId, List.of(new SeatStatusChange(-1L, "HELD", null)));
            return frames.poll(400, TimeUnit.MILLISECONDS);
        }, f -> f != null);
        frames.clear();
    }
}
