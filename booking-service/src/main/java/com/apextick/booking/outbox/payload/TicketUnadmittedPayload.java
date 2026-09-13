package com.apextick.booking.outbox.payload;

import java.time.Instant;

/** Payload for {@code ticket.unadmitted}: an admission an admin reversed, and why. */
public record TicketUnadmittedPayload(String ticketId, Long eventId, String reason, Instant unadmittedAt) {
}
