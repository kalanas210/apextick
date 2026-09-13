package com.apextick.booking.ticket.admin;

import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.ticket.Ticket;
import com.apextick.booking.ticket.TicketRepository;
import com.apextick.booking.ticket.TicketStatus;
import com.apextick.booking.ticket.dto.TicketResponse;
import com.apextick.booking.ticket.dto.VerifyResponse;
import com.apextick.booking.web.ConflictException;
import com.apextick.booking.web.ErrorCodes;
import com.apextick.booking.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class TicketVerificationService {

    private final TicketRepository tickets;

    public TicketVerificationService(TicketRepository tickets) {
        this.tickets = tickets;
    }

    @Transactional
    public VerifyResponse verify(String qrToken, CurrentUser admin) {
        Ticket ticket = tickets.findByQrToken(qrToken)
                .orElseThrow(() -> new NotFoundException("Ticket for QR token not found"));
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw cancelled();
        }
        // Admission is the conditional UPDATE itself, not a read followed by a write: two
        // turnstiles scanning the same QR code at the same moment both read ISSUED, and a
        // read-then-write lets both of them in. Only one UPDATE can move the row off ISSUED.
        if (tickets.admit(ticket.getId(), Instant.now(), admin.sub()) == 0) {
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
        return new VerifyResponse(true, TicketResponse.from(admitted), null);
    }

    private static ConflictException cancelled() {
        return new ConflictException("TICKET_CANCELLED", "Ticket has been cancelled");
    }
}
