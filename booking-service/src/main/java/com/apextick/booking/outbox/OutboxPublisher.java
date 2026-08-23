package com.apextick.booking.outbox;

import com.apextick.booking.config.AppProperties;
import com.apextick.booking.messaging.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Polls the outbox and relays events to RabbitMQ with publisher confirms + backoff. */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper mapper;
    private final AppProperties props;

    public OutboxPublisher(OutboxRepository outbox, RabbitTemplate rabbitTemplate,
                           ObjectMapper mapper, AppProperties props) {
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = mapper;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval}")
    @Transactional
    public void publishBatch() {
        List<OutboxRow> batch = outbox.claimBatch(props.outbox().batchSize());
        for (OutboxRow row : batch) {
            int attempts = row.attempts() + 1;
            try {
                Message msg = MessageBuilder.withBody(buildEnvelope(row).getBytes(StandardCharsets.UTF_8))
                        .setContentType("application/json")
                        .setMessageId(row.id().toString())
                        .setType(row.type())
                        .setHeader("x-event-type", row.type())
                        .setHeader("x-correlation-id", row.correlationId())
                        .build();
                CorrelationData cd = new CorrelationData(row.id().toString());
                rabbitTemplate.send(RabbitConfig.EXCHANGE, row.type(), msg, cd);
                CorrelationData.Confirm confirm =
                        cd.getFuture().get(props.outbox().confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
                if (confirm != null && confirm.isAck()) {
                    outbox.markPublished(row.id());
                } else {
                    fail(row, attempts, "broker nack");
                }
            } catch (Exception e) {
                fail(row, attempts, e.getMessage());
            }
        }
    }

    private void fail(OutboxRow row, int attempts, String error) {
        if (attempts >= props.outbox().maxAttempts()) {
            log.error("Outbox event {} ({}) dead after {} attempts: {}", row.id(), row.type(), attempts, error);
            outbox.markDead(row.id(), error, attempts);
        } else {
            long backoffSeconds = Math.min(1L << Math.min(attempts, 6), 60);
            outbox.markFailed(row.id(), error, attempts, Instant.now().plusSeconds(backoffSeconds));
        }
    }

    private String buildEnvelope(OutboxRow row) {
        ObjectNode env = mapper.createObjectNode();
        env.put("eventId", row.id().toString());
        env.put("type", row.type());
        env.put("version", 1);
        env.put("occurredAt", row.occurredAt().toString());
        if (row.correlationId() != null) {
            env.put("correlationId", row.correlationId());
        }
        env.put("aggregateType", row.aggregateType());
        env.put("aggregateId", row.aggregateId());
        env.set("payload", mapper.readTree(row.payload()));
        return mapper.writeValueAsString(env);
    }
}
