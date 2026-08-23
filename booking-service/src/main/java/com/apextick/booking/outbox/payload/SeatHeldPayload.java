package com.apextick.booking.outbox.payload;

import java.time.Instant;

/** Payload for {@code seat.held}. Enriched with event/section context in later steps. */
public record SeatHeldPayload(
        Long seatId, String seatNumber, String heldBy, Instant heldUntil) {
}
