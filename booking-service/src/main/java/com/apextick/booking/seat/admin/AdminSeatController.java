package com.apextick.booking.seat.admin;

import com.apextick.booking.hold.HoldService;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.dto.AdminSeatResponse;
import com.apextick.booking.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/seats")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Seats")
public class AdminSeatController {

    private final SeatRepository seats;
    private final HoldService holds;
    private final SeatBlockService blocks;

    public AdminSeatController(SeatRepository seats, HoldService holds, SeatBlockService blocks) {
        this.seats = seats;
        this.holds = holds;
        this.blocks = blocks;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminSeatResponse> byEvent(@RequestParam Long eventId) {
        return seats.findByEventIdOrderByIdAsc(eventId).stream().map(AdminSeatResponse::from).toList();
    }

    /**
     * Force-releases a stuck hold. Only a HELD seat moves; any other answers 409 SEAT_NOT_HELD
     * rather than a 200 with the seat untouched, which read as a release that had worked.
     *
     * <p>Routed through {@link HoldService#adminRelease} rather than updating the row here,
     * so the release carries the same side effects as every other one: a seat.released
     * outbox event, a live seat-map update for everyone watching, the Redis key dropped,
     * and any unpaid order covering the seat cancelled instead of left payable.
     */
    @PostMapping("/{id}/release")
    @Transactional
    public AdminSeatResponse release(@PathVariable Long id, CurrentUser admin) {
        Seat seat = holds.adminRelease(id, admin.sub());
        return AdminSeatResponse.from(seat);
    }

    @PostMapping("/{id}/block")
    @Transactional
    @Operation(summary = "Take an available seat off sale: broken, or kept for the press or a wheelchair space")
    public AdminSeatResponse block(@PathVariable Long id, CurrentUser admin) {
        return AdminSeatResponse.from(blocks.block(id, admin.sub()));
    }

    @PostMapping("/{id}/unblock")
    @Transactional
    @Operation(summary = "Put a blocked seat back on sale")
    public AdminSeatResponse unblock(@PathVariable Long id, CurrentUser admin) {
        return AdminSeatResponse.from(blocks.unblock(id, admin.sub()));
    }
}
