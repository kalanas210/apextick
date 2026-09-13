package com.apextick.booking.seat;

public enum SeatStatus {
    AVAILABLE,
    HELD,
    BOOKED,
    /** Taken off sale by an admin: a broken seat, or one kept for the press or a wheelchair space. */
    BLOCKED
}
