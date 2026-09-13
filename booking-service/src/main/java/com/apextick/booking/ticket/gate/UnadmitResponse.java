package com.apextick.booking.ticket.gate;

import com.apextick.booking.ticket.dto.TicketResponse;

/** An admission undone: the ticket, able to admit again, and the event's count without it. */
public record UnadmitResponse(TicketResponse ticket, AdmissionsResponse admissions) {
}
