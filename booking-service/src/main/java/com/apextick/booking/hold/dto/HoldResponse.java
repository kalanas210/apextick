package com.apextick.booking.hold.dto;

import com.apextick.booking.seat.dto.SeatResponse;

import java.time.Instant;
import java.util.List;

public record HoldResponse(
        Long eventId, List<Long> seatIds, Instant heldUntil, long holdSeconds, List<SeatResponse> seats) {
}
