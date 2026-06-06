package com.apextick.booking.seat;

public class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(Long seatId) {
        super("Seat " + seatId + " is no longer available");
    }
}