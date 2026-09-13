package com.apextick.booking.admin.dto;

import com.apextick.booking.ticket.Ticket;

import java.time.Instant;
import java.util.UUID;

/**
 * A ticket, for the box office: where it stands and, once used, when and at which gate. Not its
 * QR token, which would let anyone reading the console over a shoulder walk in on it.
 */
public record AdminTicketResponse(
        UUID id, String status, Long seatId, String seatLabel, String sectionName, String tierName,
        Instant issuedAt, Instant usedAt, String usedGate) {

    public static AdminTicketResponse from(Ticket t) {
        return new AdminTicketResponse(t.getId(), t.getStatus().name(), t.getSeatId(),
                t.getOrderItem().getSeatLabel(), t.getOrderItem().getSectionName(), t.getOrderItem().getTierName(),
                t.getIssuedAt(), t.getUsedAt(), t.getUsedGate());
    }
}
