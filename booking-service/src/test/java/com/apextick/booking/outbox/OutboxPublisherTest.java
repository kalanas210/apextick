package com.apextick.booking.outbox;

import com.apextick.booking.messaging.RabbitConfig;
import com.apextick.booking.outbox.payload.SeatHeldPayload;
import com.apextick.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@IntegrationTest
class OutboxPublisherTest {

    @Autowired DomainEventPublisher publisher;
    @Autowired OutboxRepository outboxRepository;
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired AmqpAdmin amqpAdmin;

    @Test
    void writes_to_outbox_then_relays_an_envelope_to_rabbit() {
        // this service declares no queues of its own (consumers own theirs), so bind a
        // throwaway one to seat.held: exclusive and auto-delete, gone with the connection.
        // (Not AnonymousQueue: it sets x-queue-master-locator, which RabbitMQ 4 rejects.)
        Queue queue = QueueBuilder.nonDurable("outbox-test-" + UUID.randomUUID()).exclusive().autoDelete().build();
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue)
                .to(new TopicExchange(RabbitConfig.EXCHANGE)).with(EventTypes.SEAT_HELD));
        try {
            long marker = System.nanoTime();
            publisher.publish(EventTypes.SEAT_HELD, "seat", String.valueOf(marker),
                    new SeatHeldPayload(marker, "Z9", "tester", Instant.now()));

            // the @Scheduled OutboxPublisher relays our row to the exchange. Drain until we see it.
            String body = await().atMost(Duration.ofSeconds(15)).until(() -> {
                Message m = rabbitTemplate.receive(queue.getName(), 500);
                return m == null ? null : new String(m.getBody(), StandardCharsets.UTF_8);
            }, b -> b != null && b.contains(String.valueOf(marker)));

            assertThat(body).contains("\"type\":\"seat.held\"");
            assertThat(body).contains("\"aggregateType\":\"seat\"");
            assertThat(body).contains("\"eventId\":");
            assertThat(body).contains("\"payload\":");
            assertThat(body).contains("Z9");
        } finally {
            amqpAdmin.deleteQueue(queue.getName());
        }
    }

    @Test
    void published_rows_are_drained_from_pending() {
        publisher.publish(EventTypes.SEAT_HELD, "seat", "p-" + System.nanoTime(),
                new SeatHeldPayload(1L, "A1", "u", Instant.now()));
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(outboxRepository.countPending()).isZero());
    }
}
