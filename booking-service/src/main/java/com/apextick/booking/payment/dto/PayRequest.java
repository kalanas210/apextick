package com.apextick.booking.payment.dto;

import com.apextick.booking.payment.model.PaymentCard;

public record PayRequest(PaymentCard card, String paymentMethodId, String returnUrl, String cancelUrl) {
}
