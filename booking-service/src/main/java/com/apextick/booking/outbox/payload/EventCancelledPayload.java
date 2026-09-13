package com.apextick.booking.outbox.payload;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload for {@code event.cancelled}, one per order the cancellation touched, so each buyer is
 * told once about their own order: {@code refundDue} when it had been paid for and its money is
 * coming back, not when it was cancelled before anyone paid.
 */
public record EventCancelledPayload(
        Long eventId, String eventName, Instant startsAt, String timeZone, String venue, String reason,
        String orderId, String orderNumber, String userEmail, String userName,
        BigDecimal total, String currency, boolean refundDue) {
}
