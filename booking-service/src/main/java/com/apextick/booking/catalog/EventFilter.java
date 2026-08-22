package com.apextick.booking.catalog;

import java.time.LocalDate;

public record EventFilter(
        Sport sport, Long seriesId, String seriesSlug, EventStatus status,
        LocalDate from, LocalDate to, String city, String q) {
}
