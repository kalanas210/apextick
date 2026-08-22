package com.apextick.notification.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /** Atomically claims an event id; returns 1 if newly claimed, 0 if already processed. */
    @Modifying
    @Query(value = "INSERT INTO processed_events (event_id, type, processed_at) "
            + "VALUES (:id, :type, now()) ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("type") String type);
}
