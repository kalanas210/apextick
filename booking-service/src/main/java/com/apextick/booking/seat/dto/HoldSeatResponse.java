package com.apextick.booking.seat.dto;

import com.apextick.booking.seat.Seat;
import java.time.Instant;

public record HoldSeatResponse(Long seatId, String status, Instant heldUntil) {

    public static HoldSeatResponse from(Seat seat) {
        return new HoldSeatResponse(
                seat.getId(),
                seat.getStatus().name(),
                seat.getHeldUntil());
    }
}