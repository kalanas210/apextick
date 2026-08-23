package com.apextick.booking.payment.model;

public record RefundResult(boolean accepted, String providerRef, String rawJson) {
}
