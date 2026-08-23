package com.apextick.booking.catalog.dto;

import com.apextick.booking.catalog.Team;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TeamResponse(
        Long id,
        String name,
        @JsonProperty("short") String shortCode,
        String monogram,
        String color,
        String flag,
        String logo) {

    public static TeamResponse from(Team t) {
        if (t == null) {
            return null;
        }
        return new TeamResponse(t.getId(), t.getName(), t.getShortCode(),
                t.getMonogram(), t.getColor(), t.getFlag(), t.getLogo());
    }
}
