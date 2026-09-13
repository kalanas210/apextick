package com.apextick.booking.outbox.payload;

import java.time.Instant;

/** Payload for {@code ticket.used}: a ticket admitted at a gate. */
public record TicketUsedPayload(String ticketId, String orderId, Long eventId, Long seatId, String gate,
                                Instant usedAt) {
}
