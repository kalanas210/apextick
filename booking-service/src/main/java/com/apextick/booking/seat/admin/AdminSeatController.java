package com.apextick.booking.seat.admin;

import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.dto.AdminSeatResponse;
import com.apextick.booking.web.NotFoundException;
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

    public AdminSeatController(SeatRepository seats) {
        this.seats = seats;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminSeatResponse> byEvent(@RequestParam Long eventId) {
        return seats.findByEventIdOrderByIdAsc(eventId).stream().map(AdminSeatResponse::from).toList();
    }

    /**
     * Force-releases a stuck hold. Only HELD seats move; a BOOKED seat is returned
     * unchanged, which is why the UI offers this on held rows only.
     */
    @PostMapping("/{id}/release")
    @Transactional
    public AdminSeatResponse release(@PathVariable Long id) {
        // Look the seat up first so an unknown id is a 404 rather than a pointless
        // UPDATE followed by a bare NoSuchElementException (a 500).
        if (!seats.existsById(id)) {
            throw new NotFoundException("Seat", id);
        }
        seats.releaseSeat(id);
        Seat seat = seats.findById(id).orElseThrow(() -> new NotFoundException("Seat", id));
        return AdminSeatResponse.from(seat);
    }
}
