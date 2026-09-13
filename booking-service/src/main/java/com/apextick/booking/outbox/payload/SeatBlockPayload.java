package com.apextick.booking.outbox.payload;

/** Payload for {@code seat.blocked} and {@code seat.unblocked}: a seat an admin took off sale, or put back. */
public record SeatBlockPayload(Long seatId, Long eventId, String adminSub) {
}
