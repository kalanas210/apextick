package com.apextick.booking.ticket.dto;

public record VerifyResponse(boolean ok, TicketResponse ticket, String reason) {
}
