package com.apextick.booking.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record EventUpsertRequest(
        @NotBlank String name, @NotBlank String slug, @NotBlank String sport,
        Long seriesId, Long homeTeamId, Long awayTeamId,
        @NotNull Instant startsAt, String timeZone, @NotBlank String venue,
        String city, String country, String stage, String status, String image, String blurb,
        @NotBlank String currency, Instant salesStartAt, Instant salesEndAt) {
}
