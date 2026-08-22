package com.apextick.booking.catalog.dto;

public record SectionResponse(
        Long id, String code, String name, Long tierId, String tierCode,
        String side, Integer rows, Integer seatsPerRow, long available) {
}
