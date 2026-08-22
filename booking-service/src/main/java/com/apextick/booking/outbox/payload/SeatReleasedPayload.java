package com.apextick.booking.outbox.payload;

/** Payload for {@code seat.released}. Reason: EXPIRED | USER_RELEASED | ORDER_CANCELLED | ADMIN. */
public record SeatReleasedPayload(Long seatId, Long eventId, String reason) {
}
