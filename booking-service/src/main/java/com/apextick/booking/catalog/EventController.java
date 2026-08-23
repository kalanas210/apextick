package com.apextick.booking.catalog;

import com.apextick.booking.catalog.dto.EventDetailResponse;
import com.apextick.booking.catalog.dto.EventSummaryResponse;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.dto.SeatResponse;
import com.apextick.booking.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Events")
public class EventController {

    private final CatalogQueryService catalog;

    public EventController(CatalogQueryService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(summary = "Browse events with filters and pagination")
    public PageResponse<EventSummaryResponse> list(
            @RequestParam(required = false) String sport,
            @RequestParam(required = false) Long seriesId,
            @RequestParam(required = false) String series,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "soonest") String sort) {

        LocalDate fromDate = from;
        LocalDate toDate = to;
        if (month != null && !month.isBlank()) {
            YearMonth ym = YearMonth.parse(month);
            fromDate = ym.atDay(1);
            toDate = ym.atEndOfMonth();
        }
        EventFilter filter = new EventFilter(
                sport == null ? null : Sport.fromCode(sport),
                seriesId, series,
                status == null ? null : EventStatus.fromCode(status),
                fromDate, toDate, city, q);
        return catalog.search(filter, page, size, sort);
    }

    @GetMapping("/{idOrSlug}")
    @Operation(summary = "Event detail with tiers and sections")
    public EventDetailResponse detail(@PathVariable String idOrSlug) {
        return catalog.detail(idOrSlug);
    }

    @GetMapping("/{idOrSlug}/seats")
    @Operation(summary = "Seats for an event; sets mine=true for the caller's holds")
    public List<SeatResponse> seats(@PathVariable String idOrSlug, CurrentUser user) {
        return catalog.seats(idOrSlug, user == null ? null : user.sub());
    }
}
