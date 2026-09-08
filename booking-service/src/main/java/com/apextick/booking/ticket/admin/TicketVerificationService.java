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
        if (ticket.getStatus() == TicketStatus.USED) {
            // usedAt rides along as a problem-detail member so the gate screen can say
            // "scanned 4 minutes ago" -- the question actually being asked at a turnstile.
            throw new ConflictException(ErrorCodes.TICKET_ALREADY_USED,
                    "Ticket was already used at " + ticket.getUsedAt(),
                    ticket.getUsedAt() == null ? Map.of() : Map.of("usedAt", ticket.getUsedAt().toString()));
        }
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new ConflictException("TICKET_CANCELLED", "Ticket has been cancelled");
        }
        ticket.setStatus(TicketStatus.USED);
        ticket.setUsedAt(Instant.now());
        ticket.setUsedBy(admin.sub());
        return new VerifyResponse(true, TicketResponse.from(ticket), null);
    }
}
