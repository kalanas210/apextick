package com.apextick.booking.payment.dto;

import com.apextick.booking.order.dto.OrderResponse;

public record PaymentResponse(
        String paymentId, String orderId, String provider, String status,
        String cardBrand, String cardLast4, String failureCode, String failureMessage,
        String clientSecret, String publishableKey, OrderResponse order) {
}
