package com.apextick.booking.ticket;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Everything the renderer needs, flattened off the JPA graph so PDF rendering can
 * happen outside a transaction (and be unit-tested without a database).
 */
public record TicketPdfData(
        String ticketId, String qrToken, String orderNumber, String holderName,
        String eventName, String venue, String city, String country,
        Instant startsAt, String timeZone,
        String seatLabel, String sectionName, String tierName,
        BigDecimal price, String currency) {
}
