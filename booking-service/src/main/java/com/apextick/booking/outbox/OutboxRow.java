package com.apextick.booking.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxRow(
        UUID id, String aggregateType, String aggregateId, String type,
        String payload, String correlationId, Instant occurredAt, int attempts) {
}
