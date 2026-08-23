package com.apextick.booking.catalog.admin;

import com.apextick.booking.catalog.dto.EventDetailResponse;
import com.apextick.booking.catalog.dto.EventUpsertRequest;
import com.apextick.booking.catalog.dto.LayoutRequest;
import com.apextick.booking.catalog.dto.LayoutResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/events")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Events")
public class AdminEventController {

    private final AdminEventService adminEvents;
    private final LayoutService layout;

    public AdminEventController(AdminEventService adminEvents, LayoutService layout) {
        this.adminEvents = adminEvents;
        this.layout = layout;
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
