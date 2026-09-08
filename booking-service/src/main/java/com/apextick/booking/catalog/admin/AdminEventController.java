package com.apextick.booking.catalog.admin;

import com.apextick.booking.catalog.CatalogQueryService;
import com.apextick.booking.catalog.EventFilter;
import com.apextick.booking.catalog.EventStatus;
import com.apextick.booking.catalog.Sport;
import com.apextick.booking.catalog.dto.EventDetailResponse;
import com.apextick.booking.catalog.dto.EventSummaryResponse;
import com.apextick.booking.catalog.dto.EventUpsertRequest;
import com.apextick.booking.catalog.dto.LayoutRequest;
import com.apextick.booking.catalog.dto.LayoutResult;
import com.apextick.booking.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/events")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Events")
public class AdminEventController {

    private final AdminEventService adminEvents;
    private final LayoutService layout;
    private final CatalogQueryService catalog;

    public AdminEventController(AdminEventService adminEvents, LayoutService layout, CatalogQueryService catalog) {
        this.adminEvents = adminEvents;
        this.layout = layout;
        this.catalog = catalog;
    }

    /**
     * The admin event list. Deliberately not the public {@code GET /api/events}: that one
     * hides DRAFT and CANCELLED, so the panel could never see the event it just created.
     * Sorted newest-first, because an operator looks for what they last touched.
     */
    @GetMapping
    @Operation(summary = "Browse every event, including drafts and cancellations")
    public PageResponse<EventSummaryResponse> list(
            @RequestParam(required = false) String sport,
            @RequestParam(required = false) Long seriesId,
            @RequestParam(required = false) String series,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "latest") String sort) {

        EventFilter filter = new EventFilter(
                sport == null ? null : Sport.fromCode(sport),
                seriesId, series,
                status == null ? null : EventStatus.fromCode(status),
                from, to, city, q);
        return catalog.searchAll(filter, page, size, sort);
    }

    @PostMapping
    public ResponseEntity<EventDetailResponse> create(@Valid @RequestBody EventUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminEvents.create(request));
    }

    @PutMapping("/{id}")
    public EventDetailResponse update(@PathVariable Long id, @Valid @RequestBody EventUpsertRequest request) {
        return adminEvents.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public EventDetailResponse setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return adminEvents.setStatus(id, body.get("status"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminEvents.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/layout")
    public ResponseEntity<LayoutResult> layout(@PathVariable Long id, @Valid @RequestBody LayoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(layout.applyLayout(id, request));
    }
}
