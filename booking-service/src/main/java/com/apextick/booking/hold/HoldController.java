package com.apextick.booking.hold;

import com.apextick.booking.hold.dto.HoldRequest;
import com.apextick.booking.hold.dto.HoldResponse;
import com.apextick.booking.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Holds")
public class HoldController {

    private final HoldService holds;

    public HoldController(HoldService holds) {
        this.holds = holds;
    }

    @PostMapping("/{idOrSlug}/holds")
    @Operation(summary = "Hold up to N seats for the caller (all-or-nothing)")
    public ResponseEntity<HoldResponse> hold(@PathVariable String idOrSlug,
                                             @Valid @RequestBody HoldRequest request,
                                             CurrentUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(holds.hold(idOrSlug, request.seatIds(), user));
    }

    @GetMapping("/{idOrSlug}/holds/me")
    @Operation(summary = "The caller's current holds for this event")
    public HoldResponse mine(@PathVariable String idOrSlug, CurrentUser user) {
        return holds.myHolds(idOrSlug, user);
    }

    @DeleteMapping("/{idOrSlug}/holds")
    @Operation(summary = "Release all of the caller's holds for this event")
    public ResponseEntity<Void> release(@PathVariable String idOrSlug, CurrentUser user) {
        holds.releaseMine(idOrSlug, user);
        return ResponseEntity.noContent().build();
    }
}
