package com.apextick.notification.messaging.payload;

import java.math.BigDecimal;
import java.time.Instant;

/** A charge that failed while its order could still be paid: its buyer is sent back to the checkout. */
public record PaymentFailedPayload(
        String paymentId, String orderId, String orderNumber, String userEmail, String userName, String eventName,
        BigDecimal total, String currency, String failureCode, Instant expiresAt) {
}
