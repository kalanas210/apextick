package com.apextick.booking.payment.model;

/** Raw card details for the mock gateway only. Never persisted or logged (brand + last4 only). */
public record PaymentCard(String number, Integer expMonth, Integer expYear, String cvc, String holder) {
}
