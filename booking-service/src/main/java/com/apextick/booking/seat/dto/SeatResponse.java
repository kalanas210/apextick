package com.apextick.booking.seat.dto;

import com.apextick.booking.catalog.PriceTier;
import com.apextick.booking.catalog.Section;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record SeatResponse(
        Long id,
        String label,
        Integer row,
        Integer col,
        Long sectionId,
        String sectionCode,
        Long tierId,
        String tierCode,
        BigDecimal price,
        SeatStatus status,
        Instant heldUntil,
        boolean mine) {

    public static SeatResponse from(Seat seat, String sub) {
        Section section = seat.getSection();
        PriceTier tier = section.getTier();
        boolean mine = sub != null && sub.equals(seat.getHeldBy());
        return new SeatResponse(
                seat.getId(), seat.getSeatNumber(), seat.getRowIdx(), seat.getColIdx(),
                section.getId(), section.getCode(), tier.getId(), tier.getCode(),
                tier.getPrice(), seat.getStatus(), seat.getHeldUntil(), mine);
    }
}
