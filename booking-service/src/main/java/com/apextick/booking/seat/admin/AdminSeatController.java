package com.apextick.booking.seat.admin;

import com.apextick.booking.hold.HoldService;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.dto.AdminSeatResponse;
import com.apextick.booking.security.CurrentUser;
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

    public AdminSeatController(SeatRepository seats, HoldService holds) {
        this.seats = seats;
        this.holds = holds;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminSeatResponse> byEvent(@RequestParam Long eventId) {
        return seats.findByEventIdOrderByIdAsc(eventId).stream().map(AdminSeatResponse::from).toList();
    }

    /**
     * Force-releases a stuck hold. Only HELD seats move; a BOOKED seat is returned
     * unchanged, which is why the UI offers this on held rows only.
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
}
