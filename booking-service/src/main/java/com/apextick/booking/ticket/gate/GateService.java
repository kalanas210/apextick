package com.apextick.booking.ticket.gate;

import com.apextick.booking.catalog.Event;
import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.catalog.EventStatus;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.ticket.Ticket;
import com.apextick.booking.ticket.TicketRepository;
import com.apextick.booking.ticket.TicketStatus;
import com.apextick.booking.ticket.dto.TicketResponse;
import com.apextick.booking.web.ConflictException;
import com.apextick.booking.web.ErrorCodes;
import com.apextick.booking.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** The doors: a steward scans a ticket against the event their gate is admitting. */
@Service
public class GateService {

    private final TicketRepository tickets;
    private final EventRepository events;

    public GateService(TicketRepository tickets, EventRepository events) {
        this.tickets = tickets;
        this.events = events;
    }

    @Transactional
    public ScanResponse scan(String qrToken, Long eventId, CurrentUser steward) {
        Event event = events.findById(eventId).orElseThrow(() -> new NotFoundException("Event", eventId));
        if (!event.getStatus().isAdmitting()) {
            throw new ConflictException(ErrorCodes.EVENT_NOT_ADMITTING,
                    event.getStatus() == EventStatus.CANCELLED
                            ? "This event has been cancelled" : "This event is not published",
                    Map.of("eventStatus", event.getStatus().code()));
        }
        Ticket ticket = tickets.findByQrToken(qrToken)
                .orElseThrow(() -> new NotFoundException("Ticket for QR token not found"));
        // A ticket opens the gates of its own event only. Checked before anything is spent, so a
        // holder who turned up at the wrong match leaves with a ticket that still works at theirs.
        if (!ticket.getEventId().equals(eventId)) {
            throw wrongEvent(ticket);
        }
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw cancelled();
        }
        // Admission is the conditional UPDATE itself, not a read followed by a write: two
        // turnstiles scanning the same QR code at the same moment both read ISSUED, and a
        // read-then-write lets both of them in. Only one UPDATE can move the row off ISSUED.
        if (tickets.admit(ticket.getId(), Instant.now(), steward.sub()) == 0) {
            Ticket current = tickets.findById(ticket.getId()).orElseThrow();
            if (current.getStatus() == TicketStatus.CANCELLED) {
                throw cancelled();
            }
            // usedAt rides along as a problem-detail member so the gate screen can say
            // "scanned 4 minutes ago" -- the question actually being asked at a turnstile.
            throw new ConflictException(ErrorCodes.TICKET_ALREADY_USED,
                    "Ticket was already used at " + current.getUsedAt(),
                    current.getUsedAt() == null ? Map.of() : Map.of("usedAt", current.getUsedAt().toString()));
        }
        Ticket admitted = tickets.findById(ticket.getId()).orElseThrow();
        return new ScanResponse(TicketResponse.from(admitted), GateEventResponse.from(event));
    }

    /** Names the event the ticket really is for, so the steward can send its holder there. */
    private ConflictException wrongEvent(Ticket ticket) {
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("ticketEventId", ticket.getEventId());
        events.findById(ticket.getEventId()).ifPresent(e -> {
            actual.put("ticketEventName", e.getName());
            actual.put("ticketEventStartsAt", e.getStartsAt().toString());
            actual.put("ticketEventTimeZone", e.getTimeZone());
            if (e.getVenue() != null) {
                actual.put("ticketEventVenue", e.getVenue());
            }
        });
        return new ConflictException(ErrorCodes.TICKET_WRONG_EVENT, "This ticket is for a different event", actual);
    }

    private static ConflictException cancelled() {
        return new ConflictException(ErrorCodes.TICKET_CANCELLED, "Ticket has been cancelled");
    }
}
