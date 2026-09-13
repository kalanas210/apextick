package com.apextick.notification.messaging.payload;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One order an event cancellation touched. {@code refundDue} when the order had been paid for and
 * its money is coming back; otherwise it was cancelled before anyone was charged.
 */
public record EventCancelledPayload(
        Long eventId, String eventName, Instant startsAt, String timeZone, String venue, String reason,
        String orderId, String orderNumber, String userEmail, String userName,
        BigDecimal total, String currency, boolean refundDue) {
}
