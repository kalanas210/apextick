package com.apextick.booking.realtime;

import com.apextick.booking.realtime.dto.SeatStatusChange;
import com.apextick.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@IntegrationTest
class WebSocketBroadcastTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    RealtimePublisher realtimePublisher;

    @Test
    void a_seat_status_broadcast_reaches_a_subscribed_client() throws Exception {
        // default SimpleMessageConverter -> deliver raw bytes regardless of content type
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());

        StompSession session = client.connectAsync(
                "ws://localhost:" + port + "/api/ws",
                new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);

        long eventId = System.nanoTime() % 1_000_000L;
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        session.subscribe("/topic/events/" + eventId + "/seats", new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                received.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });

        // retry publishing until the SUBSCRIBE has registered and a frame arrives
        String body = await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(500)).until(() -> {
            realtimePublisher.seatStatusChanged(eventId,
                    List.of(new SeatStatusChange(1L, "HELD", Instant.now())));
            return received.poll(400, TimeUnit.MILLISECONDS);
        }, b -> b != null);

        assertThat(body).contains("SEATS_CHANGED");
        assertThat(body).contains("HELD");
        assertThat(body).contains("\"eventId\":" + eventId);

        session.disconnect();
        client.stop();
    }
}
