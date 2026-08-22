package com.apextick.notification.messaging.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingConfirmedPayload(
        String orderId, String orderNumber, String userSub, String userEmail, String userName,
        EventInfo event, List<Item> items, BigDecimal subtotal, BigDecimal fee, BigDecimal total,
        String currency, PaymentInfo payment, Instant paidAt) {

    public record EventInfo(Long id, String slug, String name, Instant startsAt, String timeZone,
                            String venue, String city, String country) {
    }

    public record Item(Long orderItemId, String ticketId, Long seatId, String label, String sectionName,
                       String tierName, BigDecimal price, String qrToken, String pdfS3Key) {
    }

    public record PaymentInfo(String paymentId, String provider, String providerRef,
                              String cardBrand, String cardLast4) {
    }
}
