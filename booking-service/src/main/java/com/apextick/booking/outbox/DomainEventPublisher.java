package com.apextick.booking.outbox;

import com.apextick.booking.security.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes a domain event to the transactional outbox within the caller's transaction.
 * The {@link OutboxPublisher} relays committed rows to RabbitMQ after commit.
 */
@Component
public class DomainEventPublisher {

    private final OutboxRepository outbox;
    private final ObjectMapper mapper;

    public DomainEventPublisher(OutboxRepository outbox, ObjectMapper mapper) {
        this.outbox = outbox;
        this.mapper = mapper;
    }

    public void publish(String type, String aggregateType, String aggregateId, Object payload) {
        String json = mapper.writeValueAsString(payload);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        outbox.append(UUID.randomUUID(), aggregateType, aggregateId, type, json, correlationId, Instant.now());
    }
}
