package com.apextick.booking.seat.dto;

import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatStatus;

import java.time.Instant;

/** Admin view of a seat, including the holder (used by load-test invariant checks). */
public record AdminSeatResponse(
        Long id, String label, Long sectionId, SeatStatus status,
        String heldBy, Instant heldUntil, Long version) {

    public static AdminSeatResponse from(Seat s) {
        return new AdminSeatResponse(s.getId(), s.getSeatNumber(),
                s.getSection() == null ? null : s.getSection().getId(),
                s.getStatus(), s.getHeldBy(), s.getHeldUntil(), s.getVersion());
    }
}
