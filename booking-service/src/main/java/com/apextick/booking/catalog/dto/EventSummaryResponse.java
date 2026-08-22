package com.apextick.booking.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record EventSummaryResponse(
        Long id, String slug, String name, Long seriesId, String seriesSlug, String sport,
        TeamResponse home, TeamResponse away, Instant startsAt, String date, String time, String timeZone,
        String stadium, String city, String country, String stage, String status, String image, String blurb,
        String currency, String currencySymbol, BigDecimal fromPrice, long availableSeats, long totalSeats,
        Instant salesStartAt, Instant salesEndAt) {
}
