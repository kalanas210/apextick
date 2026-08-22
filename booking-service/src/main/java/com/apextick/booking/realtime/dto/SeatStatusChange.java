package com.apextick.booking.realtime.dto;

import java.time.Instant;

public record SeatStatusChange(Long seatId, String status, Instant heldUntil) {
}
