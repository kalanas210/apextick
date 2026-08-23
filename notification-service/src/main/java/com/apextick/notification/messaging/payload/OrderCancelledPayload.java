package com.apextick.notification.messaging.payload;

import java.util.List;

public record OrderCancelledPayload(
        String orderId, String orderNumber, String userSub, String userEmail,
        Long eventId, List<Long> seatIds, String reason) {
}
