package com.apextick.booking.ticket.gate;

import com.apextick.booking.ticket.dto.TicketResponse;

/** An admission: the ticket that was let in, and the event it was let in to. */
public record ScanResponse(TicketResponse ticket, GateEventResponse event) {
}
