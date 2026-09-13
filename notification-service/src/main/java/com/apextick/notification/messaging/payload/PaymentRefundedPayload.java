package com.apextick.notification.messaging.payload;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A refund the payment provider accepted, so the money is on its way back. {@code reason} is
 * booking-service's own code for why: {@code box_office_refund}, {@code seats_lost},
 * {@code order_closed}, {@code duplicate_charge} or {@code amount_mismatch}.
 */
public record PaymentRefundedPayload(
        String paymentId, String orderId, String orderNumber, String userSub, String userEmail, String userName,
        Long eventId, String eventName, BigDecimal amount, String currency, String reason, String refundRef,
        Instant refundedAt) {
}
