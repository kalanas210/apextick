package com.apextick.booking.realtime.dto;

import java.time.Instant;
import java.util.List;

/** Broadcast to /topic/events/{eventId}/seats when seats change availability. */
public record SeatStatusMessage(String type, Long eventId, Instant occurredAt, List<SeatStatusChange> seats) {
    public static SeatStatusMessage of(Long eventId, List<SeatStatusChange> seats) {
        return new SeatStatusMessage("SEATS_CHANGED", eventId, Instant.now(), seats);
    }
}
