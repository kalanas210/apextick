package com.apextick.booking.payment.model;

import com.apextick.booking.payment.PaymentOutcome;

public record PaymentInitiation(
        PaymentOutcome outcome, String providerRef, String clientSecret, CheckoutForm checkout,
        String cardBrand, String cardLast4, String failureCode, String failureMessage, String rawJson) {

    public static PaymentInitiation succeeded(String providerRef, String brand, String last4) {
        return new PaymentInitiation(PaymentOutcome.SUCCEEDED, providerRef, null, null, brand, last4, null, null, null);
    }

    public static PaymentInitiation declined(String brand, String last4, String code, String message) {
        return new PaymentInitiation(PaymentOutcome.DECLINED, null, null, null, brand, last4, code, message, null);
    }
}
