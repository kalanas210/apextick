package com.apextick.notification.messaging.payload;

public record SeatEventPayload(Long seatId, String seatNumber, Long eventId, String reason) {
}
