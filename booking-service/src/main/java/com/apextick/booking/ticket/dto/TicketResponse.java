package com.apextick.booking.ticket.dto;

import com.apextick.booking.ticket.Ticket;

import java.time.Instant;

public record TicketResponse(
        String id, String orderId, String status, String qrToken, Instant issuedAt, Instant usedAt,
        String pdfUrl, Long seatId, String seatLabel, String sectionName, String tierName) {

    public static TicketResponse from(Ticket t) {
        return new TicketResponse(
                t.getId().toString(), t.getOrder().getId().toString(), t.getStatus().name(),
                t.getQrToken(), t.getIssuedAt(), t.getUsedAt(),
                "/api/tickets/" + t.getId() + "/pdf",
                t.getSeatId(), t.getOrderItem().getSeatLabel(),
                t.getOrderItem().getSectionName(), t.getOrderItem().getTierName());
    }
}
