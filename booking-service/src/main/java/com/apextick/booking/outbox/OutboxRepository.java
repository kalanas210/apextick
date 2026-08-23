package com.apextick.booking.outbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Direct-JDBC access to the transactional outbox (no JPA entity; polled with SKIP LOCKED). */
@Repository
public class OutboxRepository {

    private final JdbcClient jdbc;

    public OutboxRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void append(UUID id, String aggregateType, String aggregateId, String type,
                       String payloadJson, String correlationId, Instant occurredAt) {
        jdbc.sql("""
                INSERT INTO outbox_events (id, aggregate_type, aggregate_id, type, payload, correlation_id, occurred_at)
                VALUES (:id, :at, :aid, :type, CAST(:payload AS jsonb), :cid, :occ)
                """)
                .param("id", id)
                .param("at", aggregateType)
                .param("aid", aggregateId)
                .param("type", type)
                .param("payload", payloadJson)
                .param("cid", correlationId)
                .param("occ", occurredAt.atOffset(ZoneOffset.UTC))
                .update();
    }

    public List<OutboxRow> claimBatch(int limit) {
        return jdbc.sql("""
                SELECT id, aggregate_type, aggregate_id, type, payload::text AS payload,
                       correlation_id, occurred_at, attempts
                  FROM outbox_events
                 WHERE published_at IS NULL AND dead_at IS NULL AND next_attempt_at <= now()
                 ORDER BY occurred_at
                 FOR UPDATE SKIP LOCKED
                 LIMIT :limit
                """)
                .param("limit", limit)
                .query((rs, n) -> new OutboxRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("aggregate_type"),
                        rs.getString("aggregate_id"),
                        rs.getString("type"),
                        rs.getString("payload"),
                        rs.getString("correlation_id"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        rs.getInt("attempts")))
                .list();
    }

    public void markPublished(UUID id) {
        jdbc.sql("UPDATE outbox_events SET published_at = now() WHERE id = :id").param("id", id).update();
    }

    public void markFailed(UUID id, String error, int attempts, Instant nextAttemptAt) {
        jdbc.sql("""
                UPDATE outbox_events
                   SET attempts = :attempts, last_error = :err, next_attempt_at = :next
                 WHERE id = :id
                """)
                .param("attempts", attempts)
                .param("err", error)
                .param("next", nextAttemptAt.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    public void markDead(UUID id, String error, int attempts) {
        jdbc.sql("UPDATE outbox_events SET attempts = :attempts, last_error = :err, dead_at = now() WHERE id = :id")
                .param("attempts", attempts).param("err", error).param("id", id).update();
    }

    public long countPending() {
        return jdbc.sql("SELECT count(*) FROM outbox_events WHERE published_at IS NULL AND dead_at IS NULL")
                .query(Long.class).single();
    }
}
