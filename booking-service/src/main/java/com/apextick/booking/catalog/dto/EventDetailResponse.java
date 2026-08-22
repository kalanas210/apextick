package com.apextick.booking.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EventDetailResponse(
        Long id, String slug, String name, Long seriesId, String seriesSlug, String sport,
        TeamResponse home, TeamResponse away, Instant startsAt, String date, String time, String timeZone,
        String stadium, String city, String country, String stage, String status, String image, String blurb,
        String currency, String currencySymbol, BigDecimal fromPrice, long availableSeats, long totalSeats,
        Instant salesStartAt, Instant salesEndAt,
        SeriesResponse series, List<PriceTierResponse> tiers, List<SectionResponse> sections) {

    public static EventDetailResponse of(EventSummaryResponse s, SeriesResponse series,
                                         List<PriceTierResponse> tiers, List<SectionResponse> sections) {
        return new EventDetailResponse(
                s.id(), s.slug(), s.name(), s.seriesId(), s.seriesSlug(), s.sport(),
                s.home(), s.away(), s.startsAt(), s.date(), s.time(), s.timeZone(),
                s.stadium(), s.city(), s.country(), s.stage(), s.status(), s.image(), s.blurb(),
                s.currency(), s.currencySymbol(), s.fromPrice(), s.availableSeats(), s.totalSeats(),
                s.salesStartAt(), s.salesEndAt(), series, tiers, sections);
    }
}
