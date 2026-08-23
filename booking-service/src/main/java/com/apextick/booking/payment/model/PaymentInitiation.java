package com.apextick.booking.payment.model;

import com.apextick.booking.payment.PaymentOutcome;

public record PaymentInitiation(
        PaymentOutcome outcome, String providerRef, String clientSecret,
        String cardBrand, String cardLast4, String failureCode, String failureMessage, String rawJson) {

    public static PaymentInitiation succeeded(String providerRef, String brand, String last4) {
        return new PaymentInitiation(PaymentOutcome.SUCCEEDED, providerRef, null, brand, last4, null, null, null);
    }

    /** Gateway created a charge that the client must finish (Stripe 3-D Secure / off-session confirm). */
    public static PaymentInitiation requiresAction(String providerRef, String clientSecret) {
        return new PaymentInitiation(PaymentOutcome.REQUIRES_ACTION, providerRef, clientSecret,
                null, null, null, null, null);
    }

    public static PaymentInitiation declined(String brand, String last4, String code, String message) {
        return new PaymentInitiation(PaymentOutcome.DECLINED, null, null, brand, last4, code, message, null);
    }

    /** A gateway/transport error (not a card decline); recorded as FAILED so the order stays payable. */
    public static PaymentInitiation failed(String code, String message) {
        return new PaymentInitiation(PaymentOutcome.FAILED, null, null, null, null, code, message, null);
    }
}
