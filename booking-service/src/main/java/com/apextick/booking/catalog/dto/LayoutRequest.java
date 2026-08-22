package com.apextick.booking.catalog.dto;

import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

public record LayoutRequest(@NotEmpty List<TierSpec> tiers, @NotEmpty List<SectionSpec> sections) {

    public record TierSpec(String code, String name, BigDecimal price, List<String> perks, Integer sortOrder) {
    }

    public record SectionSpec(String code, String name, String tierCode, String side,
                              Integer rows, Integer seatsPerRow, Integer sortOrder) {
    }
}
