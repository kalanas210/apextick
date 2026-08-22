package com.apextick.booking.payment.model;

import com.apextick.booking.payment.PaymentOutcome;

import java.math.BigDecimal;

/** Normalised result of a provider callback (webhook/notify). */
public record PaymentResult(
        String externalEventId, String providerRef, PaymentOutcome outcome, BigDecimal amount, String currency,
        String cardBrand, String cardLast4, String failureCode, String rawJson) {
}
