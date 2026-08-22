package com.apextick.booking.payment.model;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentContext(
        UUID paymentId, UUID orderId, String orderNumber, BigDecimal amount, String currency,
        Customer customer, String idempotencyKey, String returnUrl, String cancelUrl, PaymentCard card) {
}
