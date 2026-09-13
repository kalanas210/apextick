package com.apextick.booking.outbox.payload;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload for {@code payment.failed}: a charge the provider reported failed while its order could
 * still be paid, so its buyer, who may have left the checkout page, can be sent back to it.
 */
public record PaymentFailedPayload(
        String paymentId, String orderId, String orderNumber, String userEmail, String userName, String eventName,
        BigDecimal total, String currency, String failureCode, Instant expiresAt) {
}
