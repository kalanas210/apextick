package com.apextick.booking.ticket.gate;

/**
 * How full the ground is: tickets admitted so far across every gate, out of the tickets that can
 * still admit anyone (issued or already used; cancelled ones do not count).
 */
public record AdmissionsResponse(Long eventId, long admitted, long issued) {
}
