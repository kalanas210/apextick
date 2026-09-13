package com.apextick.booking.ticket.gate;

import com.apextick.booking.ticket.dto.TicketResponse;

/** An admission: the ticket that was let in, the event it was let in to, and how full that event now is. */
public record ScanResponse(TicketResponse ticket, GateEventResponse event, AdmissionsResponse admissions) {
}
