package com.apextick.booking.admin.dto;

import com.apextick.booking.payment.Payment;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A payment attempt, for the box office: the provider's reference to find it by in the provider's
 * own dashboard, and where its refund stands. No client secret: nothing here needs one.
 */
public record AdminPaymentResponse(
        String id, String provider, String status, BigDecimal amount, String currency, String providerRef,
        String cardBrand, String cardLast4, String failureCode, String failureMessage,
        Instant createdAt, Instant confirmedAt,
        BigDecimal refundAmount, String refundCurrency, String refundRef, Instant refundedAt,
        int refundAttempts, Instant refundLastAttemptAt, String refundError) {

    public static AdminPaymentResponse from(Payment p) {
        return new AdminPaymentResponse(p.getId().toString(), p.getProvider().name(), p.getStatus().name(),
                p.getAmount(), p.getCurrency(), p.getProviderRef(), p.getCardBrand(), p.getCardLast4(),
                p.getFailureCode(), p.getFailureMessage(), p.getCreatedAt(), p.getConfirmedAt(),
                p.getRefundAmount(), p.getRefundCurrency(), p.getRefundRef(), p.getRefundedAt(),
                p.getRefundAttempts(), p.getRefundLastAttemptAt(), p.getRefundError());
    }
}
