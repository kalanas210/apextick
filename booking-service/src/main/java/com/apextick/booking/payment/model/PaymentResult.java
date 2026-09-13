package com.apextick.booking.payment.model;

import com.apextick.booking.payment.PaymentOutcome;

import java.math.BigDecimal;

/**
 * Normalised result of a provider callback (webhook/notify). {@code paymentId} is the ApexTick
 * payment id the charge was stamped with when it was created, when the provider sends it back.
 */
public record PaymentResult(
        String externalEventId, String providerRef, String paymentId, PaymentOutcome outcome, BigDecimal amount,
        String currency, String cardBrand, String cardLast4, String failureCode, String rawJson) {
}
