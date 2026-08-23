package com.apextick.booking.seat;

import java.util.List;

/** Thrown when one or more requested seats are not AVAILABLE; mapped to HTTP 409. */
public class SeatUnavailableException extends RuntimeException {

    private final transient List<Long> seatIds;

    public SeatUnavailableException(Long seatId) {
        super("Seat " + seatId + " is no longer available");
        this.seatIds = List.of(seatId);
    }

    public SeatUnavailableException(List<Long> seatIds) {
        super(seatIds.size() + " seat(s) are no longer available");
        this.seatIds = List.copyOf(seatIds);
    }

    public List<Long> getSeatIds() {
        return seatIds;
    }
}
