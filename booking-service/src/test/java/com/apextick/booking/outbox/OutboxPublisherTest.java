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
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@IntegrationTest
class OutboxPublisherTest {

    @Autowired DomainEventPublisher publisher;
    @Autowired OutboxRepository outboxRepository;
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired AmqpAdmin amqpAdmin;
    @Autowired JdbcClient jdbc;

    @Test
    void writes_to_outbox_then_relays_an_envelope_to_rabbit() {
        // this service declares no queues of its own (consumers own theirs), so bind a
        // throwaway one to seat.held. Not exclusive: that ties the queue to the connection
        // that declared it, and the cached connection the scheduled publisher shares can be
        // replaced mid-test, taking the queue with it (404 NOT_FOUND on receive). Durable,
        // because RabbitMQ 4 refuses a transient non-exclusive queue by closing the whole
        // connection (541, transient_nonexcl_queues). x-expires cleans up after a test that
        // dies before its finally block.
        // (Not AnonymousQueue: it sets x-queue-master-locator, which RabbitMQ 4 rejects.)
        Queue queue = QueueBuilder.durable("outbox-test-" + UUID.randomUUID()).expires(60_000).build();
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

    /**
     * The broker acks a message no queue takes, so an ack alone used to mark an event published that nobody could
     * ever read: a booking confirmation relayed before notification-service had declared its queues on a new
     * broker was simply gone. An event a queue must take now waits for one; any other event still goes out.
     */
    @Test
    void an_event_a_queue_must_take_stays_pending_while_no_queue_takes_it() {
        // test.must-route is in the test profile's app.outbox.require-route, and no queue is bound to it
        String required = publishUnbound("test.must-route");
        String optional = publishUnbound("test.nobody-listens");
        try {
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertThat(lastError(required)).startsWith("unroutable");
                assertThat(isPublished(optional)).isTrue();
            });
            assertThat(isPublished(required)).isFalse();
        } finally {
            jdbc.sql("DELETE FROM outbox_events WHERE aggregate_id IN (:ids)")
                    .param("ids", List.of(required, optional)).update();
        }
    }

    private String publishUnbound(String type) {
        String aggregateId = type + "-" + UUID.randomUUID();
        publisher.publish(type, "test", aggregateId, Map.of("marker", aggregateId));
        return aggregateId;
    }

    private String lastError(String aggregateId) {
        return jdbc.sql("SELECT coalesce(last_error, '') FROM outbox_events WHERE aggregate_id = :id")
                .param("id", aggregateId).query(String.class).single();
    }

    private boolean isPublished(String aggregateId) {
        return jdbc.sql("SELECT published_at IS NOT NULL FROM outbox_events WHERE aggregate_id = :id")
                .param("id", aggregateId).query(Boolean.class).single();
    }
}
