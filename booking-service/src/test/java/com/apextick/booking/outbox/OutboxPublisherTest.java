package com.apextick.booking.outbox;

import com.apextick.booking.messaging.RabbitConfig;
import com.apextick.booking.outbox.payload.SeatHeldPayload;
import com.apextick.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@IntegrationTest
class OutboxPublisherTest {

    @Autowired DomainEventPublisher publisher;
    @Autowired OutboxRepository outboxRepository;
    @Autowired RabbitTemplate rabbitTemplate;

    @Test
    void writes_to_outbox_then_relays_an_envelope_to_rabbit() {
        long marker = System.nanoTime();
        publisher.publish(EventTypes.SEAT_HELD, "seat", String.valueOf(marker),
                new SeatHeldPayload(marker, "Z9", "tester", Instant.now()));

        // the durable seat-held-notifications queue (declared by RabbitConfig) is bound to
        // seat.held; the @Scheduled OutboxPublisher relays our row there. Drain until we see it.
        String body = await().atMost(Duration.ofSeconds(15)).until(() -> {
            Message m = rabbitTemplate.receive(RabbitConfig.SEAT_HELD_QUEUE, 500);
            return m == null ? null : new String(m.getBody(), StandardCharsets.UTF_8);
        }, b -> b != null && b.contains(String.valueOf(marker)));

        assertThat(body).contains("\"type\":\"seat.held\"");
        assertThat(body).contains("\"aggregateType\":\"seat\"");
        assertThat(body).contains("\"eventId\":");
        assertThat(body).contains("\"payload\":");
        assertThat(body).contains("Z9");
    }

    @Test
    void published_rows_are_drained_from_pending() {
        publisher.publish(EventTypes.SEAT_HELD, "seat", "p-" + System.nanoTime(),
                new SeatHeldPayload(1L, "A1", "u", Instant.now()));
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(outboxRepository.countPending()).isZero());
    }
}
