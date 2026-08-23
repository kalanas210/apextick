package com.apextick.booking.payment;

import java.util.List;

/** Raised during confirmation when a paid seat is no longer held by the buyer (hold expired mid-payment). */
public class SeatsLostException extends RuntimeException {
    private final transient List<Long> seatIds;

    public SeatsLostException(List<Long> seatIds) {
        super("One or more seats were lost before payment could be confirmed");
        this.seatIds = List.copyOf(seatIds);
    }

    public List<Long> getSeatIds() {
        return seatIds;
    }
}
