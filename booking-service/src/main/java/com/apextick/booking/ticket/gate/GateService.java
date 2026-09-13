package com.apextick.booking.ticket.gate;

import com.apextick.booking.catalog.Event;
import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.catalog.EventStatus;
import com.apextick.booking.outbox.DomainEventPublisher;
import com.apextick.booking.outbox.EventTypes;
import com.apextick.booking.outbox.payload.TicketUnadmittedPayload;
import com.apextick.booking.outbox.payload.TicketUsedPayload;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.ticket.Ticket;
import com.apextick.booking.ticket.TicketRepository;
import com.apextick.booking.ticket.TicketStatus;
import com.apextick.booking.ticket.dto.TicketResponse;
import com.apextick.booking.web.ConflictException;
import com.apextick.booking.web.ErrorCodes;
import com.apextick.booking.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** The doors: a steward scans a ticket against the event their gate is admitting. */
@Service
public class GateService {

    private final TicketRepository tickets;
    private final EventRepository events;
    private final TicketScanRepository scans;
    private final DomainEventPublisher domainEvents;
    private final TransactionTemplate tx;

    public GateService(TicketRepository tickets, EventRepository events, TicketScanRepository scans,
                       DomainEventPublisher domainEvents, PlatformTransactionManager txManager) {
        this.tickets = tickets;
        this.events = events;
        this.scans = scans;
        this.domainEvents = domainEvents;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Scans a ticket at a gate. A refusal is a scan worth keeping too -- one QR code turned away at
     * three turnstiles in a minute is exactly what a supervisor wants to see -- so the decision and
     * its record commit together, and a refusal is only thrown once they have.
     */
    public ScanResponse scan(String qrToken, Long eventId, String gate, CurrentUser steward) {
        Verdict verdict = tx.execute(s -> decide(qrToken, eventId, gateName(gate), steward));
        if (verdict.refusal() != null) {
            throw verdict.refusal();
        }
        return verdict.admission();
    }

    @Transactional(readOnly = true)
    public AdmissionsResponse admissions(Long eventId) {
        if (!events.existsById(eventId)) {
            throw new NotFoundException("Event", eventId);
        }
        return countAdmissions(eventId);
    }

    /**
     * Undoes an admission: a steward scanned the wrong phone, or let someone in who was turned
     * straight back. The ticket admits again, and the record keeps the admission as well as who
     * undid it and why.
     */
    @Transactional
    public UnadmitResponse unadmit(UUID ticketId, String reason, CurrentUser admin) {
        Ticket ticket = tickets.findById(ticketId).orElseThrow(() -> new NotFoundException("Ticket", ticketId));
        Long eventId = ticket.getEventId();
        if (tickets.unadmit(ticketId) == 0) {
            String status = tickets.findById(ticketId).map(t -> t.getStatus().name()).orElse("UNKNOWN");
            throw new ConflictException(ErrorCodes.TICKET_NOT_ADMITTED, "Ticket has not been admitted",
                    Map.of("ticketStatus", status));
        }
        Instant now = Instant.now();
        String why = reason.strip();
        record(ticketId, eventId, ScanOutcome.UNADMITTED, null, why, admin);
        domainEvents.publish(EventTypes.TICKET_UNADMITTED, "ticket", ticketId.toString(),
                new TicketUnadmittedPayload(ticketId.toString(), eventId, why, now));
        Ticket restored = tickets.findById(ticketId).orElseThrow();
        return new UnadmitResponse(TicketResponse.from(restored), countAdmissions(eventId));
    }

    /** An admission, or the refusal to throw once the scan's record has committed. */
    private record Verdict(ScanResponse admission, ConflictException refusal) {
        static Verdict admitted(ScanResponse admission) {
            return new Verdict(admission, null);
        }

        static Verdict refused(ConflictException refusal) {
            return new Verdict(null, refusal);
        }
    }

    private Verdict decide(String qrToken, Long eventId, String gate, CurrentUser steward) {
        Event event = events.findById(eventId).orElseThrow(() -> new NotFoundException("Event", eventId));
        if (!event.getStatus().isAdmitting()) {
            // no ticket has been looked at yet, so there is no scan to record
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
            record(ticket.getId(), eventId, ScanOutcome.WRONG_EVENT, gate, null, steward);
            return Verdict.refused(wrongEvent(ticket));
        }
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            record(ticket.getId(), eventId, ScanOutcome.TICKET_CANCELLED, gate, null, steward);
            return Verdict.refused(cancelled());
        }
        Instant now = Instant.now();
        // Admission is the conditional UPDATE itself, not a read followed by a write: two
        // turnstiles scanning the same QR code at the same moment both read ISSUED, and a
        // read-then-write lets both of them in. Only one UPDATE can move the row off ISSUED.
        if (tickets.admit(ticket.getId(), now, steward.sub(), gate) == 0) {
            Ticket current = tickets.findById(ticket.getId()).orElseThrow();
            if (current.getStatus() == TicketStatus.CANCELLED) {
                record(current.getId(), eventId, ScanOutcome.TICKET_CANCELLED, gate, null, steward);
                return Verdict.refused(cancelled());
            }
            record(current.getId(), eventId, ScanOutcome.ALREADY_USED, gate, null, steward);
            return Verdict.refused(alreadyUsed(current));
        }
        Ticket admitted = tickets.findById(ticket.getId()).orElseThrow();
        record(admitted.getId(), eventId, ScanOutcome.ADMITTED, gate, null, steward);
        domainEvents.publish(EventTypes.TICKET_USED, "ticket", admitted.getId().toString(),
                new TicketUsedPayload(admitted.getId().toString(), admitted.getOrder().getId().toString(),
                        admitted.getEventId(), admitted.getSeatId(), gate, now));
        return Verdict.admitted(new ScanResponse(TicketResponse.from(admitted), GateEventResponse.from(event),
                countAdmissions(eventId)));
    }

    private void record(UUID ticketId, Long eventId, ScanOutcome outcome, String gate, String reason,
                        CurrentUser actor) {
        TicketScan scan = new TicketScan();
        scan.setTicketId(ticketId);
        scan.setEventId(eventId);
        scan.setOutcome(outcome);
        scan.setGate(gate);
        scan.setReason(reason);
        scan.setActorSub(actor.sub());
        scan.setScannedAt(Instant.now());
        scans.save(scan);
    }

    private AdmissionsResponse countAdmissions(Long eventId) {
        long admitted = tickets.countByEventIdAndStatus(eventId, TicketStatus.USED);
        long issued = admitted + tickets.countByEventIdAndStatus(eventId, TicketStatus.ISSUED);
        return new AdmissionsResponse(eventId, admitted, issued);
    }

    private static String gateName(String gate) {
        return gate == null || gate.isBlank() ? null : gate.strip();
    }

    /**
     * When and where the ticket was first let in, so the gate screen can say "scanned 4 minutes
     * ago at North 3" -- the question actually being asked at a turnstile -- and which ticket it
     * was, for an admin who has to undo a mistaken admission.
     */
    private static ConflictException alreadyUsed(Ticket ticket) {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("ticketId", ticket.getId().toString());
        if (ticket.getUsedAt() != null) {
            first.put("usedAt", ticket.getUsedAt().toString());
        }
        if (ticket.getUsedGate() != null) {
            first.put("usedGate", ticket.getUsedGate());
        }
        return new ConflictException(ErrorCodes.TICKET_ALREADY_USED,
                "Ticket was already used at " + ticket.getUsedAt(), first);
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
